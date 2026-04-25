package com.example.llama;

import android.os.Handler;
import android.os.Looper;

public class LocalGenerator implements TextGenerator {
    private final MainActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LocalGenerator(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void generate(String prompt, TextGenerator.Callback callback) {
        callback.onStart();
        
        new Thread(() -> {
            try {
                String result = activity.generateText(prompt);
                final String fullText = result != null ? result : "";
                
                mainHandler.post(() -> {
                    for (int i = 0; i < fullText.length(); i++) {
                        callback.onToken(String.valueOf(fullText.charAt(i)));
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    callback.onComplete(fullText);
                });
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }
}