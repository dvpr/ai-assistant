package com.example.llama;

public interface ParseCallback {
    void onSuccess(String key, String value, String category);
    void onFailure(String error);
}