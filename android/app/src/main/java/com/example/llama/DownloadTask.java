package com.example.llama;

import android.os.AsyncTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask extends AsyncTask<String, Integer, String> {
    private final DebugLogger logger;
    private final ModelManager modelManager;
    private final Callback callback;
    private String currentFileName;
    
    public interface Callback {
        void onProgress(int percent);
        void onSuccess(String modelPath, String fileName);
        void onError(String error);
    }
    
    public DownloadTask(DebugLogger logger, ModelManager modelManager, Callback callback) {
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
            
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            File modelFile = new File(downloadDir, currentFileName);
            
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
            return modelFile.getAbsolutePath();
            
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    @Override
    protected void onProgressUpdate(Integer... values) {
        callback.onProgress(values[0]);
    }
    
    @Override
    protected void onPostExecute(String result) {
        if (result.startsWith("ERROR:")) {
            callback.onError(result.substring(6));
        } else {
            callback.onSuccess(result, currentFileName);
        }
    }
}