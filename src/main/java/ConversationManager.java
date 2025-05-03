package com.acu.assistant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConversationManager {

    private Connection db;

    public ConversationManager() throws SQLException {
        Config config = Config.getInstance();
        String servername = config.getDbServerName();
        String username = config.getDbUsername();
        String password = config.getDbPassword();
        String dbName = "acu_assistant_db";

        try {
            this.db = DriverManager.getConnection("jdbc:mysql://" + servername + "/" + dbName, username, password);
        } catch (SQLException e) {
            throw new SQLException("Database connection failed: " + e.getMessage());
        }

        setupTables();
    }

    private void setupTables() throws SQLException {
        try (Statement stmt = db.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS conversations ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "email_address VARCHAR(255) NOT NULL, "
                    + "user_name VARCHAR(255), "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "INDEX (email_address)"
                    + ")");

            stmt.execute("CREATE TABLE IF NOT EXISTS messages ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY, "
                    + "conversation_id INT NOT NULL, "
                    + "is_user BOOLEAN NOT NULL, "
                    + "content TEXT NOT NULL, "
                    + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE, "
                    + "INDEX (conversation_id)"
                    + ")");
        }
    }

    public int getOrCreateConversation(String emailAddress) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement("SELECT id FROM conversations WHERE email_address = ?")) {
            stmt.setString(1, emailAddress);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            } else {
                try (PreparedStatement insertStmt = db.prepareStatement(
                        "INSERT INTO conversations (email_address) VALUES (?)",
                        Statement.RETURN_GENERATED_KEYS)) {

                    insertStmt.setString(1, emailAddress);
                    insertStmt.executeUpdate();

                    ResultSet generatedKeys = insertStmt.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating conversation failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public int addMessage(int conversationId, String content, boolean isUser) throws SQLException {
        try (PreparedStatement stmt = db.prepareStatement(
                "INSERT INTO messages (conversation_id, is_user, content) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, conversationId);
            stmt.setBoolean(2, isUser);
            stmt.setString(3, content);
            stmt.executeUpdate();

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            } else {
                throw new SQLException("Creating message failed, no ID obtained.");
            }
        }
    }

    public List<Map<String, Object>> getConversationHistory(String emailAddress, int limit) throws SQLException {
        List<Map<String, Object>> messages = new ArrayList<>();

        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT m.is_user, m.content, m.timestamp "
                + "FROM messages m "
                + "JOIN conversations c ON m.conversation_id = c.id "
                + "WHERE c.email_address = ? "
                + "ORDER BY m.timestamp ASC "
                + "LIMIT ?")) {

            stmt.setString(1, emailAddress);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> message = new HashMap<>();
                message.put("role", rs.getBoolean("is_user") ? "user" : "assistant");
                message.put("content", rs.getString("content"));
                message.put("timestamp", rs.getTimestamp("timestamp").toString());
                messages.add(message);
            }
        }

        return messages;
    }

    public List<Map<String, String>> formatHistoryForAssistant(List<Map<String, Object>> history) {
        return history.stream()
                .map(msg -> Map.of(
                "role", (String) msg.get("role"),
                "content", (String) msg.get("content")
        ))
                .collect(Collectors.toList());
    }

    public String getUserName(String emailAddress, String name) throws SQLException {
        if (name != null && !name.isEmpty()) {
            try (PreparedStatement stmt = db.prepareStatement(
                    "UPDATE conversations SET user_name = ? WHERE email_address = ?")) {
                stmt.setString(1, name);
                stmt.setString(2, emailAddress);
                stmt.executeUpdate();
            }
        }

        try (PreparedStatement stmt = db.prepareStatement(
                "SELECT user_name FROM conversations WHERE email_address = ?")) {
            stmt.setString(1, emailAddress);
            ResultSet rs = stmt.executeQuery();

            String userName = null;
            if (rs.next()) {
                userName = rs.getString("user_name");
            }

            return userName;
        }
    }

    public String getUserName(String emailAddress) throws SQLException {
        return getUserName(emailAddress, null);
    }

    public String formatEmailContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        content = content.replace("\r\n", "\n").replace("\r", "\n");

        String[] paragraphs = content.split("\n\n");
        List<String> result = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                result.add("");
                continue;
            }

            String[] lines = paragraph.split("\n");
            List<String> wrappedLines = new ArrayList<>();

            for (String line : lines) {
                wrappedLines.add(wordWrap(line, maxLength));
            }

            result.add(String.join("\n", wrappedLines));
        }

        return String.join("\n\n", result);
    }

    public String formatEmailContent(String content) {
        return formatEmailContent(content, 70);
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

    public void close() {
        try {
            if (db != null && !db.isClosed()) {
                db.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}
