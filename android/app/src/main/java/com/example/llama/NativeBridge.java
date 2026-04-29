package com.example.llama;

/**
 * JNI 桥接类：集中声明所有 native 方法，避免在 Activity 中直接声明。
 */
public class NativeBridge {
    static {
        System.loadLibrary("wrapper");
    }

    private static MainActivity activityRef;  // 静态引用，用于转发回调

    public static void setActivity(MainActivity activity) {
        activityRef = activity;
    }

    // 供 JNI 调用的回调方法
    public void onStreamChunk(String chunk) {
        if (activityRef != null) {
            activityRef.onStreamChunk(chunk);
        }
    }

    public void onStreamFinish() {
        if (activityRef != null) {
            activityRef.onStreamFinish();
        }
    }

    // Native 方法声明（必须与 C++ 中的函数名严格匹配）
    public native boolean loadModel(String modelPath);
    public native float[] getEmbedding(String input);
    public native String generateText(String prompt);
    public native void registerStreamCallback();
    public native void cleanup();
    public native void setDebugLogger(DebugLogger logger);
}