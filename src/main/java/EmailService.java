package com.acu.assistant;

import java.util.*;
import java.util.logging.Logger;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.search.FlagTerm;
import java.io.*;
import java.net.Socket;

public class EmailService {

    private Session session;
    private Store store;
    private boolean connected = false;
    private String emailAccount;
    private String emailPassword;
    private String emailServer;
    private int emailSmtpPort;
    private boolean useSmtp;
    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    public EmailService() throws MessagingException {
        Config config = Config.getInstance();
        String emailImapHost = config.getEmailImapHost();
        int emailPort = config.getEmailPort();
        this.emailAccount = config.getEmailAccount();
        this.emailPassword = config.getEmailPassword();
        String emailFolder = config.getEmailFolder();
        this.emailServer = config.getEmailServer();
        this.emailSmtpPort = config.getEmailSmtpPort();
        this.useSmtp = config.getUseSmtp();

        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", emailImapHost);
        properties.put("mail.imaps.port", emailPort);
        properties.put("mail.imaps.ssl.enable", "true");
        properties.put("mail.imaps.ssl.trust", emailImapHost);

        session = Session.getDefaultInstance(properties);

        try {
            store = session.getStore();
            store.connect(emailImapHost, emailAccount, emailPassword);
            connected = true;
        } catch (MessagingException e) {
            throw new MessagingException("Cannot connect to email: " + e.getMessage(), e);
        }
    }

    public void close() {
        if (connected) {
            try {
                store.close();
                connected = false;
            } catch (MessagingException e) {
                logger.warning("Error closing email connection: " + e.getMessage());
            }
        }
    }

    public List<Map<String, String>> getNewEmails() throws MessagingException {
        List<Map<String, String>> emails = new ArrayList<>();

        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        Flags seen = new Flags(Flags.Flag.SEEN);
        FlagTerm unseenFlagTerm = new FlagTerm(seen, false);
        Message[] messages = inbox.search(unseenFlagTerm);

        for (int i = 0; i < messages.length; i++) {
            Message message = messages[i];
            Map<String, String> email = new HashMap<>();

            String messageId = "";
            String[] headers = message.getHeader("Message-ID");
            if (headers != null && headers.length > 0) {
                messageId = headers[0];
            }

            Address[] fromAddresses = message.getFrom();
            String fromAddress = "";
            if (fromAddresses != null && fromAddresses.length > 0) {
                if (fromAddresses[0] instanceof InternetAddress) {
                    fromAddress = ((InternetAddress) fromAddresses[0]).getAddress();
                } else {
                    fromAddress = fromAddresses[0].toString();
                }
            }

            String subject = message.getSubject() != null ? message.getSubject() : "(No Subject)";

            String body = getEmailBody(message);

            Date sentDate = message.getSentDate();
            String dateStr = sentDate != null
                    ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(sentDate)
                    : new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

            email.put("id", String.valueOf(message.getMessageNumber()));
            email.put("message_id", messageId);
            email.put("from", fromAddress);
            email.put("subject", subject);
            email.put("body", body);
            email.put("date", dateStr);

            emails.add(email);
        }

        inbox.close(false);
        return emails;
    }

    private String getEmailBody(Message message) throws MessagingException {
        String body = "";
        try {
            Object content = message.getContent();

            if (content instanceof String) {
                body = (String) content;
            } else if (content instanceof Multipart) {
                Multipart multipart = (Multipart) content;
                body = getPart(multipart);
            }

            if (body.contains("<html") || body.contains("<body")) {
                body = stripHtml(body);
            }

        } catch (IOException e) {
            logger.warning("Error getting email body: " + e.getMessage());
        }

        return body.trim();
    }

