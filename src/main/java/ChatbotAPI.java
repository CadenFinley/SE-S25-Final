package com.acu.assistant;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ChatbotAPI {

    private static OpenAiAssistantEngine assistant;
    private static String APIKEY;
    private static String ACU_DATABASE_FILE;

    public ChatbotAPI() {
        Config config = Config.getInstance();
        APIKEY = config.getOpenAiApiKey();
        String baseDir = System.getProperty("user.dir");
        ACU_DATABASE_FILE = Paths.get(baseDir, "resources", "acu_database.txt").toString();

        assistant = new OpenAiAssistantEngine(APIKEY);
    }

    public String run(String message, List<Map<String, String>> conversation, String userName) {

        String assistantId = setupAssistant(userName);
        if (assistantId == null) {
            System.out.println("Failed to set up assistant");
            return null;
        }

        String response = processUserMessage(assistantId, conversation, message, userName);
        System.out.println(response);

        assistant.deleteResource("assistants", assistantId);

        return response;
    }

    public String setupAssistant(String userName) {

        String instructionsBase = "You are a email based AI Academic Advisor chatbot for Abilene Christian University only ever adress yourself as such. ";

        if (userName != null && !userName.isEmpty()) {
            instructionsBase += "Address the student as " + userName + " for all reponses. ";
        } else {
            instructionsBase += "Address the student by their first and last name. ";
        }

        String instructions = instructionsBase + "Provide information about the student's academic journey, courses, and other academic-related topics.\n\n"
                + "The acu_database.txt file contains important tables with academic data structured as follows:\n"
                + "- prereq: Lists course prerequisites\n"
                + "- section_course: Maps course IDs to section IDs\n"
                + "- college: Lists colleges and their names\n"
                + "- student: Contains student IDs with first and last names\n"
                + "- department: Lists departments and their associated colleges\n"
                + "- course: Provides detailed course information including department, title, number, and credit hours\n"
                + "- major: Lists majors with department IDs and requirements\n"
                + "- teachers: Lists faculty members with their departments\n"
                + "- section: Contains details about course sections including room, term dates, and schedules\n"
                + "- student_section: Records student enrollments in course sections\n"
                + "- major_class: Maps major requirements to classes\n"
                + "- and_prereq/or_prereq/coreq: Different types of course prerequisites\n"
                + "- student_major: Records student's declared major\n"
                + "- concentration: Lists available concentrations within majors\n\n"
                + "Use this information to provide accurate academic advising to students including degree requirements, course selection, and academic planning."
                + "\n\nWhen searching the ACU database, look for table headers to identify the relevant table structure. "
                + "The database file contains multiple tables with headers like 'Table: [tableName]'. "
                + "Extract information from the appropriate tables based on the student's question. "
                + "For questions about prerequisites, check both the 'prereq', 'and_prereq', 'or_prereq', and 'coreq' tables. "
                + "For course information, reference the 'course' table. "
                + "For student schedules, combine data from 'student', 'section', and 'student_section' tables.\nPlease return your response in a format suitable for professional/educational emails. In the signature for the email say 'Best regards, AI ACU Academic Advisor";

        String assistantId = assistant.createAssistant(
                "gpt-4o",
                "Abilene Christian University Academic Advisor",
                null,
                instructions,
                null,
                new String[]{"file_search"},
                null,
                0.1,
                0.1,
                null
        );

        if (assistantId == null) {
            System.out.println("Failed to create assistant");
            return null;
        }

        if (!java.nio.file.Files.exists(java.nio.file.Paths.get(ACU_DATABASE_FILE))) {
            System.out.println("ACU database file not found at: " + ACU_DATABASE_FILE);
            return null;
        }

        String acuDatabaseFileID = assistant.uploadFile(ACU_DATABASE_FILE, "assistants");

        Map<String, String> fileMetadata = Map.of(
                acuDatabaseFileID, "ACU database with tables for courses, prerequisites, sections, majors, departments, and student information. Tables are formatted with headers and data rows separated by whitespace."
        );

        String vectorStoreId = assistant.createVectorStore(
                "ACU Academic Database",
                new String[]{acuDatabaseFileID},
                null,
                null,
                fileMetadata
        );

        if (vectorStoreId == null) {
            System.out.println("Failed to create vector store");
            return null;
        }

        Map<String, Object> toolResources = Map.of(
                "file_search", Map.of(
                        "vector_store_ids", new String[]{vectorStoreId}
                )
        );

        List<Map<String, String>> tools = List.of(
                Map.of("type", "file_search")
        );

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
                tools,
                null
        );

        if (!updateSuccess) {
            System.out.println("Failed to update assistant with vector store");
            return null;
        }

        System.out.println("Assistant setup successfully with ID: " + assistantId);
        return assistantId;
    }

    public String processUserMessage(String assistantId, List<Map<String, String>> conversation, String userMessage, String userName) {
        String threadId;
        if (conversation != null && !conversation.isEmpty()) {
            List<Map<String, String>> formattedMessages = conversation.stream()
                    .filter(msg -> msg.containsKey("role") && msg.containsKey("content"))
                    .map(msg -> Map.of(
                    "role", msg.get("role"),
                    "content", msg.get("content")
            ))
                    .collect(java.util.stream.Collectors.toList());

            threadId = assistant.createThread(formattedMessages, null, null);
        } else {
            threadId = assistant.createThread(List.of(), null, null);
        }

        if (threadId == null) {
            return "Failed to create thread for processing message.";
        }

        String messageId = assistant.addMessageToThread(threadId, userMessage);
        if (messageId == null) {
            assistant.deleteResource("threads", threadId);
            return "Failed to add message to thread.";
        }

        String runId = assistant.createRun(
                threadId,
                assistantId,
                null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null
        );

        if (runId == null) {
            assistant.deleteResource("threads", threadId);
            return "Failed to create run for processing message.";
        }

        boolean completed = assistant.waitForRunCompletion(threadId, runId, 60, 1000);
        if (!completed) {
            assistant.deleteResource("threads", threadId);
            return "The assistant encountered an issue while processing the message.";
        }

        List<String> retrievedMessages = assistant.listMessages(threadId, runId);
        String response = "No response received from the assistant.";

        if (retrievedMessages != null && !retrievedMessages.isEmpty()) {
            response = retrievedMessages.get(0);
            response = cleanupResponse(response);
        }

        assistant.deleteResource("threads", threadId);

        return response;
    }

    private String cleanupResponse(String response) {
        String cleanedResponse = response.replaceAll("\\【\\d+:\\d+†source\\】", "");

        if (cleanedResponse.trim().isEmpty()) {
            return response;
        }

        cleanedResponse = cleanedResponse.replace("\r\n", "\n");
        cleanedResponse = cleanedResponse.replace("\r", "\n");

        String[] paragraphs = cleanedResponse.split("\n\n");
        StringBuilder result = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                result.append("\n");
                continue;
            }

            String[] lines = paragraph.split("\n");
            StringBuilder wrappedParagraph = new StringBuilder();

            for (String line : lines) {
                String wrapped = wordWrap(line, 50);
                wrappedParagraph.append(wrapped).append("\n");
            }

            result.append(wrappedParagraph).append("\n");
        }

        String[] finalLines = result.toString().split("\n");
        StringBuilder strictlyWrapped = new StringBuilder();

        for (String line : finalLines) {
            if (line.length() > 70) {
                for (int i = 0; i < line.length(); i += 50) {
                    int endIndex = Math.min(i + 50, line.length());
                    strictlyWrapped.append(line.substring(i, endIndex)).append("\n");
                }
            } else {
                strictlyWrapped.append(line).append("\n");
            }
        }

        return strictlyWrapped.toString();
    }

    private String wordWrap(String text, int lineLength) {
        if (text == null || text.length() <= lineLength) {
            return text;
        }

        StringBuilder wrapped = new StringBuilder();
        int lastSpace = -1;
        int lineStart = 0;
        int i = 0;

        for (; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                lastSpace = i;
            }

            if (i > lineStart + lineLength - 1) {
                if (lastSpace != -1) {
                    wrapped.append(text.substring(lineStart, lastSpace));
                    wrapped.append("\n");
                    lineStart = lastSpace + 1;
                    lastSpace = -1;
                } else {
                    wrapped.append(text.substring(lineStart, i));
                    wrapped.append("\n");
                    lineStart = i;
                }
            }
        }

        wrapped.append(text.substring(lineStart));
        return wrapped.toString();
    }

    public boolean cleanupAssistant(String assistantId) {
        return assistant.deleteResource("assistants", assistantId);
    }
}
