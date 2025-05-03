package com.acu.assistant;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {

    private static Config instance;
    private Properties properties;

    private Config() {
        properties = new Properties();
        try {
            // Load configuration from a properties file
            FileInputStream in = new FileInputStream("config.properties");
            properties.load(in);
            in.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load configuration file: " + e.getMessage());
        }
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    public String getDbServerName() {
        return properties.getProperty("db.servername");
    }

    public String getDbUsername() {
        return properties.getProperty("db.username");
    }

    public String getDbPassword() {
        return properties.getProperty("db.password");
    }

    public String getOpenAiApiKey() {
        return properties.getProperty("openai.api.key");
    }

    public String getEmailImapHost() {
        return properties.getProperty("email.imap.host");
    }

    public int getEmailPort() {
        return Integer.parseInt(properties.getProperty("email.port"));
    }

    public String getEmailAccount() {
        return properties.getProperty("email.account");
    }

    public String getEmailPassword() {
        return properties.getProperty("email.password");
    }

    public String getEmailFolder() {
        return properties.getProperty("email.folder");
    }

    public String getEmailServer() {
        return properties.getProperty("email.server");
    }

    public int getEmailSmtpPort() {
        return Integer.parseInt(properties.getProperty("email.smtp.port"));
    }

    public boolean getUseSmtp() {
        return Boolean.parseBoolean(properties.getProperty("email.use.smtp"));
    }
}
