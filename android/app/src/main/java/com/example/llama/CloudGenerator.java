package com.example.llama;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class CloudGenerator implements TextGenerator {
    private final String apiKey;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DebugLogger logger;
    private OkHttpClient client;
    private Call currentCall;

    public CloudGenerator(String apiKey, DebugLogger logger) {
        this.apiKey = apiKey;
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void generate(String prompt, TextGenerator.Callback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("请先输入 DeepSeek API Key");
            return;
        }

        callback.onStart();

        JSONObject body = new JSONObject();
        try {
            body.put("model", "deepseek-chat");
            body.put("stream", true);
            body.put("max_tokens", 512);
            body.put("temperature", 0.7);
            
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);
            body.put("messages", messages);
        } catch (Exception e) {
            callback.onError("构建请求失败: " + e.getMessage());
            return;
        }

        Request request = new Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                .build();

        currentCall = client.newCall(request);
        final StringBuilder fullText = new StringBuilder();

        // 使用 OkHttp 的 Callback，避免命名冲突
        currentCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    mainHandler.post(() -> callback.onError("API 错误: " + response.code()));
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                JSONArray choices = json.getJSONArray("choices");
                                if (choices.length() > 0) {
                                    JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
                                    if (delta.has("content")) {
                                        String content = delta.getString("content");
                                        if (content != null && !content.isEmpty()) {
                                            fullText.append(content);
                                            mainHandler.post(() -> callback.onToken(content));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                logger.log("JSON 解析错误: " + e.getMessage());
                            }
                        }
                    }
                    
                    mainHandler.post(() -> callback.onComplete(fullText.toString()));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                if (!call.isCanceled()) {
                    mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
                }
            }
        });
    }

    public void cancel() {
        if (currentCall != null) {
            currentCall.cancel();
        }
    }
}