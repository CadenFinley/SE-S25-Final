
import java.io.File;
import java.util.List;

import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenAiAssistantEngineTest {

    private static OpenAiAssistantEngine engine;
    private static String testAssistantId;
    private static String testThreadId;
    private static String testFileId;
    private static final String TEST_API_KEY = System.getenv("OPENAI_API_KEY");

    @BeforeAll
    static void setUp() {
        assertNotNull(TEST_API_KEY, "API key must be set in environment variables");
        engine = new OpenAiAssistantEngine(TEST_API_KEY);
    }

    @Test
    @Order(1)
    void testConstructor() {
        assertNotNull(engine);
        OpenAiAssistantEngine customEngine = new OpenAiAssistantEngine(TEST_API_KEY, 50);
        assertNotNull(customEngine);
    }

    @Test
    @Order(2)
    void testFileUpload() {
        File testFile = new File("user_info.txt");
        testFileId = engine.uploadFile(testFile, "assistants");
        assertNotNull(testFileId, "File upload should return a valid ID");
    }

    @Test
    @Order(3)
    void testCreateAssistant() {
        testAssistantId = engine.createAssistant(
                "gpt-3.5-turbo",
                "Test Assistant",
                "Test Description",
                "You are a test assistant",
                null,
                List.of("file_search"),
                null,
                0.7,
                0.7,
                null
        );
        assertNotNull(testAssistantId, "Assistant creation should return a valid ID");
    }

    @Test
    @Order(4)
    void testCreateThread() {
        List<JSONObject> messages = List.of(
                new JSONObject()
                        .put("role", "user")
                        .put("content", "Hello")
        );
        testThreadId = engine.createThread(messages, null, null);
        assertNotNull(testThreadId, "Thread creation should return a valid ID");
    }

    @Test
    @Order(5)
    void testAddMessageToThread() {
        assertNotNull(testThreadId, "Thread ID must be available");
        String messageId = engine.addMessageToThread(testThreadId, "Test message");
        assertNotNull(messageId, "Message addition should return a valid ID");
    }

    @Test
    @Order(6)
    void testRetrieveAssistant() {
        assertNotNull(testAssistantId, "Assistant ID must be available");
        String assistantInfo = engine.retrieveAssistant(testAssistantId);
        assertNotNull(assistantInfo, "Retrieved assistant info should not be null");
    }

    @Test
    @Order(7)
    void testListMessages() {
        assertNotNull(testThreadId, "Thread ID must be available");
        // List messages without run_id parameter
        List<String> messages = engine.listMessages(testThreadId, null);
        assertNotNull(messages, "Messages list should not be null");
    }

    @Test
    @Order(8)
    void testCreateAndRetrieveRun() {
        assertNotNull(testThreadId, "Thread ID must be available");
        assertNotNull(testAssistantId, "Assistant ID must be available");

        // Cancel any existing runs first
        String runStatus = engine.retrieveRunStatus(testThreadId);
        if (runStatus != null && runStatus.contains("\"status\":\"in_progress\"")) {
            JSONObject statusObj = new JSONObject(runStatus);
            String existingRunId = statusObj.getString("id");
            engine.cancelRun(testThreadId, existingRunId);
        }

        String runId = engine.createRun(testThreadId, testAssistantId, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertNotNull(runId, "Run creation should return a valid ID");

        String runInfo = engine.retrieveRun(testThreadId, runId);
        assertNotNull(runInfo, "Retrieved run info should not be null");
    }

    @Test
    @Order(9)
    void testWaitForRunCompletion() {
        assertNotNull(testThreadId, "Thread ID must be available");
        assertNotNull(testAssistantId, "Assistant ID must be available");

        // Cancel any existing runs first
        String runStatus = engine.retrieveRunStatus(testThreadId);
        if (runStatus != null && runStatus.contains("\"status\":\"in_progress\"")) {
            JSONObject statusObj = new JSONObject(runStatus);
            String existingRunId = statusObj.getString("id");
            engine.cancelRun(testThreadId, existingRunId);
        }

        String runId = engine.createRun(testThreadId, testAssistantId, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertNotNull(runId, "Run creation should return a valid ID");

        boolean completed = engine.waitForRunCompletion(testThreadId, runId, 30, 1000);
        assertTrue(completed, "Run should complete within timeout");
    }

    @Test
    @Order(10)
    void testCancelRun() {
        assertNotNull(testThreadId, "Thread ID must be available");
        assertNotNull(testAssistantId, "Assistant ID must be available");

        // Cancel any existing runs first
        String runStatus = engine.retrieveRunStatus(testThreadId);
        if (runStatus != null && runStatus.contains("\"status\":\"in_progress\"")) {
            JSONObject statusObj = new JSONObject(runStatus);
            String existingRunId = statusObj.getString("id");
            engine.cancelRun(testThreadId, existingRunId);
        }

        String runId = engine.createRun(testThreadId, testAssistantId, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
        assertNotNull(runId, "Run creation should return a valid ID");

        String cancelResponse = engine.cancelRun(testThreadId, runId);
        assertNotNull(cancelResponse, "Cancel run should return a response");
    }

    @Test
    @Order(11)
    void testResourceDeletion() {
        // Test deleting thread
        assertTrue(engine.deleteResource("threads", testThreadId), "Thread deletion should succeed");

        // Test deleting assistant
        assertTrue(engine.deleteResource("assistants", testAssistantId), "Assistant deletion should succeed");

        // Test deleting file
        assertTrue(engine.deleteResource("files", testFileId), "File deletion should succeed");
    }
}
