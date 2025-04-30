
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class Chatbot_API {

    static OpenAiAssistantEngine assistant;

    //this wil be set in the environment variables on server
    private static final String APIKEY = System.getenv("OPENAI_API_KEY");

    // this file needs to be gotten from the parser
    private static final File USER_INFO_FILE = new File("user_info.txt");

    //this will always be the same
    private static final File ACU_DATABASE_FILE = new File("acu_database.txt");

    public static void main(String[] args) {
        assistant = new OpenAiAssistantEngine(APIKEY);

        // this will be gotten from the recieve module, for now we will just use an example thread
        List<JSONObject> oldMessages = createExampleThread();

        //this will be gotten from the email parser
        String userMessage = "what was the last question I asked you?";

        String assistantId = setupAssistant();
        if (assistantId == null) {
            System.out.println("Failed to set up assistant");
            return;
        }

        String response = processUserMessage(assistantId, oldMessages, userMessage);
        System.out.println(response);

        // Clean up resources
        assistant.deleteResource("assistants", assistantId);
    }

    /**
     * Creates an example thread with previous conversation history.
     *
     * @return A list of JSONObjects representing messages in the thread
     */
    public static List<JSONObject> createExampleThread() {
        List<JSONObject> messages = new ArrayList<>();
        JSONObject userMessage1 = new JSONObject()
                .put("role", "user")
                .put("content", "Hello, I'm interested in learning about my degree plan.");

        JSONObject assistantMessage1 = new JSONObject()
                .put("role", "assistant")
                .put("content", "Hello! I'd be happy to help you with information about your degree plan. What specific aspects would you like to know about?");

        JSONObject userMessage2 = new JSONObject()
                .put("role", "user")
                .put("content", "What classes do I need to take next semester?");

        JSONObject assistantMessage2 = new JSONObject()
                .put("role", "assistant")
                .put("content", "Based on your current progress, I recommend taking the following courses next semester: CS 330 (Database Management Systems), MATH 227 (Discrete Mathematics), and BIOL 112 (General Biology II). Would you like more details about any of these courses?");

        messages.add(userMessage1);
        messages.add(assistantMessage1);
        messages.add(userMessage2);
        messages.add(assistantMessage2);

        return messages;
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

    /**
     * Process a single user message with context from previous messages
     *
     * @param assistantId The ID of the configured assistant
     * @param oldMessages List of previous messages in the conversation
     * @param userMessage The new message from the user
     * @return The assistant's response
     */
    public static String processUserMessage(String assistantId, List<JSONObject> oldMessages, String userMessage) {
        // Create a thread with previous messages if they exist
        String threadId = assistant.createThread(oldMessages, null, null);
        if (threadId == null) {
            return "Failed to create thread for processing message.";
        }

        // Add the new user message to the thread
        String messageId = assistant.addMessageToThread(threadId, userMessage);
        if (messageId == null) {
            assistant.deleteResource("threads", threadId);
            return "Failed to add message to thread.";
        }

        // Run the assistant
        String runId = assistant.createRun(
                threadId,
                assistantId,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null
        );

        if (runId == null) {
            assistant.deleteResource("threads", threadId);
            return "Failed to create run for processing message.";
        }

        // Wait for the assistant to process the message
        boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);
        if (!completed) {
            assistant.deleteResource("threads", threadId);
            return "The assistant encountered an issue while processing the message.";
        }

        // Get the assistant's response
        List<String> retrievedMessages = assistant.listMessages(threadId, runId);
        String response = "No response received from the assistant.";

        if (retrievedMessages != null && !retrievedMessages.isEmpty()) {
            response = retrievedMessages.get(0);
        }

        // Clean up the thread
        assistant.deleteResource("threads", threadId);

        return response;
    }
}
