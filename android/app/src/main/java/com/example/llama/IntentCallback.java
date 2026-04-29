package com.example.llama;

public interface IntentCallback {
    void onStore(String key, String value, String category);
    void onQuery(String keywords);
    void onChat();
    void onError(String error);
}