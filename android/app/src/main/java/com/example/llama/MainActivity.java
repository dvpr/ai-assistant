package com.example.llama;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.widget.FrameLayout;
import android.view.View;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import com.example.llama.DebugLogger;
import com.example.llama.memory.*;
import com.example.llama.memory.models.*;

public class MainActivity extends Activity implements ModelSelectionFragment.TextGeneratorCallback {

    private static final String MODEL_URL = "https://www.modelscope.cn/models/qwen/Qwen2-1.5B-Instruct-GGUF/resolve/master/qwen2-1_5b-instruct-q4_k_m.gguf";
    private static final String MODEL_FILE_NAME = "qwen2-1_5b-instruct-q4_k_m.gguf";

    private DebugLogger debugLogger;
    private TextView debugTextView;
    private TextView resultTextView;        // 成员变量，将在onCreate中初始化
    private EditText inputEditText;         // 也作为成员变量，方便其他地方使用

    private MemoryManager memoryManager;
    private ConversationLogger conversationLogger;

    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private CloudGenerator cloudGenerator;
    private boolean useCloud = false;

    // Native methods
    private native boolean loadModel(String modelPath);
    private native float[] getEmbedding(String input);
    public native String generateText(String prompt);
    private native void registerStreamCallback();
    private native void cleanup();
    private native void setDebugLogger(DebugLogger logger);

    static {
        System.loadLibrary("wrapper");
    }

    public interface ParseCallback {
        void onSuccess(String key, String value, String category);
        void onFailure(String error);
    }

    public interface IntentCallback {
        void onStore(String key, String value, String category);
        void onQuery(String keywords);
        void onChat();
        void onError(String error);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SecurityVerifier.init(getApplicationContext());
        memoryManager = new MemoryManager(this);
        conversationLogger = new ConversationLogger(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // ---- 模型管理 Fragment 容器 ----
        FrameLayout fragmentContainer = new FrameLayout(this);
        fragmentContainer.setId(View.generateViewId());
        fragmentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fragmentContainer);

        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ModelSelectionFragment fragment = new ModelSelectionFragment();
        ft.add(fragmentContainer.getId(), fragment);
        ft.commit();

        // ---- 重新加载模型按钮 ----
        Button loadButton = new Button(this);
        loadButton.setText("重新加载模型");
        loadButton.setOnClickListener(v -> checkAndLoadModel());
        layout.addView(loadButton);

        // ---- 输入框 ----
        inputEditText = new EditText(this);
        inputEditText.setHint("输入提示词，例如：你好，请介绍一下你自己");
        inputEditText.setText("1.1.1.1的密码是什么");
        layout.addView(inputEditText);

        // ---- 生成按钮 ----
        Button generateButton = new Button(this);
        generateButton.setText("生成文本");
        layout.addView(generateButton);

        // ---- 结果显示区（带滚动）----
        // 重要：先实例化 resultTextView
        resultTextView = new TextView(this);
        resultTextView.setText("生成结果将显示在这里");
        resultTextView.setTextIsSelectable(true);
        resultTextView.setTextSize(14);

