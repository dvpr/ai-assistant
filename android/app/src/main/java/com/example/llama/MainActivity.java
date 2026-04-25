package com.example.llama;  // 请修改为你的实际包名

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

import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Toast;

import android.view.WindowManager;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.widget.FrameLayout;
import android.view.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.example.llama.DebugLogger;
 
public class MainActivity extends Activity implements ModelSelectionFragment.TextGeneratorCallback {

    // 模型下载地址 
    private static final String MODEL_URL = "https://hf-mirror.com/QuantFactory/Qwen2.5-0.5B-GGUF/resolve/main/Qwen2.5-0.5B-Q4_K_M.gguf";
    private static final String MODEL_FILE_NAME = "qwen2.5-0.5b-q4.gguf";

    private DebugLogger debugLogger;
    private TextView debugTextView;

    // 成员变量
    private CloudGenerator cloudGenerator;
    private boolean useCloud = false;

    // 你的 native 方法
    private native boolean loadModel(String modelPath);
    private native float[] getEmbedding(String input);  // 如果有就保留，没有就注释
    public native String generateText(String prompt);  // ← 新增
    private native void cleanup();

    static {
        System.loadLibrary("wrapper");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        // ===== 新增：模型管理区域的容器 =====
        FrameLayout fragmentContainer = new FrameLayout(this);
        fragmentContainer.setId(View.generateViewId());  // 动态生成 ID
        fragmentContainer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(fragmentContainer);

        // 添加 Fragment
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ModelSelectionFragment fragment = new ModelSelectionFragment();
        ft.add(fragmentContainer.getId(), fragment);  // 使用动态 ID
        ft.commit();

        // 加载模型按钮
        Button loadButton = new Button(this);
        loadButton.setText("重新加载模型");
        loadButton.setOnClickListener(v -> checkAndLoadModel());
        layout.addView(loadButton);

        // 输入框（Prompt）
        EditText inputEditText = new EditText(this);
        inputEditText.setHint("输入提示词，例如：你好，请介绍一下你自己");
        layout.addView(inputEditText);

        // 生成按钮
        Button generateButton = new Button(this);
        generateButton.setText("生成文本");
        layout.addView(generateButton);

        // 结果显示区
        TextView resultTextView = new TextView(this);
        resultTextView.setText("生成结果将显示在这里");
        resultTextView.setPadding(16, 16, 16, 16);
        resultTextView.setBackgroundColor(0xFFF0F0F0);
        resultTextView.setTextIsSelectable(true);
        layout.addView(resultTextView);

        // 调试日志区
        ScrollView scrollView = new ScrollView(this);
        debugTextView = new TextView(this);
        debugTextView.setTextSize(12);
        debugTextView.setPadding(16, 16, 16, 16);
        debugTextView.setTextIsSelectable(true);
        scrollView.addView(debugTextView);
        layout.addView(scrollView);

        setContentView(layout);

        // 开启常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 初始化日志
        debugLogger = DebugLogger.getInstance();
        debugLogger.init(debugTextView);

        // 启动 logcat 读取线程（显示 C++ 层的日志）
        startLogcatReader();

        // 设置生成按钮点击事件
        generateButton.setOnClickListener(v -> {
            String prompt = inputEditText.getText().toString();
            if (!prompt.isEmpty()) {
                debugLogger.log("开始生成，提示词: " + prompt);
                resultTextView.setText("生成中，请稍候...");
                
                new Thread(() -> {
                    String result = generateText(prompt);
                    runOnUiThread(() -> {
                        if (result != null && !result.isEmpty()) {
                            resultTextView.setText(result);
                            debugLogger.log("生成完成，长度: " + result.length() + " 字符");
                        } else {
                            resultTextView.setText("生成失败");
                            debugLogger.log("生成失败，返回空结果");
                        }
                    });
                }).start();
            } else {
                Toast.makeText(this, "请输入提示词", Toast.LENGTH_SHORT).show();
            }
        });

        // 自动检查并加载模型
        checkAndLoadModel();
    }

    // 实现接口方法
    @Override
    public void onSwitchToLocal(String modelPath) {
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
    public void onSwitchToCloud(String apiKey) {
        cloudGenerator = new CloudGenerator(apiKey, debugLogger);
        debugLogger.log("已切换到 DeepSeek 云端模型");
        // 设置一个标志，让生成按钮使用 cloudGenerator
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
                        if (now - lastLog > 1000) {
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
}