# OpenAI Assistant API Java Client

## Overview
This project provides a Java interface to OpenAI's Assistant API (beta, v2). It consists of two main components:

1. **OpenAiAssistantEngine**: A low-level client that directly maps to the OpenAI Assistant API endpoints.
2. **AssistantClient**: A high-level, user-friendly client with fluent builder pattern for easier configuration.

## AssistantClient Usage

### 1. Create an Assistant Client
```java
// Initialize with your OpenAI API key
AssistantClient client = new AssistantClient("YOUR_API_KEY");
```

### 2. Configure and Create an Assistant
Use the fluent builder pattern to configure and create a new assistant:
```java
client.withModel("gpt-4-turbo")
      .withDescription("A helpful coding assistant")
      .withTool("code_interpreter")
      .withTool("file_search")
      .withTemperature(0.7)
      .withTimeout(60) // seconds
      .createAssistant("Coding Helper", "You are a programming assistant that helps with coding tasks.");
```

### 3. Start a Conversation
```java
client.startConversation();
```

### 4. Send Messages and Get Responses
```java
// Send a message and get the response
String response = client.sendMessage("How do I create a binary search tree in Java?");
System.out.println(response);

// Or get all messages in the response
List<String> allResponses = client.sendMessageAndWait("Can you explain recursion?");
allResponses.forEach(System.out::println);
```

### 5. Clean Up Resources
```java
// End the conversation when done
client.endConversation();

// Delete the assistant if no longer needed
client.deleteAssistant();
```

## OpenAiAssistantEngine (Low-level API)

### Overview
OpenAiAssistantEngine is a Java library designed to communicate with OpenAI's Assistant API (beta, v2). It provides custom methods to:
- Upload files.
- Create vector stores.
- Create and manage assistants.
- Create threads to hold conversation flows.
- Run queries and retrieve responses.
- Log responses and handle errors effectively.

### Usage

#### 1. Instantiate the Engine
Provide your OpenAI API key when constructing the engine:
```java
OpenAiAssistantEngine engine = new OpenAiAssistantEngine("YOUR_API_KEY");
```
This key is used for all subsequent requests.

#### 2. Upload Files
You can upload files for the assistant to reference:
```java
File myFile = new File("path/to/file.txt");
String fileId = engine.uploadFile(myFile, "assistants");
```
If successful, `fileId` will be the unique identifier for the uploaded file.

#### 3. Create Vector Store
Once files are uploaded, you can create a vector store:
```java
String vectorStoreId = engine.createVectorStore(
    "MyVectorStore", 
    List.of(fileId), 
    null, 
    null, 
    null
);
```
This groups your files into a searchable index.

#### 4. Create an Assistant
Set up a new assistant with a specified model and other optional parameters:
```java
String assistantId = engine.createAssistant(
    "gpt-4-1106-preview",
    "My Assistant",
    "An example assistant",
    "Global instructions here",
    "auto",
    List.of("file_search"),
    null,
    0.7,
    1.0,
    null
);
```

#### 5. Create & Use Threads
A thread is a container for conversation messages:
```java
// Creating a new thread with an initial user message
List<JSONObject> initialMessages = List.of(
    new JSONObject().put("role", "user").put("content", "Hello!")
);
String threadId = engine.createThread(initialMessages, null, null);
```
Adding messages:
```java
engine.addMessageToThread(threadId, "How are you?");
```
Then create a run to get the assistant’s response and poll for completion:
```java
String runId = engine.createRun(
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
boolean completed = engine.waitForRunCompletion(threadId, runId, 60);
```
Retrieve the assistant’s messages:
```java
List<String> messages = engine.listMessages(threadId, runId);
messages.forEach(System.out::println);
```

#### 6. Error Handling
The engine provides detailed error messages for various HTTP response codes. For example:
- **400**: Bad Request
- **401**: Unauthorized
- **429**: Too Many Requests
- **500**: Internal Server Error

Errors are logged to the console with detailed descriptions and possible solutions. Example:
```java
// If an error occurs during a request, it will be logged automatically
engine.uploadFile(new File("invalid/path"), "assistants");
```

#### 7. Response Logging
The engine logs responses by category (e.g., "file_upload", "assistant", "thread"). You can retrieve or clear these logs:
```java
// Retrieve all responses for a category
List<String> fileUploadResponses = engine.getResponsesByCategory("file_upload");

// Get the latest response
String latestResponse = engine.getLatestResponse("file_upload");

// Clear responses for a category
engine.clearCategory("file_upload");

// Clear all responses
engine.clearAllResponses();
```

#### 8. Update an Assistant
You can change assistant properties, such as adding more tool resources:
```java
Map<String, Object> toolResources = new HashMap<>();
toolResources.put("file_search", Map.of("vector_store_ids", List.of(vectorStoreId)));
boolean updateSuccess = engine.updateAssistant(assistantId, toolResources);
```

#### 9. Housekeeping
- You can delete resources if needed:
```java
engine.deleteResource("threads", threadId);
engine.deleteResource("assistants", assistantId);
```

## Contributing
Contributions are welcome. Please fork this repository and submit pull requests.

## License
This project is licensed under the MIT License.
