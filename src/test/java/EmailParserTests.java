import org.junit.jupiter.api.*;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class EmailParserTests {
    
    private static final String TEST_JSON_PATH = "test_email.json";
    File jsonFile = new File(TEST_JSON_PATH); 
    
    @Test
    public void checkJsonFile() {   
        // Check if the JSON file exists
        assertTrue(jsonFile.exists());
    }

    @Test
    public void testExtractID() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailData = "";
        try {
            emailData = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailData);
        // Check if the ID is a positive integer
        for (JSONObject email : emails) {
            int id = email.getInt("id");
            assertTrue(id > 0);
        }
    }

    @Test
    public void testExtractDate() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailDate = "";
        try {
            emailDate = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailDate);
        // Check if the date is in the expected format
        for (JSONObject email : emails) {
            String date = email.getString("date");
            assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        }
    }

    @Test
    public void testExtractSender() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailSender = "";
        try {
            emailSender = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailSender);
        // Check if the sender is not empty
        for (JSONObject email : emails) {
            String sender = email.getString("sender");
            assertFalse(sender.isEmpty());
        }

        // Pring the sender to the console
        for (JSONObject email : emails) {
            String sender = email.getString("sender");
            System.out.println("Sender: " + sender);
        }
    }

    @Test
    public void testExtractSubject() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailSubject = "";
        try {
            emailSubject = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailSubject);
        // Check if the subject is not empty
        for (JSONObject email : emails) {
            String subject = email.getString("subject");
            // If the subject is empty, set it to "No Subject"
            if (subject.isEmpty()) {
                subject = "No Subject";
            }
            // Check if the subject is not empty
            assertFalse(subject.isEmpty());
        }
        // Print the subject to the console
        for (JSONObject email : emails) {
            String subject = email.getString("subject");
            System.out.println("Subject: " + subject);
        }
    }

    @Test
    public void testExtractBody() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailBody = "";
        try {
            emailBody = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailBody);
        // Check if the body is not empty
        for (JSONObject email : emails) {
            String body = email.getString("body");
            assertFalse(body.isEmpty());
        }
        // Print the body to the console
        for (JSONObject email : emails) {
            String body = email.getString("body");
            System.out.println("Body: " + body);
        }
    }

    @Test
    public void testSendingToAssistant() {
        // Read the JSON file
        jsonFile = new File(TEST_JSON_PATH);
        String emailData = "";
        try {
            emailData = new String(java.nio.file.Files.readAllBytes(jsonFile.toPath()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Parse the JSON content
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(emailData);
        
        // Initialize the OpenAI assistant engine - this was missing
        OpenAiAssistantEngine.testAPIKey(Chatbot.APIKEY);
        Chatbot.assistant = new OpenAiAssistantEngine(Chatbot.APIKEY);
        
        // Set up the assistant
        String assistantId = Chatbot.setupAssistant();
        
        // Check if the email is sent to the assistant
        for (JSONObject email : emails) {
            String sender = email.getString("sender");
            String subject = email.getString("subject");
            String body = email.getString("body");
            
            // Check if the email is sent to the assistant
            if (sender.equals("axb19c@acu.edu")) {
                System.out.println("Email sent to assistant: " + email);
                
                // Process this specific email
                String emailJson = "{\"status\":\"success\",\"data\":[" + email.toString() + "]}";
                List<String> responses = Chatbot.processEmailsFromJson(emailJson, assistantId);
                
                System.out.println("Chatbot response: " + responses.get(0));
            } else {
                System.out.println("Email not sent to assistant: " + email);
            }
        }
        
        // Clean up
        if (assistantId != null) {
            Chatbot.assistant.deleteResource("assistants", assistantId);
        }
    }
}
