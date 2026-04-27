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
            debugLogger.log("🔍 当前 useCloud = " + useCloud);
            String prompt = inputEditText.getText().toString().trim();
            if (prompt.isEmpty()) {
                Toast.makeText(this, "请输入提示词", Toast.LENGTH_SHORT).show();
                return;
            }

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
        return "用户：" + userInput + "\n只回答答案：";
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
        // 先记录用户输入
        conversationLogger.logNormal(userInput, "");
        
        // 检查是否是记忆操作
        if (userInput.contains("记一下") || userInput.contains("记住")) {
            // 存储记忆
            // ... 调用 AI 提取 key-value 和分类
            String category = "Security";
            String key = "server_password";
            String value = "abc123";
            
            boolean success = memoryManager.storeMemory(category, key, value, userInput);
            String response = success ? "已记住" : "保存失败";
            conversationLogger.logStore(userInput, response, 
                memoryManager.getCategoryByName(category).getId(), 0);
            // 显示 response
        } 
        else if (userInput.contains("密码") && userInput.contains("是什么")) {
            // 查询记忆
            Memory memory = memoryManager.getMemory("Security", "server_password");
            if (memory != null) {
                // 需要验证安全策略
                Category category = memoryManager.getCategoryById(memory.getCategoryId());
                SecurityPolicy policy = new SecurityPolicy(category.getPolicyType(), 
                    category.getPolicyCombination());
                
                if (SecurityVerifier.verifyCategory(policy)) {
                    String response = "密码是: " + memory.getValueText();
                    conversationLogger.logQuery(userInput, response, category.getId(), memory.getId());
                    // 显示 response
                } else {
                    conversationLogger.logNormal(userInput, "验证失败");
                    // 显示验证失败
                }
            } else {
                // 普通对话
                callCloudAPI(userInput);
            }
        } 
        else {
            // 普通对话
            callCloudAPI(userInput);
        }
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
}