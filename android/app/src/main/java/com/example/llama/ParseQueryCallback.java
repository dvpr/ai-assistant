package com.example.llama;

public interface ParseQueryCallback {
    void onSuccess(String target, String attribute);
    void onFailure(String error);
}