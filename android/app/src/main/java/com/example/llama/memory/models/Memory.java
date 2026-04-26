package com.example.llama.memory.models;

public class Memory {
    private long id;
    private long categoryId;
    private String keyText;
    private String valueText;
    private String rawInput;
    private long createdAt;
    
    public Memory(long id, long categoryId, String keyText, String valueText, 
                  String rawInput, long createdAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.keyText = keyText;
        this.valueText = valueText;
        this.rawInput = rawInput;
        this.createdAt = createdAt;
    }
    
    public long getId() { return id; }
    public long getCategoryId() { return categoryId; }
    public String getKeyText() { return keyText; }
    public String getValueText() { return valueText; }
    public String getRawInput() { return rawInput; }
    public long getCreatedAt() { return createdAt; }
    
    public void setValueText(String valueText) { this.valueText = valueText; }
}