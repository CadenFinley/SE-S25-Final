
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class EmailProcessor {

    public static String extractNameFromEmail(String email) {
        String[] parts = email.split("@");
        String username = parts[0];

        username = username.replace('.', ' ').replace('_', ' ').replace('-', ' ');

        username = username.replaceAll("[0-9]+", "");

        username = username.trim();
        if (username.isEmpty()) {
            return "";
        }

        String[] words = username.split("\\s+");
        StringBuilder name = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                name.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return name.toString().trim();
    }

    public static void processEmails() {
        try {
            EmailService emailService = new EmailService();
            ChatbotAPI chatbot = new ChatbotAPI();
            ConversationManager conversationManager = new ConversationManager();

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date()));

            List<Map<String, String>> emails = emailService.getNewEmails();

            if (emails.isEmpty()) {
                result.put("message", "No new emails found.");
                System.out.println(result.toString(4));
                return;
            }

            result.put("totalEmails", emails.size());
            JSONArray processedEmails = new JSONArray();

            for (Map<String, String> email : emails) {
                JSONObject emailData = new JSONObject();
                emailData.put("id", email.get("id"));
                emailData.put("from", email.get("from"));
                emailData.put("subject", email.get("subject"));

                int conversationId = conversationManager.getOrCreateConversation(email.get("from"));
                emailData.put("conversationId", conversationId);

                String userName = conversationManager.getUserName(email.get("from"));
                if (userName == null || userName.isEmpty()) {
                    userName = extractNameFromEmail(email.get("from"));
                    conversationManager.getUserName(email.get("from"), userName);
                }
                emailData.put("userName", userName);

                List<Map<String, Object>> conversationHistory
                        = conversationManager.getConversationHistory(email.get("from"), 10);
                List<Map<String, String>> formattedHistory
                        = conversationManager.formatHistoryForAssistant(conversationHistory);
                emailData.put("historyCount", conversationHistory.size());

                int userMessageId = conversationManager.addMessage(conversationId, email.get("body"), true);
                emailData.put("userMessageId", userMessageId);

                String message = email.get("body");
                String response = chatbot.run(message, formattedHistory, userName);

                String formattedResponse = conversationManager.formatEmailContent(response);

                conversationManager.addMessage(conversationId, response, false);

                String responsePreview = response != null && response.length() > 100
                        ? response.substring(0, 100) + "..." : response;
                emailData.put("responsePreview", responsePreview);

                try {
                    emailService.markAsRead(email.get("id"));
                } catch (MessagingException e) {
                    System.err.println("Failed to mark email as read: " + e.getMessage());
                    continue;
                }
                String replySubject = email.get("subject").toLowerCase().startsWith("re:")
                        ? email.get("subject") : "Re: " + email.get("subject");
                emailService.replyToEmail(email.get("id"), email.get("from"), replySubject, formattedResponse);
                emailData.put("replySent", true);

                processedEmails.put(emailData);
            }

            result.put("processedEmails", processedEmails);
            result.put("message", "Email processing complete.");

            emailService.close();
            conversationManager.close();

            System.out.println(result.toString(4));

        } catch (Exception e) {
            JSONObject errorData = new JSONObject();
            errorData.put("status", "error");
            errorData.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date()));
            errorData.put("message", e.getMessage());

            System.out.println(errorData.toString(4));
        }
    }

    public static void main(String[] args) {
        processEmails();
    }
}