    private String getPart(Multipart multipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart bodyPart = multipart.getBodyPart(i);

            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent().toString());
                break;
            } else if (bodyPart.isMimeType("text/html")) {
                String html = bodyPart.getContent().toString();
                result.append(stripHtml(html));
            } else if (bodyPart.getContent() instanceof Multipart) {
                result.append(getPart((Multipart) bodyPart.getContent()));
            }
        }

        return result.toString();
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&");
    }

    public void markAsRead(String msgId) throws MessagingException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        int messageNumber = Integer.parseInt(msgId);
        Message message = inbox.getMessage(messageNumber);
        message.setFlag(Flags.Flag.SEEN, true);

        inbox.close(false);
    }

    public boolean sendEmail(String to, String subject, String body) throws MessagingException {
        body = wrapLines(body, 70);

        if (useSmtp) {
            return sendSMTP(to, subject, body);
        } else {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", emailServer);
            properties.put("mail.smtp.port", emailSmtpPort);
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");

            Session mailSession = Session.getInstance(properties, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailAccount, emailPassword);
                }
            });

            try {
                MimeMessage message = new MimeMessage(mailSession);
                message.setFrom(new InternetAddress("ACU Assistant <" + emailAccount + ">"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                logger.warning("Error sending email: " + e.getMessage());
                throw e;
            }
        }
    }

    public boolean replyToEmail(String msgId, String to, String subject, String body)
            throws MessagingException {
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        int messageNumber = Integer.parseInt(msgId);
        Message message = inbox.getMessage(messageNumber);

        String[] headers = message.getHeader("Message-ID");
        String inReplyTo = headers != null && headers.length > 0 ? headers[0] : "";

        String references = "";
        String[] referencesHeaders = message.getHeader("References");
        if (referencesHeaders != null && referencesHeaders.length > 0) {
            references = referencesHeaders[0] + " " + inReplyTo;
        } else {
            references = inReplyTo;
        }

        inbox.close(false);

        return sendEmailWithHeaders(to, subject, body, inReplyTo, references);
    }

    private boolean sendEmailWithHeaders(String to, String subject, String body, String inReplyTo, String references)
            throws MessagingException {
        body = wrapLines(body, 70);

        if (useSmtp) {
            return sendSMTPWithHeaders(to, subject, body, inReplyTo, references);
        } else {
            Properties properties = new Properties();
            properties.put("mail.smtp.host", emailServer);
            properties.put("mail.smtp.port", emailSmtpPort);
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.starttls.enable", "true");

            Session mailSession = Session.getInstance(properties, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailAccount, emailPassword);
                }
            });

            try {
                MimeMessage message = new MimeMessage(mailSession);
                message.setFrom(new InternetAddress("ACU Assistant <" + emailAccount + ">"));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
                message.setSubject(subject);
                message.setText(body);

                if (inReplyTo != null && !inReplyTo.isEmpty()) {
                    message.setHeader("In-Reply-To", inReplyTo);
                }
                if (references != null && !references.isEmpty()) {
                    if (references.length() > 70) {
                        references = wrapHeaderValue(references);
                    }
                    message.setHeader("References", references);
                }

                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                logger.warning("Error sending email: " + e.getMessage());
                throw e;
            }
        }
    }

    private boolean sendSMTP(String to, String subject, String body) {
        return sendSMTPWithHeaders(to, subject, body, null, null);
    }

    private boolean sendSMTPWithHeaders(String to, String subject, String body, String inReplyTo, String references) {
        body = wrapLines(body, 70);

        Map<String, String> headers = new HashMap<>();
        headers.put("From", "ACU Assistant <" + emailAccount + ">");
        headers.put("Reply-To", emailAccount);
        headers.put("To", to);
        headers.put("Subject", subject);
        headers.put("MIME-Version", "1.0");
        headers.put("Content-Type", "text/plain; charset=UTF-8");

        if (inReplyTo != null && !inReplyTo.isEmpty()) {
            headers.put("In-Reply-To", inReplyTo);
        }
        if (references != null && !references.isEmpty()) {
            if (references.length() > 70) {
                references = wrapHeaderValue(references);
            }
            headers.put("References", references);
        }

        try (Socket smtp = new Socket(emailServer, emailSmtpPort)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(smtp.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(smtp.getOutputStream()));

            getResponse(reader);

            writer.write("EHLO " + emailServer + "\r\n");
            writer.flush();
            getResponse(reader);

            if (emailSmtpPort == 587) {
                writer.write("STARTTLS\r\n");
                writer.flush();
                getResponse(reader);

                writer.write("EHLO " + emailServer + "\r\n");
                writer.flush();
                getResponse(reader);
            }

            writer.write("AUTH LOGIN\r\n");
            writer.flush();
            getResponse(reader);

            writer.write(Base64.getEncoder().encodeToString(emailAccount.getBytes()) + "\r\n");
            writer.flush();
            getResponse(reader);

            writer.write(Base64.getEncoder().encodeToString(emailPassword.getBytes()) + "\r\n");
            writer.flush();
            getResponse(reader);

            writer.write("MAIL FROM:<" + emailAccount + ">\r\n");
            writer.flush();
            getResponse(reader);

            writer.write("RCPT TO:<" + to + ">\r\n");
            writer.flush();
            getResponse(reader);

            writer.write("DATA\r\n");
            writer.flush();
            getResponse(reader);

            StringBuilder headerText = new StringBuilder();
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey().length() + header.getValue().length() + 2 > 70) {
                    headerText.append(header.getKey()).append(": ")
                            .append(wrapHeaderValue(header.getValue())).append("\r\n");
                } else {
                    headerText.append(header.getKey()).append(": ")
                            .append(header.getValue()).append("\r\n");
                }
            }

            String wrappedBody = ensureSmtpLineLimits(body);

            writer.write(headerText.toString() + "\r\n" + wrappedBody + "\r\n.\r\n");
            writer.flush();
            getResponse(reader);

            writer.write("QUIT\r\n");
            writer.flush();

            return true;
        } catch (Exception e) {
            logger.warning("SMTP Send Failed: " + e.getMessage());
            return false;
        }
    }

    private String wrapHeaderValue(String value) {
        StringBuilder result = new StringBuilder();
        String line = "";
        String[] words = value.split("\\s+");

        for (String word : words) {
            if ((line + word).length() > 65) {
                result.append(line).append("\r\n ");
                line = word;
            } else {
                line = line.isEmpty() ? word : line + " " + word;
            }
        }

        return result.append(line).toString();
    }

    private String ensureSmtpLineLimits(String body) {
        String[] lines = body.split("\n");
        List<String> result = new ArrayList<>();

        for (String line : lines) {
            if (line.length() > 900) {
                for (int i = 0; i < line.length(); i += 900) {
                    int end = Math.min(i + 900, line.length());
                    result.add(line.substring(i, end));
                }
            } else {
                result.add(line);
            }
        }

        return String.join("\r\n", result);
    }

    private String wrapLines(String text, int length) {
        if (text == null) {
            return "";
        }

        text = text.replace("\r\n", "\n").replace("\r", "\n");

        String[] paragraphs = text.split("\n\n");
        List<String> result = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                result.add("");
                continue;
            }

            String[] lines = paragraph.split("\n");
            List<String> wrappedLines = new ArrayList<>();

            for (String line : lines) {
                if (line.length() <= length) {
                    wrappedLines.add(line);
                } else {
                    String wrapped = wordWrap(line, length);

                    String[] secondaryLines = wrapped.split("\n");
                    for (String secondary : secondaryLines) {
                        if (secondary.length() > length) {
                            for (int i = 0; i < secondary.length(); i += length) {
                                int end = Math.min(i + length, secondary.length());
                                wrappedLines.add(secondary.substring(i, end));
                            }
                        } else {
                            wrappedLines.add(secondary);
                        }
                    }
                }
            }

            result.add(String.join("\n", wrappedLines));
        }

        String finalText = String.join("\n\n", result);
        String[] finalLines = finalText.split("\n");
        List<String> safeLines = new ArrayList<>();

        for (String line : finalLines) {
            if (line.length() > length) {
                for (int i = 0; i < line.length(); i += length) {
                    int end = Math.min(i + length, line.length());
                    safeLines.add(line.substring(i, end));
                }
            } else {
                safeLines.add(line);
            }
        }

        return String.join("\n", safeLines);
    }

    private String wordWrap(String text, int lineLength) {
        if (text == null || text.length() <= lineLength) {
            return text;
        }

        StringBuilder wrapped = new StringBuilder();
        int lastSpace = -1;
        int lineStart = 0;
        int i = 0;

        for (; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                lastSpace = i;
            }

            if (i > lineStart + lineLength - 1) {
                if (lastSpace != -1) {
                    wrapped.append(text.substring(lineStart, lastSpace));
                    wrapped.append("\n");
                    lineStart = lastSpace + 1;
                    lastSpace = -1;
                } else {
                    wrapped.append(text.substring(lineStart, i));
                    wrapped.append("\n");
                    lineStart = i;
                }
            }
        }

        wrapped.append(text.substring(lineStart));
        return wrapped.toString();
    }

    private String getResponse(BufferedReader reader) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line).append("\n");
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }

        String responseStr = response.toString();
        String code = responseStr.substring(0, 3);

        if (Integer.parseInt(code) >= 400) {
            throw new IOException("SMTP Error: " + responseStr);
        }

        return responseStr;
    }
}
