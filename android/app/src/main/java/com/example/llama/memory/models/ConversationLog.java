package com.example.llama.memory.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConversationLog {
    private long id;
    private String sessionId;
    private String userInput;
    private String aiResponse;
    private String intentType;
    private long createdAt;
    
    public ConversationLog(long id, String sessionId, String userInput, String aiResponse,
                           String intentType, long createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.userInput = userInput;
        this.aiResponse = aiResponse;
        this.intentType = intentType;
        this.createdAt = createdAt;
    }
    
    public long getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getUserInput() { return userInput; }
    public String getAiResponse() { return aiResponse; }
    public String getIntentType() { return intentType; }
    public long getCreatedAt() { return createdAt; }
    
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(createdAt));
    }
    
    public String getPreview() {
        int maxLen = 50;
        String input = userInput.length() > maxLen ? userInput.substring(0, maxLen) + "..." : userInput;
        String response = aiResponse.length() > maxLen ? aiResponse.substring(0, maxLen) + "..." : aiResponse;
        return input + " → " + response;
    }
}