        ScrollView resultScrollView = new ScrollView(this);
        float density = getResources().getDisplayMetrics().density;
        resultScrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (150 * density)));
        resultScrollView.setPadding(16, 16, 16, 16);
        resultScrollView.setBackgroundColor(0xFFF0F0F0);
        resultScrollView.addView(resultTextView);
        layout.addView(resultScrollView);

        // ---- 调试日志区 ----
        ScrollView scrollView = new ScrollView(this);
        debugTextView = new TextView(this);
        debugTextView.setTextSize(12);
        debugTextView.setPadding(16, 16, 16, 16);
        debugTextView.setTextIsSelectable(true);
        scrollView.addView(debugTextView);
        layout.addView(scrollView);

        setContentView(layout);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        debugLogger = DebugLogger.getInstance();
        debugLogger.init(debugTextView);
        startLogcatReader();
        setDebugLogger(debugLogger);

        // 注册流式回调（必须在 generateText 调用前）
        registerStreamCallback();

        // 生成按钮逻辑（修正版）
        generateButton.setOnClickListener(v -> {
            String prompt = inputEditText.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(this, "请输入提示词", Toast.LENGTH_SHORT).show();
                return;
            }

            processUserInput(prompt);
        });

        checkAndLoadModel();
    }

    // ==================== 流式回调（供 C++ 调用） ====================
    public void onStreamChunk(String chunk) {
        runOnUiThread(() -> {
            if (resultTextView == null) return;
            String current = resultTextView.getText().toString();
            if (current.equals("📱 本地生成中...")) {
                resultTextView.setText(chunk);
            } else {
                resultTextView.setText(current + chunk);
            }
        });
    }

    public void onStreamFinish() {
        runOnUiThread(() -> {
            if (debugLogger != null) {
                debugLogger.log("✅ 本地流式生成完成");
            }
        });
    }

    // ==================== 模型切换回调 ====================
    @Override
    public void onSwitchToLocal(String modelPath) {
        useCloud = false;
        
        // 关键：先释放旧模型资源
        cleanup();

        // 重新加载本地模型
        new Thread(() -> {
            boolean success = loadModel(modelPath);
            runOnUiThread(() -> {
                if (success) {
                    debugLogger.log("已切换到本地模型: " + modelPath);
                } else {
                    debugLogger.log("切换失败，模型加载错误");
                }
            });
        }).start();
    }

    @Override
    public void onSwitchToCloud(String apiKey, String apiUrl, String modelName) {
        debugLogger.log("☁️ 配置云端: " + modelName);
        debugLogger.log("☁️ API URL: " + apiUrl);
        debugLogger.log("☁️ API Key 长度: " + (apiKey != null ? apiKey.length() : 0));
        useCloud = true;
        cloudGenerator = new CloudGenerator(apiKey, apiUrl, modelName, debugLogger);
        debugLogger.log("✅ 已切换到云端模型: " + modelName);
    } 

    private void checkAndLoadModel() {
        File modelFile = new File(getFilesDir(), MODEL_FILE_NAME);
        if (modelFile.exists()) {
            debugLogger.log("模型已存在: " + modelFile.getAbsolutePath());
            debugLogger.log("文件大小: " + (modelFile.length() / 1024 / 1024) + " MB");
            loadModelFromFile(modelFile);
        } else {
            debugLogger.log("模型不存在，开始从网络下载...");
            downloadModelInBackground();
        }
    }

    private void downloadModelInBackground() {
        new Thread(() -> {
            try {
                URL url = new URL(MODEL_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent", "Android-App");
                connection.connect();

                int contentLength = connection.getContentLength();
                if (contentLength <= 0) {
                    runOnUiThread(() -> debugLogger.log("错误：无法获取文件大小，URL 可能无效"));
                    return;
                }
                debugLogger.log("开始下载，总大小: " + (contentLength / 1024 / 1024) + " MB");

                File modelFile = new File(getFilesDir(), MODEL_FILE_NAME);
                try (InputStream is = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(modelFile)) {

                    byte[] buffer = new byte[8192];
                    int len;
                    long downloaded = 0;
                    long lastLog = System.currentTimeMillis();

                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                        downloaded += len;
                        long now = System.currentTimeMillis();
                        if (now - lastLog > 5000) {  // 5秒输出一次
                            final int percent = (int) (downloaded * 100 / contentLength);
                            runOnUiThread(() -> debugLogger.log("下载进度: " + percent + "%"));
                            lastLog = now;
                        }
                    }
                }

                runOnUiThread(() -> {
                    debugLogger.log("模型下载完成！");
                    loadModelFromFile(modelFile);
                });

            } catch (Exception e) {
                runOnUiThread(() -> debugLogger.log("下载失败: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    private void loadModelFromFile(File modelFile) {
        String path = modelFile.getAbsolutePath();
        debugLogger.log("开始加载模型: " + path);
        boolean success = loadModel(path);
        debugLogger.log("loadModel 返回: " + success);
        if (success) {
            debugLogger.log("✅ 模型加载成功！可以开始使用了。");
        } else {
            debugLogger.log("❌ 模型加载失败！请确认模型与 llama.cpp 兼容。");
        }
    }

    private String formatQwenPrompt(String userInput) {
        // return "<|im_start|>system\n你是一个简洁的助手。回答要直接、精炼，不要废话，不要展开无关内容。<|im_end|>\n"
        //      + "<|im_start|>user\n" + userInput + "\n<|im_end|>\n"
        //      + "<|im_start|>assistant\n";
        // return "用户：" + userInput + "\n只回答答案：";
        return userInput;
    }

    private void startLogcatReader() {
        new Thread(() -> {
            try {
                // 只读取 LlamaNative 标签的日志（ERROR 级别及以上）
                Process process = Runtime.getRuntime().exec("logcat -s LlamaNative:D LlamaNative:E");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String logLine = line;
                    runOnUiThread(() -> {
                        // 过滤掉重复或空行
                        if (logLine != null && !logLine.trim().isEmpty()) {
                            debugLogger.log("[C++] " + logLine);
                        }
                    });
                }
            } catch (IOException e) {
                debugLogger.log("logcat 读取失败: " + e.getMessage());
            }
        }).start();
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_STORAGE_PERMISSION);
            } else {
                // 已有权限
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                debugLogger.log("✅ 存储权限已授予");
            } else {
                debugLogger.log("❌ 存储权限被拒绝，无法下载模型");
            }
        }
    }

    // 处理用户输入
    private void processUserInput(String userInput) {
        conversationLogger.logNormal(userInput, "");
        parseIntent(userInput, new IntentCallback() {
            @Override
            public void onStore(String key, String value, String category) {
                // 存储记忆
                boolean success = memoryManager.storeMemory(category, key, value, userInput);
                runOnUiThread(() -> {
                    String msg = success ? "✅ 已记住" : "❌ 保存失败";
                    resultTextView.setText(msg);
                    debugLogger.log(msg);
                });
                conversationLogger.logStore(userInput, success ? "已记住" : "失败",
                        memoryManager.getCategoryByName(category) != null ? memoryManager.getCategoryByName(category).getId() : 0, 0);
            }
            
            @Override
            public void onQuery(String keywords) {
                // 查询记忆（复用已有的查询逻辑）
                performQuery(keywords);
            }
            
            @Override
            public void onChat() {
                // 普通对话（调用本地或云端模型）
                performNormalChat(userInput);
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> resultTextView.setText("处理失败: " + error));
                debugLogger.log("意图解析错误: " + error);
                // 降级为普通对话
                performNormalChat(userInput);
            }
        });
    }

    private String extractJsonFromAIResponse(String response) {
        // 移除可能的 markdown 代码块标记
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        // 找到第一个 { 和最后一个 } 
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private String extractKeyValueByRegex(String input) {
        // 例如：匹配 "记一下(.+?)的密码是(.+?)"
        Pattern p = Pattern.compile("记一下(.+?)的密码是(.+?)");
        Matcher m = p.matcher(input);
        if (m.find()) {
            String key = "server_" + m.group(1).trim() + "_password";
            String value = m.group(2).trim();
            return "{\"key\":\"" + key + "\", \"value\":\"" + value + "\", \"category\":\"Security\"}";
        }
        return null;
    }

    private String callAIForParsing(String userInput) {
        String prompt = buildParsingPrompt(userInput);
        // 使用 cloudGenerator 同步调用（需改造为返回完整字符串）
        final StringBuilder result = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);
        cloudGenerator.generate(prompt, new TextGenerator.Callback() {
            @Override
            public void onToken(String token) { 
                /* 不处理流式，仅在 onComplete 获取结果 */
                debugLogger.log("Token：" + token);
            }
            @Override
            public void onComplete(String fullText) {
                debugLogger.log("FullText：" + fullText);
                result.append(fullText);
                latch.countDown();
            }
            @Override public void onStart() {}
            @Override public void onError(String error) { latch.countDown(); }
        });
        try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {}
        debugLogger.log("Result：" + result.toString());
        return result.toString();
    }

    private String extractByRegex(String input) {
        // 示例：匹配 "记一下 XXX 的密码是 YYY"
        Pattern p = Pattern.compile("记一下(.+?)的密码是(.+?)");
        Matcher m = p.matcher(input);
        if (m.find()) {
            String key = "server_" + m.group(1).trim().replaceAll("\\s+", "_") + "_password";
            String value = m.group(2).trim();
            return "{\"key\":\"" + key + "\", \"value\":\"" + value + "\", \"category\":\"Security\"}";
        }
        // 可添加更多匹配规则
        return null;
    }

    private String buildParsingPrompt(String userInput) {
        return "你是一个信息提取助手。从用户输入中提取出“记忆的关键词”和“要记住的值”，以及推荐分类（从以下选择：Security, Personal, Work, Health, Finance）。输出必须为 JSON 格式，字段：key, value, category。\n\n" +
               "示例1：\n用户：帮我记一下 1.1.1.1 的密码是 admin123\n输出：{\"key\": \"server_1.1.1.1_password\", \"value\": \"admin123\", \"category\": \"Security\"}\n\n" +
               "示例2：\n用户：记住我的生日是1990年1月1日\n输出：{\"key\": \"my_birthday\", \"value\": \"1990-01-01\", \"category\": \"Personal\"}\n\n" +
               "返回完整的json结构，不要包含```json这类多余的字符" +
               "现在请处理：\n用户：" + userInput;
    }

    private void callCloudAPI(String userInput) {
        // 调用云端 API
        // 收到响应后记录
        // conversationLogger.logNormal(userInput, aiResponse);
    }
    
    // 查看历史记录
    private void viewHistory() {
        List<ConversationLog> history = conversationLogger.getHistory();
        if (history != null) {
            // 显示历史记录
        } else {
            // 验证失败
        }
    }

    private void parseMemoryFromAI(String userInput, ParseCallback callback) {
        String prompt = buildParsingPrompt(userInput);  // 复用你已有的构建 prompt 方法
        
        if (cloudGenerator == null) {
            callback.onFailure("云端未配置，无法解析");
            return;
        }
        
        cloudGenerator.generate(prompt, new TextGenerator.Callback() {
            @Override public void onStart() { }
            
            @Override
            public void onToken(String token) { /* 可选：显示进度 */ }
            
            @Override
            public void onComplete(String fullText) {
                // 解析 JSON
                String jsonStr = extractJsonFromAIResponse(fullText);
                try {
                    JSONObject obj = new JSONObject(jsonStr);
                    String key = obj.getString("key");
                    String value = obj.getString("value");
                    String category = obj.getString("category");
                    callback.onSuccess(key, value, category);
                } catch (JSONException e) {
                    callback.onFailure("JSON 解析失败: " + e.getMessage());
                }
            }
            
            @Override
            public void onError(String error) {
                callback.onFailure("AI 解析错误: " + error);
            }
        });
    }

    private void storeFromJson(String jsonStr, String userInput) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String key = obj.getString("key");
            String value = obj.getString("value");
            String category = obj.getString("category");
            boolean success = memoryManager.storeMemory(category, key, value, userInput);
            resultTextView.setText(success ? "✅ 已记住" : "❌ 保存失败");
            conversationLogger.logStore(userInput, success ? "已记住" : "失败",
                    memoryManager.getCategoryByName(category) != null ? memoryManager.getCategoryByName(category).getId() : 0, 0);
        } catch (JSONException e) {
            resultTextView.setText("解析失败");
        }
    }

    private String buildQueryPrompt(String userInput) {
            return "你是一个信息查询助手。从用户输入中提取出“要查询的类别(category)”和“要查询的关键词描述(keywords)”。类别从 [Security, Personal, Work, Health, Finance] 中选择，如果无法确定则留空。输出 JSON 格式：{\"category\": \"...\", \"keywords\": \"...\"}\n\n" +
               "示例1：\n用户：1.1.1.1的密码是什么\n输出：{\"category\": \"Security\", \"keywords\": \"1.1.1.1 password\"}\n\n" +
               "示例2：\n用户：妈妈的生日是哪天\n输出：{\"category\": \"Personal\", \"keywords\": \"mother birthday\"}\n\n" +
               "示例3：\n用户：明天的计划是什么\n输出：{\"category\": \"Work\", \"keywords\": \"tomorrow plan\"}\n\n" +
               "示例4：\n用户：阿司匹林怎么吃\n输出：{\"category\": \"Health\", \"keywords\": \"aspirin dosage\"}\n\n" +
               "现在请处理：\n用户：" + userInput;
    }

    private void parseQueryIntent(String userInput, ParseQueryCallback callback) {
        // 快速正则提取 IP
        String ip = extractIP(userInput);
        if (ip != null) {
            callback.onSuccess(ip, "password"); // 默认查询密码，可后续扩展
            return;
        }
        
        // 调用云端 AI
        if (cloudGenerator == null) {
            callback.onFailure("云端未配置，无法解析查询");
            return;
        }
        
        String prompt = buildQueryPrompt(userInput);
        cloudGenerator.generate(prompt, new TextGenerator.Callback() {
            @Override public void onStart() {}
            @Override public void onToken(String token) {}
            @Override
            public void onComplete(String fullText) {
                String jsonStr = extractJsonFromAIResponse(fullText);
                try {
                    JSONObject obj = new JSONObject(jsonStr);
                    String target = obj.getString("target");
                    String attribute = obj.optString("attribute", "password");
                    callback.onSuccess(target, attribute);
                } catch (JSONException e) {
                    callback.onFailure("解析查询失败: " + e.getMessage());
                }
            }
            @Override
            public void onError(String error) {
                callback.onFailure("AI 查询错误: " + error);
            }
        });
    }

    // 定义回调
    public interface ParseQueryCallback {
        void onSuccess(String target, String attribute);
        void onFailure(String error);
    }

    // 简单正则提取 IP
    private String extractIP(String input) {
        Pattern pattern = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String buildIntentParsingPrompt(String userInput) {
        return "你是一个智能助手，需要判断用户的意图，并提取相关信息。意图分为三种：\n" +
               "1. STORE：用户想要记住、存储某些信息（如密码、生日、计划）。\n" +
               "2. QUERY：用户想要查询之前记住的信息（如密码是多少、生日是哪天）。\n" +
               "3. CHAT：普通对话，不需要存储或查询记忆。\n\n" +
               "输出必须为 JSON 格式，字段：intent, key, value, category。\n" +
               "- 当 intent 为 STORE 时，需提取 key（关键词）、value（要记住的值）、category（从 Security, Personal, Work, Health, Finance 中选择）。\n" +
               "- 当 intent 为 QUERY 时，需提取 keywords（查询的关键词描述，如“1.1.1.1的密码”或“妈妈的生日”）。\n" +
               "- 当 intent 为 CHAT 时，无需其他字段。\n\n" +
               "示例1：\n输入：帮我记一下 1.1.1.1 的密码是 admin123\n输出：{\"intent\": \"STORE\", \"key\": \"server_1.1.1.1_password\", \"value\": \"admin123\", \"category\": \"Security\"}\n\n" +
               "示例2：\n输入：妈妈的生日是什么时候\n输出：{\"intent\": \"QUERY\", \"keywords\": \"妈妈的生日\"}\n\n" +
               "示例3：\n输入：今天天气真好\n输出：{\"intent\": \"CHAT\"}\n\n" +
               "现在请处理：\n输入：" + userInput;
    }

    private void parseIntent(String userInput, IntentCallback callback) {
        // 优先使用云端模型（更智能）
        if (cloudGenerator != null) {
            String prompt = buildIntentParsingPrompt(userInput);
            cloudGenerator.generate(prompt, new TextGenerator.Callback() {
                @Override public void onStart() {}
                @Override public void onToken(String token) {}
                @Override
                public void onComplete(String fullText) {
                    String jsonStr = extractJsonFromAIResponse(fullText);
                    try {
                        JSONObject obj = new JSONObject(jsonStr);
                        String intent = obj.getString("intent");
                        if ("STORE".equals(intent)) {
                            String key = obj.getString("key");
                            String value = obj.getString("value");
                            String category = obj.getString("category");
                            callback.onStore(key, value, category);
                        } else if ("QUERY".equals(intent)) {
                            String keywords = obj.getString("keywords");
                            callback.onQuery(keywords);
                        } else {
                            callback.onChat();
                        }
                    } catch (JSONException e) {
                        // 降级：如果 AI 返回格式错误，走普通对话
                        callback.onChat();
                    }
                }
                @Override public void onError(String error) {
                    // 降级：如果云端调用失败，使用本地关键词匹配（保留原有的硬编码逻辑）
                    fallbackIntentParsing(userInput, callback);
                }
            });
        } else {
            // 没有云端，使用本地关键词匹配降级
            fallbackIntentParsing(userInput, callback);
        }
    }

    private void fallbackIntentParsing(String userInput, IntentCallback callback) {
        if (userInput.contains("记一下") || userInput.contains("记住")) {
            // 简单正则提取（仅用于降级）
            String ip = extractIP(userInput);
            if (ip != null && userInput.contains("密码")) {
                callback.onStore("server_" + ip + "_password", "临时占位", "Security");
                return;
            }
            // 其他降级处理...
            callback.onChat();
        } else if (userInput.contains("是什么") || userInput.contains("多少")) {
            callback.onQuery(userInput);
        } else {
            callback.onChat();
        }
    }

    private void performQuery(String keywords) {
        // 复用之前查询逻辑，根据 keywords 在数据库中搜索
        resultTextView.setText("🔍 正在查询...");
        // 示例：在 Security 分类中按关键字搜索（你可以扩展为全局搜索）
        List<Memory> results = new ArrayList<>();
        for (Memory m : memoryManager.getAllMemories()) {
            if (m.getRawInput().contains(keywords) || m.getKeyText().contains(keywords)) {
                results.add(m);
            }
        }
        if (results.isEmpty()) {
            runOnUiThread(() -> resultTextView.setText("未找到相关信息"));
            return;
        }
        Memory memory = results.get(0);
        Category category = memoryManager.getCategoryById(memory.getCategoryId());
        // 安全验证...
        BiometricHelper.authenticate(this, new BiometricHelper.Callback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> resultTextView.setText(category.getDisplayName() + "：" + memory.getValueText()));
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> resultTextView.setText("验证失败: " + error));
            }
        });
    }

    private void performNormalChat(String userInput) {
        debugLogger.log("🔍 当前 useCloud = " + useCloud);

        String prompt = userInput;
        

        // 清空结果区域，准备显示新内容
        resultTextView.setText("");

        if (useCloud && cloudGenerator != null) {
            // 云端模式（保持不变）
            debugLogger.log("☁️ 调用云端 DeepSeek API");
            resultTextView.setText("☁️ 云端思考中...");
            cloudGenerator.generate(prompt, new TextGenerator.Callback() {
                @Override public void onStart() { debugLogger.log("云端请求已发送"); }
                @Override public void onToken(String token) {
                    runOnUiThread(() -> {
                        String current = resultTextView.getText().toString();
                        if (current.equals("☁️ 云端思考中...")) {
                            resultTextView.setText(token);
                        } else {
                            resultTextView.setText(current + token);
                        }
                    });
                }
                @Override public void onComplete(String fullText) {
                    runOnUiThread(() -> debugLogger.log("✅ 云端生成完成，长度: " + fullText.length()));
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        debugLogger.log("❌ 云端错误: " + error);
                        resultTextView.setText("云端错误: " + error);
                    });
                }
            });
        } else {
            // 本地模式：使用流式输出
            debugLogger.log("📱 调用本地模型（流式）");
            resultTextView.setText("📱 本地生成中...");
            // new Thread(() -> generateText(prompt)).start();  // 不再等待返回值，文本通过 onStreamChunk 回调显示
            String formattedPrompt = formatQwenPrompt(prompt);
            new Thread(() -> generateText(formattedPrompt)).start();  // 使用包装后的 prompt
        }
    }
}