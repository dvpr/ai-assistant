package com.example.llama;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 模型下载器：从给定 URL 下载 GGUF 模型文件到应用私有目录。
 */
public class ModelDownloader {
    private final DebugLogger logger;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ModelDownloader(DebugLogger logger) {
        this.logger = logger;
    }

    /**
     * 异步下载模型。
     * @param urlStr       下载地址
     * @param destFile     目标文件
     * @param callback     进度/结果回调
     */
    public void download(String urlStr, File destFile, DownloadCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent", "Android-App");
                connection.connect();

                int contentLength = connection.getContentLength();
                if (contentLength <= 0) {
                    mainHandler.post(() -> callback.onError("无法获取文件大小，URL可能无效"));
                    return;
                }
                logger.log("开始下载，总大小: " + (contentLength / 1024 / 1024) + " MB");

                try (InputStream is = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    long downloaded = 0;
                    long lastLog = System.currentTimeMillis();

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        long now = System.currentTimeMillis();
                        if (now - lastLog >= 5000) { // 每5秒更新一次进度
                            final int percent = (int) (downloaded * 100 / contentLength);
                            mainHandler.post(() -> callback.onProgress(percent));
                            lastLog = now;
                        }
                    }
                }

                mainHandler.post(() -> callback.onSuccess(destFile));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File modelFile);
        void onError(String error);
    }
}