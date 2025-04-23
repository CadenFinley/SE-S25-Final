import org.json.JSONObject;
import java.io.*;
import java.util.regex.*;

public class EmailParser {

    public static JSONObject parseEmail(String content) {
        JSONObject json = new JSONObject();

        // Extract sender
        Pattern senderPattern = Pattern.compile("From:\\s*([^<\\r\\n]*(?:<[^>]*>)?)", Pattern.CASE_INSENSITIVE);
        Matcher senderMatcher = senderPattern.matcher(content);
        if (senderMatcher.find()) {
            String sender = senderMatcher.group(1).trim();
            json.put("sender", sender);
        } else {
            json.put("sender", "Unknown");
        }

        // extract subject
        Pattern subjectPattern = Pattern.compile("Subject:\\s*(.*?)\\r?\\n", Pattern.CASE_INSENSITIVE);
        Matcher subjectMatcher = subjectPattern.matcher(content);
        if (subjectMatcher.find()) {
            json.put("subject", subjectMatcher.group(1).trim());
        } else {
            json.put("subject", "No Subject");
        }

        // Extract body - everything after the first blank line
        Pattern bodyPattern = Pattern.compile("\\r?\\n\\r?\\n(.*)", Pattern.DOTALL);
        Matcher bodyMatcher = bodyPattern.matcher(content);
        if (bodyMatcher.find()) {
            json.put("body", bodyMatcher.group(1).trim());
        } else {
            json.put("body", "");
        }
        
        return json;
    }

    /**
     * Parse an email from a file
     * @param file The email file to parse
     * @return JSONObject containing the parsed email components
     */
    public static JSONObject parseEmailFile(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return parseEmail(content.toString());
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
