import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class Chatbot {
     static OpenAiAssistantEngine assistant;
    private static final String APIKEY = System.getenv("OPENAI_API_KEY");
    private static final File USER_INFO_FILE = new File("user_info.txt");
    private static final File ACU_DATABASE_FILE = new File("acu_database.txt");

    public static void main(String[] args) {
        assistant = new OpenAiAssistantEngine(APIKEY);
        System.out.println("-------------------------");
        System.out.println("Setting up AI Academic Advisor...");

        //OpenAiAssistantEngine.testAPIKey("hola");
        OpenAiAssistantEngine.testAPIKey(APIKEY);

        String assistantId = setupAssistant();
        if (assistantId == null) {
            return;
        }

        startInteractiveChat(assistantId);

        assistant.deleteResource("assistants", assistantId);
    }

    public static String setupAssistant() {
        String assistantId = assistant.createAssistant(
                "gpt-4o",
                "Personal AI Academic Advisor",
                null, // i dont think this is really needed
                "You are a real-time chat AI Academic Advisor for Abilene Christian University. Address the student by their first and last name based on the user info provided in the user_info.txt file. Provide information about the student's academic journey, courses, and other academic-related topics.",
                null, // only supported by o models
                List.of("file_search"),
                null, // we will add this later with the vector store
                0.1,
                0.1,
                null // we will add these later
        );

        if (assistantId == null) {
            System.out.println("Failed to create assistant");
            return null;
        }

        String userInfoFileID = assistant.uploadFile(USER_INFO_FILE, "assistants");
        String acuDatabaseFileID = assistant.uploadFile(ACU_DATABASE_FILE, "assistants");

        if (userInfoFileID == null || acuDatabaseFileID == null) {
            System.out.println("Failed to upload one or more files");
            return null;
        }

        Map<String, String> fileMetadata = new HashMap<>();
        fileMetadata.put(userInfoFileID, "This fileID (user_info.txt) is associated with the user info");
        fileMetadata.put(acuDatabaseFileID, "This fileID (acu_database.txt) is associated with the ACU database");

        String vectorStoreId = assistant.createVectorStore(
                "User Files",
                Arrays.asList(userInfoFileID, acuDatabaseFileID),
                null,
                null,
                fileMetadata
        );

        if (vectorStoreId == null) {
            System.out.println("Failed to create vector store");
            return null;
        }

        Map<String, Object> toolResources = new HashMap<>();
        Map<String, List<String>> fileSearch = new HashMap<>();
        fileSearch.put("vector_store_ids", List.of(vectorStoreId));
        toolResources.put("file_search", fileSearch);

        boolean updateSuccess = assistant.modifyAssistant(
                assistantId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                toolResources,
                null,
                null
        );

        if (!updateSuccess) {
            System.out.println("Failed to update assistant with vector store");
            return null;
        }

        System.out.println("Assistant setup successfully with ID: " + assistantId);
        return assistantId;
    }

    private static void startInteractiveChat(String assistantId) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String threadId = null;

        System.out.println("\n=== ACU AI Academic Advisor Chat ===");
        System.out.println("Type 'exit' to end the conversation");

        try {
            String userInput;
            while (true) {
                System.out.print("\nYou: ");
                userInput = reader.readLine().trim();

                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }

                if (userInput.isEmpty()) {
                    continue;
                }

                if (threadId == null) {
                    List<JSONObject> messages = List.of(
                            new JSONObject()
                                    .put("role", "user")
                                    .put("content", userInput)
                    );
                    threadId = assistant.createThread(messages, null, null);
                    if (threadId == null) {
                        System.out.println("Failed to create thread. Please try again.");
                        continue;
                    }
                } else {
                    String messageId = assistant.addMessageToThread(threadId, userInput);
                    if (messageId == null) {
                        System.out.println("Failed to send message. Please try again.");
                        continue;
                    }
                }

                String runId = assistant.createRun(
                        threadId,
                        assistantId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                if (runId == null) {
                    System.out.println("Failed to create run. Please try again.");
                    continue;
                }

                boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);

                if (!completed) {
                    System.out.println("The assistant encountered an issue. Please try again.");
                    continue;
                }
                List<String> retrievedMessages = assistant.listMessages(threadId, runId);
                if (retrievedMessages != null && !retrievedMessages.isEmpty()) {
                    System.out.println("\nAdvisor: " + retrievedMessages.get(0));
                } else {
                    System.out.println("No response received. Please try again.");
                }
            }

            if (threadId != null) {
                assistant.deleteResource("threads", threadId);
            }

        } catch (IOException e) {
            System.out.println("Error reading input: " + e.getMessage());
        }
    }

    public static String processEmail(String emailContent, String assistantId) {
        // Parse the email into JSON
    JSONObject emailJson = (JSONObject) EmailParser.parseEmailsFromJson(emailContent);
    String sender = emailJson.getString("sender");
    String subject = emailJson.getString("subject");
    String body = emailJson.getString("body");
    
    // Extract plain text if body contains HTML
    String plainBody = EmailParser.extractPlainText(body);
    
    // Format the query to include email context
    String query = String.format("SENDER: %s\nSUBJECT: %s\nBODY: %s", 
                                sender, subject, plainBody);
    
    // Create a thread for this email
    List<JSONObject> messages = List.of(
        new JSONObject()
            .put("role", "user")
            .put("content", query)
    );
    
    String threadId = assistant.createThread(messages, null, null);
    if (threadId == null) {
        return "Failed to process email due to thread creation error.";
    }
    
    // Run the assistant
    String runId = assistant.createRun(
        threadId, assistantId, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null
    );
    
    if (runId == null) {
        return "Failed to process email due to run creation error.";
    }
    
    boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);
    if (!completed) {
        return "Failed to process email due to timeout or processing error.";
    }
    
    // Get the assistant's response
    List<String> retrievedMessages = assistant.listMessages(threadId, runId);
    if (retrievedMessages == null || retrievedMessages.isEmpty()) {
        return "No response generated for email.";
    }
    
    // Clean up resources
    assistant.deleteResource("threads", threadId);
    
    return retrievedMessages.get(0);
    }

    /**
     * Process multiple emails from a JSON string
     * @param jsonContent JSON string containing email data
     * @param assistantId The ID of the configured assistant
     * @return List of responses for each email
     */
    public static List<String> processEmailsFromJson(String jsonContent, String assistantId) {
        List<String> responses = new ArrayList<>();
        List<JSONObject> emails = EmailParser.parseEmailsFromJson(jsonContent);
        
        for (JSONObject email : emails) {
            String sender = email.getString("sender");
            String subject = email.optString("subject", "No Subject");
            String body = email.getString("body");
            String date = email.optString("date", "");
            int id = email.optInt("id", 0);
            
            // Format the query to include email context
            String query = String.format("EMAIL ID: %d\nDATE: %s\nSENDER: %s\nSUBJECT: %s\nBODY: %s", 
                                        id, date, sender, subject, body);
            
            // Create a thread for this email
            List<JSONObject> messages = List.of(
                new JSONObject()
                    .put("role", "user")
                    .put("content", query)
            );
            
            String threadId = assistant.createThread(messages, null, null);
            if (threadId == null) {
                responses.add("Failed to process email due to thread creation error.");
                continue;
            }
            
            // Run the assistant
            String runId = assistant.createRun(
                threadId, assistantId, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
            );
            
            if (runId == null) {
                responses.add("Failed to process email due to run creation error.");
                continue;
            }
            
            boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);
            if (!completed) {
                responses.add("Failed to process email due to timeout or processing error.");
                continue;
            }
            
            // Get the assistant's response
            List<String> retrievedMessages = assistant.listMessages(threadId, runId);
            if (retrievedMessages == null || retrievedMessages.isEmpty()) {
                responses.add("No response generated for email.");
            } else {
                responses.add(retrievedMessages.get(0));
            }

            // print the response to the console for debugging
            //System.out.println("Response for email ID " + id + ": " + responses.get(responses.size() - 1));
            
            // Clean up resources
            assistant.deleteResource("threads", threadId);
        }
        
        return responses;
    }
}
