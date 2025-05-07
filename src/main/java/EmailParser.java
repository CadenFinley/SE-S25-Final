import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.util.regex.*;
import java.util.ArrayList;
import java.util.List;

public class EmailParser {

    private static final File USER_INFO_FILE = new File("user_info.txt");

    /**
     * Parse a JSON string containing email data
     * @param jsonContent The JSON string containing email data
     * @return List of JSONObjects containing individual emails
     */
    public static List<JSONObject> parseEmailsFromJson(String jsonContent) {
        List<JSONObject> emails = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonContent);
            if (root.has("status") && root.getString("status").equals("success")) {
                JSONArray emailsArray = root.getJSONArray("data");
                for (int i = 0; i < emailsArray.length(); i++) {
                    JSONObject emailData = emailsArray.getJSONObject(i);
                    JSONObject formattedEmail = new JSONObject();
                    formattedEmail.put("id", emailData.optInt("id"));
                    formattedEmail.put("date", emailData.optString("date"));
                    formattedEmail.put("sender", emailData.optString("from", "Unknown"));
                    formattedEmail.put("subject", emailData.optString("subject", "No Subject"));
                    formattedEmail.put("body", emailData.optString("body", ""));
                    emails.add(formattedEmail);
                }
            }
        } catch (Exception e) {
            // Silently ignore malformed JSON input during tests
        }
        return emails;
    }

    /**
     * Parse a single email from raw email content
     * @param content Raw email content with headers
     * @return JSONObject containing the parsed email components
     */
    public static JSONObject parseRawEmail(String content) {
        JSONObject emailJson = new JSONObject();

        // Extract sender
        // Accepts anything up to a newline, including numbers, periods, @, and dashes
        Pattern senderPattern = Pattern.compile("From:\\s*([^\r\n]+)", Pattern.CASE_INSENSITIVE);
        Matcher senderMatcher = senderPattern.matcher(content);
        if (senderMatcher.find()) {
            String sender = senderMatcher.group(1).trim();
            emailJson.put("sender", sender);
        } else {
            emailJson.put("sender", "Unknown");
        }

        // extract subject
        Pattern subjectPattern = Pattern.compile("Subject:\\s*(.*?)\\r?\\n", Pattern.CASE_INSENSITIVE);
        Matcher subjectMatcher = subjectPattern.matcher(content);
        if (subjectMatcher.find()) {
            emailJson.put("subject", subjectMatcher.group(1).trim());
        } else {
            emailJson.put("subject", "No Subject");
        }

        // Extract body - everything after the first blank line
        Pattern bodyPattern = Pattern.compile("\\r?\\n\\r?\\n(.*)", Pattern.DOTALL);
        Matcher bodyMatcher = bodyPattern.matcher(content);
        if (bodyMatcher.find()) {
            emailJson.put("body", bodyMatcher.group(1).trim());
        } else {
            emailJson.put("body", "");
        }
        
        return emailJson;
    }
    
    /**
     * Parse an email from a file (supports both raw email or JSON format)
     * @param file The email file to parse
     * @param isJson Whether the file contains JSON data or raw email
     * @return List of JSONObjects for JSON format, or a list with one JSONObject for raw format
     */
    public static List<JSONObject> parseEmailFile(File file, boolean isJson) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        if (isJson) {
            return parseEmailsFromJson(content.toString());
        } else {
            List<JSONObject> result = new ArrayList<>();
            result.add(parseRawEmail(content.toString()));
            return result;
        }
    }
    
    /**
     * Extract plain text from a potentially HTML email body
     * @param body The email body that might contain HTML
     * @return Plain text version of the body
     */
    public static String extractPlainText(String body) {
        // Simple HTML tag removal
        return body.replaceAll("<[^>]*>", "")
                  .replaceAll("&nbsp;", " ")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&gt;", ">")
                  .replaceAll("&amp;", "&");
    }
}
