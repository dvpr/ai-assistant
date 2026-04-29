package com.example.llama;

import android.os.Handler;
import android.os.Looper;
import java.io.File;

/**
 * 本地模型加载辅助类：封装 JNI 调用，负责模型的加载、切换、资源清理。
 */
public class ModelLoaderHelper {
    private final DebugLogger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NativeBridge nativeBridge;
    private String currentModelPath = null;

    public ModelLoaderHelper(DebugLogger logger, NativeBridge nativeBridge) {
        this.logger = logger;
        this.nativeBridge = nativeBridge;
    }

    /**
     * 加载模型（异步，回调在主线程）。
     * @param modelFile 模型文件
     * @param callback  加载结果回调
     */
    public void loadModel(File modelFile, LoadCallback callback) {
        new Thread(() -> {
            boolean success = nativeBridge.loadModel(modelFile.getAbsolutePath());
            mainHandler.post(() -> {
                if (success) {
                    currentModelPath = modelFile.getAbsolutePath();
                    logger.log("模型加载成功: " + currentModelPath);
                    callback.onSuccess();
                } else {
                    logger.log("模型加载失败: " + modelFile.getAbsolutePath());
                    callback.onError("模型加载失败");
                }
            });
        }).start();
    }

    /** 切换本地模型：先清理旧资源，再加载新模型。 */
    public void switchToLocalModel(File modelFile, LoadCallback callback) {
        // 清理旧模型（包括上下文和模型本身）
        nativeBridge.cleanup();
        loadModel(modelFile, callback);
    }

    /** 获取当前已加载模型的路径（如果有） */
    public String getCurrentModelPath() {
        return currentModelPath;
    }

    public interface LoadCallback {
        void onSuccess();
        void onError(String error);
    }
}