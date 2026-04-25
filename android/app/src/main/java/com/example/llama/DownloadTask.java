package com.example.llama;

import android.content.Context;
import android.os.AsyncTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask extends AsyncTask<String, Integer, String> {
    private final Context context;
    private final DebugLogger logger;
    private final ModelManager modelManager;
    private final Callback callback;
    private String currentFileName;
    
    public interface Callback {
        void onProgress(int percent);
        void onSuccess(String modelPath, String fileName);
        void onError(String error);
    }
    
    public DownloadTask(Context context, DebugLogger logger, ModelManager modelManager, Callback callback) {
        this.context = context;
        this.logger = logger;
        this.modelManager = modelManager;
        this.callback = callback;
    }
    
    @Override
    protected String doInBackground(String... params) {
        String urlStr = params[0];
        try {
            URL url = new URL(urlStr);
            String urlPath = url.getPath();
            currentFileName = urlPath.substring(urlPath.lastIndexOf('/') + 1);
            if (!currentFileName.endsWith(".gguf")) {
                currentFileName += ".gguf";
            }
            
            // 关键修改：保存到应用私有目录，不需要权限
            File downloadDir = context.getFilesDir();
            File modelFile = new File(downloadDir, currentFileName);
            
            logger.log("📁 保存路径: " + modelFile.getAbsolutePath());
            
            // 如果文件已存在，先删除
            if (modelFile.exists()) {
                modelFile.delete();
                logger.log("已删除旧文件: " + currentFileName);
            }
            
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.connect();
            
            int totalSize = conn.getContentLength();
            InputStream is = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(modelFile);
            
            byte[] buffer = new byte[8192];
            int len;
            long downloaded = 0;
            
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
                downloaded += len;
                if (totalSize > 0) {
                    final int percent = (int) (downloaded * 100 / totalSize);
                    publishProgress(percent);
                }
            }
            
            fos.close();
            is.close();
            
            modelManager.addModel(modelFile.getAbsolutePath(), currentFileName);
            logger.log("✅ 模型已保存到私有目录");
            return modelFile.getAbsolutePath();
            
        } catch (Exception e) {
            logger.log("❌ 下载异常: " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    @Override
    protected void onProgressUpdate(Integer... values) {
        if (callback != null) {
            callback.onProgress(values[0]);
        }
    }
    
    @Override
    protected void onPostExecute(String result) {
        if (callback != null) {
            if (result.startsWith("ERROR:")) {
                callback.onError(result.substring(6));
            } else {
                callback.onSuccess(result, currentFileName);
            }
        }
    }
}