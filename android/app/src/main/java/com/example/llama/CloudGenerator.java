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
    private final String apiUrl;
    private final String modelName;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DebugLogger logger;
    private OkHttpClient client;
    private Call currentCall;

    public CloudGenerator(String apiKey, String apiUrl, String modelName, DebugLogger logger) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.modelName = modelName;
        this.logger = logger;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public void generate(String prompt, TextGenerator.Callback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("请先配置 API Key");
            return;
        }

        logger.log("☁️ ===== 开始云端请求 =====");
        logger.log("☁️ API URL: " + apiUrl);
        logger.log("☁️ 模型: " + modelName);
        logger.log("☁️ 提示词: " + (prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt));
        callback.onStart();

        JSONObject body = new JSONObject();
        try {
            body.put("model", modelName);
            body.put("stream", true);
            body.put("max_tokens", 512);
            body.put("temperature", 0.7);
            
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);
            body.put("messages", messages);
            
            logger.log("☁️ 请求体: " + body.toString());
        } catch (Exception e) {
            callback.onError("构建请求失败: " + e.getMessage());
            return;
        }

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
                .build();

        logger.log("☁️ 发送请求到: " + apiUrl);
        
        currentCall = client.newCall(request);
        final StringBuilder fullText = new StringBuilder();
        final long startTime = System.currentTimeMillis();

        currentCall.enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final int statusCode = response.code();
                logger.log("☁️ 收到响应，状态码: " + statusCode);
                
                if (!response.isSuccessful()) {
                    String errorBodyTemp;
                    try {
                        if (response.body() != null) {
                            errorBodyTemp = response.body().string();
                        } else {
                            errorBodyTemp = "无响应体";
                        }
                    } catch (Exception e) {
                        errorBodyTemp = "读取错误: " + e.getMessage();
                    }
                    final String errorBody = errorBodyTemp;
                    logger.log("☁️ 错误响应: " + errorBody);
                    mainHandler.post(() -> callback.onError("API 错误: " + statusCode + " - " + errorBody));
                    return;
                }

                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        mainHandler.post(() -> callback.onError("响应为空"));
                        return;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(body.byteStream()));
                    String line;
                    int chunkCount = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                logger.log("☁️ 收到 [DONE] 标记");
                                break;
                            }
                            try {
                                JSONObject json = new JSONObject(data);
                                
                                // 尝试多种可能的字段路径
                                String content = null;
                                
                                // 方式1: choices[0].delta.content (流式)
                                if (json.has("choices")) {
                                    JSONArray choices = json.getJSONArray("choices");
                                    if (choices.length() > 0) {
                                        JSONObject choice = choices.getJSONObject(0);
                                        if (choice.has("delta")) {
                                            JSONObject delta = choice.getJSONObject("delta");
                                            if (delta.has("content")) {
                                                content = delta.getString("content");
                                            }
                                        }
                                        // 方式2: choices[0].message.content (非流式)
                                        else if (choice.has("message")) {
                                            JSONObject message = choice.getJSONObject("message");
                                            if (message.has("content")) {
                                                content = message.getString("content");
                                            }
                                        }
                                        // 方式3: choices[0].text
                                        else if (choice.has("text")) {
                                            content = choice.getString("text");
                                        }
                                    }
                                }
                                // 方式4: 直接有 content 字段
                                else if (json.has("content")) {
                                    content = json.getString("content");
                                }
                                // 方式5: 硅基流动特殊格式
                                else if (json.has("data")) {
                                    JSONObject dataObj = json.getJSONObject("data");
                                    if (dataObj.has("content")) {
                                        content = dataObj.getString("content");
                                    }
                                }
                                
                                if (content != null && !content.isEmpty()) {
                                    fullText.append(content);
                                    chunkCount++;
                                    if (chunkCount % 5 == 0) {
                                        logger.log("☁️ 已接收 " + chunkCount + " 个数据块，累计 " + fullText.length() + " 字符");
                                    }
                                    final String tokenContent = content;
                                    mainHandler.post(() -> callback.onToken(tokenContent));
                                } else {
                                    // 调试：打印未解析的 JSON
                                    logger.log("☁️ 未解析的 JSON: " + data);
                                }
                            } catch (Exception e) {
                                logger.log("☁️ JSON 解析错误: " + e.getMessage());
                                logger.log("☁️ 原始数据: " + data);
                            }
                        }
                    }
                    
                    long totalTime = System.currentTimeMillis() - startTime;
                    final int finalChunkCount = chunkCount;
                    final int finalLength = fullText.length();
                    final String finalText = fullText.toString();
                    logger.log("☁️ 流式传输完成，共接收 " + finalChunkCount + " 个数据块");
                    logger.log("☁️ 总耗时: " + totalTime + " ms，生成长度: " + finalLength + " 字符");
                    logger.log("☁️ 文本为: " + fullText.toString());
                    mainHandler.post(() -> callback.onComplete(finalText));
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                long elapsed = System.currentTimeMillis() - startTime;
                logger.log("☁️ 网络请求失败，耗时: " + elapsed + " ms");
                logger.log("☁️ 错误详情: " + e.getMessage());
                if (!call.isCanceled()) {
                    mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
                }
            }
        });
    }

    public void cancel() {
        if (currentCall != null) {
            currentCall.cancel();
            logger.log("☁️ 已取消请求");
        }
    }

    // 简单的日志拦截器
    private static class HttpLoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            android.util.Log.d("CloudGenerator", "请求: " + request.method() + " " + request.url());
            Response response = chain.proceed(request);
            android.util.Log.d("CloudGenerator", "响应: " + response.code());
            return response;
        }
    }
}



