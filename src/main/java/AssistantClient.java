
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class AssistantClient {

    private final OpenAiAssistantEngine engine;
    private String currentThreadId;
    private String currentAssistantId;

    // Configuration fields
    private String model = "gpt-3.5-turbo";
    private String description;
    private String reasoningEffort;
    private final List<String> tools = new ArrayList<>();
    private final Map<String, String> metadata = new HashMap<>();
    private Double temperature = 0.7;
    private Double topP;
    private final Map<String, String> toolResources = new HashMap<>();
    private Integer timeout = 30;
    private Integer pollRateMiliSeconds = 1000;
    private String additionalInstructions;

    public AssistantClient(String apiKey) {
        this.engine = new OpenAiAssistantEngine(apiKey);
    }

    // Builder methods
    public AssistantClient withModel(String model) {
        this.model = model;
        return this;
    }

    public AssistantClient withDescription(String description) {
        this.description = description;
        return this;
    }

    public AssistantClient withReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        return this;
    }

    public AssistantClient withTool(String tool) {
        this.tools.add(tool);
        return this;
    }

    public AssistantClient withMetadata(String key, String value) {
        this.metadata.put(key, value);
        return this;
    }

    public AssistantClient withTemperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    public AssistantClient withTopP(double topP) {
        this.topP = topP;
        return this;
    }

    public AssistantClient withToolResource(String key, String value) {
        this.toolResources.put(key, value);
        return this;
    }

    public AssistantClient withTimeout(int seconds) {
        this.timeout = seconds;
        return this;
    }

    public AssistantClient withPollRate(int milliseconds) {
        this.pollRateMiliSeconds = milliseconds;
        return this;
    }

    public AssistantClient withAdditionalInstructions(String instructions) {
        this.additionalInstructions = instructions;
        return this;
    }

    /**
     * Creates a new assistant with basic configuration.
     *
     * @param name Assistant name
     * @param instructions Basic instructions for the assistant
     * @return AssistantClient instance for chaining
     */
    public AssistantClient createAssistant(String name, String instructions) {
        String assistantId = engine.createAssistant(
                model,
                name,
                description,
                instructions,
                reasoningEffort,
                tools.isEmpty() ? null : tools,
                metadata.isEmpty() ? null : metadata,
                temperature,
                topP,
                toolResources.isEmpty() ? null : toolResources
        );

        if (assistantId != null) {
            this.currentAssistantId = assistantId;
        }
        return this;
    }

    /**
     * Starts a new conversation thread.
     *
     * @return AssistantClient instance for chaining
     */
    public AssistantClient startConversation() {
        String threadId = engine.createThread(null, null, null);
        if (threadId != null) {
            this.currentThreadId = threadId;
        }
        return this;
    }

    /**
     * Sends a message and waits for the assistant's response.
     *
     * @param message User's message
     * @return List of assistant's response messages, null if failed
     */
    public List<String> sendMessageAndWait(String message) {
        if (currentThreadId == null || currentAssistantId == null) {
            throw new IllegalStateException("No active conversation. Call startConversation() first.");
        }

        // Add the message to the thread
        String messageId = engine.addMessageToThread(currentThreadId, message);
        if (messageId == null) {
            return null;
        }

        // Create and run the assistant
        String runId = engine.createRun(
                currentThreadId,
                currentAssistantId,
                model,
                reasoningEffort,
                null, // Use assistant's default instructions
                null, // No additional instructions
                null, // No additional messages
                tools.isEmpty() ? null : tools.stream().map(tool -> new JSONObject().put("type", tool)).toList(),
                metadata.isEmpty() ? null : metadata,
                temperature,
                topP,
                false, // No streaming
                null, // Default token limits
                null, // Default token limits
                null, // Default truncation strategy
                null, // Default tool choice
                null, // Default parallel tool calls
                null // Default response format
        );

        if (runId == null) {
            return null;
        }

        // Wait for the run to complete (timeout from builder)
        boolean completed = engine.waitForRunCompletion(currentThreadId, runId, timeout, pollRateMiliSeconds);
        if (!completed) {
            return null;
        }

        // Return the messages from this run
        return engine.listMessages(currentThreadId, runId);
    }

    /**
     * Sends multiple messages and waits for the assistant's response.
     *
     * @param messages List of user's messages
     * @return List of assistant's response messages, null if failed
     */
    public List<String> sendMessagesAndWait(List<String> messages) {
        if (currentThreadId == null || currentAssistantId == null) {
            throw new IllegalStateException("No active conversation. Call startConversation() first.");
        }
        for (String msg : messages) {
            engine.addMessageToThread(currentThreadId, msg);
        }

        // Create and run with possible additional instructions
        String runId = engine.createRun(
                currentThreadId,
                currentAssistantId,
                model,
                reasoningEffort,
                additionalInstructions,
                null,
                null,
                tools.isEmpty() ? null : tools.stream().map(tool -> new JSONObject().put("type", tool)).toList(),
                metadata.isEmpty() ? null : metadata,
                temperature,
                topP,
                false, // No streaming
                null, // Default token limits
                null, // Default token limits
                null, // Default truncation strategy
                null, // Default tool choice
                null, // Default parallel tool calls
                null // Default response format
        );

        if (runId == null) {
            return null;
        }

        // Wait for the run to complete (timeout from builder)
        boolean completed = engine.waitForRunCompletion(currentThreadId, runId, timeout, pollRateMiliSeconds);
        if (!completed) {
            return null;
        }

        // Return the messages from this run
        return engine.listMessages(currentThreadId, runId);
    }

    /**
     * Sends a message and returns just the last response.
     *
     * @param message User's message
     * @return Last response from the assistant, null if failed
     */
    public String sendMessage(String message) {
        List<String> responses = sendMessageAndWait(message);
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        return responses.get(responses.size() - 1);
    }

    /**
     * Ends the current conversation and cleans up resources.
     */
    public void endConversation() {
        if (currentThreadId != null) {
            engine.deleteResource("threads", currentThreadId);
            currentThreadId = null;
        }
    }

    /**
     * Deletes the current assistant and cleans up resources.
     */
    public void deleteAssistant() {
        if (currentAssistantId != null) {
            engine.deleteResource("assistants", currentAssistantId);
            currentAssistantId = null;
        }
    }

    public String getCurrentThreadId() {
        return currentThreadId;
    }

    public String getCurrentAssistantId() {
        return currentAssistantId;
    }

    public OpenAiAssistantEngine getEngine() {
        return engine;
    }
}
