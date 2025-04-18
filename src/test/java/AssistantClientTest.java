
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AssistantClientTest {

    private static AssistantClient client;
    private static final String TEST_API_KEY = System.getenv("OPENAI_API_KEY");

    @BeforeAll
    static void setUp() {
        assertNotNull(TEST_API_KEY, "API key must be set in environment variables");
        client = new AssistantClient(TEST_API_KEY);
    }

    @Test
    void testCreateAssistantAndStartConversation() {
        client.createAssistant("Test Assistant", "You are a test assistant");
        assertNotNull(client.getCurrentAssistantId(), "Assistant ID should not be null");

        client.startConversation();
        assertNotNull(client.getCurrentThreadId(), "Thread ID should not be null");
    }

    @Test
    void testSendMessageAndWait() {
        client.createAssistant("Test Assistant", "You are a test assistant");
        client.startConversation();

        List<String> responses = client.sendMessageAndWait("Hello");
        assertNotNull(responses, "Responses should not be null");
        assertFalse(responses.isEmpty(), "Responses should not be empty");
    }

    @Test
    void testEndConversation() {
        client.startConversation();
        assertNotNull(client.getCurrentThreadId(), "Thread ID should not be null");

        client.endConversation();
        assertNull(client.getCurrentThreadId(), "Thread ID should be null after ending conversation");
    }

    @Test
    void testDeleteAssistant() {
        client.createAssistant("Test Assistant", "You are a test assistant");
        assertNotNull(client.getCurrentAssistantId(), "Assistant ID should not be null");

        client.deleteAssistant();
        assertNull(client.getCurrentAssistantId(), "Assistant ID should be null after deletion");
    }
}
