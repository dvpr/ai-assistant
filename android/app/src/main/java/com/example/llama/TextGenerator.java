package com.example.llama;

public interface TextGenerator {
    void generate(String prompt, Callback callback);
    
    interface Callback {
        void onStart();
        void onToken(String token);
        void onComplete(String fullText);
        void onError(String error);
    }
}