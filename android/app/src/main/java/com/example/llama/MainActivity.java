package com.example.llama;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.File;

import com.example.llama.memory.*;

public class MainActivity extends Activity implements ModelSelectionFragment.TextGeneratorCallback {

    private static final String MODEL_URL = "https://www.modelscope.cn/models/qwen/Qwen2-0.5B-Instruct-GGUF/resolve/master/qwen2-0_5b-instruct-q4_k_m.gguf";
    private static final String MODEL_FILE_NAME = "qwen2-0_5b-instruct-q4_k_m.gguf";

    private DebugLogger debugLogger;
    private UiManager ui;
    private ModelDownloader modelDownloader;
    private ModelLoaderHelper modelLoader;
    private ConversationProcessor conversationProcessor;
    private ExportHelper exportHelper;
    private NativeBridge nativeBridge;
    private CloudGenerator cloudGenerator;

    private boolean useCloud = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化单例日志
        debugLogger = DebugLogger.getInstance();

        // 创建 NativeBridge
        nativeBridge = new NativeBridge();
        nativeBridge.setDebugLogger(debugLogger);
        nativeBridge.registerStreamCallback();
        NativeBridge.setActivity(this);

        // 构建 UI
        ui = new UiManager(this);
        setContentView(ui.buildLayout(debugLogger));

        // 启动 logcat 读取线程（显示 C++ 日志）
        startLogcatReader();

        // 初始化辅助组件
        modelDownloader = new ModelDownloader(debugLogger);
        modelLoader = new ModelLoaderHelper(debugLogger, nativeBridge);
        exportHelper = new ExportHelper(this, debugLogger);

        // 初始化记忆管理器、对话日志及处理器（cloudGenerator 初始为 null）
        MemoryManager memoryManager = new MemoryManager(this);
        ConversationLogger conversationLogger = new ConversationLogger(this);
        conversationProcessor = new ConversationProcessor(this, debugLogger,
                memoryManager, conversationLogger, cloudGenerator, nativeBridge);

        // 设置 UI 按钮点击事件
        ui.loadButton.setOnClickListener(v -> checkAndLoadModel());
        ui.exportDbButton.setOnClickListener(v -> checkStorageAndExport());
        ui.generateButton.setOnClickListener(v -> {
            String input = ui.getInputText();
            if (input.isEmpty()) {
                Toast.makeText(this, "请输入提示词", Toast.LENGTH_SHORT).show();
                return;
            }
            conversationProcessor.processInput(input, ui);
        });

        // 添加模型管理 Fragment
        FragmentManager fm = getFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        ModelSelectionFragment fragment = new ModelSelectionFragment();
        ft.add(ui.fragmentContainer.getId(), fragment);
        ft.commit();

        // 启动时自动检查模型
        checkAndLoadModel();

        // 保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ==================== 模型管理 ====================
    private void checkAndLoadModel() {
        File modelFile = new File(getFilesDir(), MODEL_FILE_NAME);
        if (modelFile.exists()) {
            debugLogger.log("模型已存在: " + modelFile.getAbsolutePath() + "，大小: " + (modelFile.length() / 1024 / 1024) + " MB");
            modelLoader.loadModel(modelFile, new ModelLoaderHelper.LoadCallback() {
                @Override public void onSuccess() { debugLogger.log("模型加载成功"); }
                @Override public void onError(String error) { debugLogger.log("模型加载失败: " + error); }
            });
        } else {
            debugLogger.log("模型不存在，开始下载...");
            modelDownloader.download(MODEL_URL, modelFile, new ModelDownloader.DownloadCallback() {
                @Override public void onProgress(int percent) { debugLogger.log("下载进度: " + percent + "%"); }
                @Override public void onSuccess(File file) {
                    debugLogger.log("模型下载完成，开始加载");
                    modelLoader.loadModel(file, new ModelLoaderHelper.LoadCallback() {
                        @Override public void onSuccess() { debugLogger.log("模型加载成功"); }
                        @Override public void onError(String error) { debugLogger.log("模型加载失败: " + error); }
                    });
                }
                @Override public void onError(String error) { debugLogger.log("下载失败: " + error); }
            });
        }
    }

    // ==================== 模型切换回调（来自 ModelSelectionFragment） ====================
    @Override
    public void onSwitchToLocal(String modelPath) {
        useCloud = false;
        conversationProcessor.setUseCloud(false);
        File modelFile = new File(modelPath);
        modelLoader.switchToLocalModel(modelFile, new ModelLoaderHelper.LoadCallback() {
            @Override public void onSuccess() { debugLogger.log("已切换到本地模型: " + modelPath); }
            @Override public void onError(String error) { debugLogger.log("切换失败: " + error); }
        });
    }

    @Override
    public void onSwitchToCloud(String apiKey, String apiUrl, String modelName) {
        useCloud = true;
        conversationProcessor.setUseCloud(true);
        if (cloudGenerator == null) {
            cloudGenerator = new CloudGenerator(apiKey, apiUrl, modelName, debugLogger);
        } else {
            cloudGenerator.updateConfig(apiKey, apiUrl, modelName); // 需在 CloudGenerator 中添加该方法
        }
        // 更新处理器中的 cloudGenerator 引用
        conversationProcessor.setCloudGenerator(cloudGenerator); // 需在 ConversationProcessor 中添加 setter
        debugLogger.log("✅ 已切换到云端模型: " + modelName);
    }

    // 实际更新 UI 的方法
    public void onStreamChunk(String chunk) {
        runOnUiThread(() -> {
            // 更新 resultTextView
        });
    }

    public void onStreamFinish() {
        runOnUiThread(() -> {
            debugLogger.log("✅ 本地流式生成完成");
        });
    }

    // ==================== 导出数据库相关 ====================
    private void checkStorageAndExport() {
        SecurityHelper.requestStoragePermission(this, new SecurityHelper.PermissionCallback() {
            @Override public void onGranted() {
                // 权限授予后，启动指纹验证
                BiometricHelper.authenticate(MainActivity.this, new BiometricHelper.Callback() {
                    @Override public void onSuccess() { exportHelper.exportDatabase(new ExportHelper.ExportCallback() {
                        @Override public void onSuccess(File file) {
                            debugLogger.log("导出成功: " + file.getAbsolutePath());
                            // 询问是否分享
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle("导出成功")
                                .setMessage("文件已保存到 Download 目录，是否分享？")
                                .setPositiveButton("分享", (d, w) -> exportHelper.shareFile(file))
                                .setNegativeButton("取消", null)
                                .show();
                        }
                        @Override public void onError(String error) { debugLogger.log("导出失败: " + error); }
                    }); }
                    @Override public void onError(String error) { debugLogger.log("指纹验证失败: " + error); }
                });
            }
            @Override public void onDenied() { Toast.makeText(MainActivity.this, "需要存储权限才能导出", Toast.LENGTH_SHORT).show(); }
        });
    }

    // ==================== 调试日志 ====================
    private void startLogcatReader() {
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec("logcat -s LlamaNative:D LlamaNative:E");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    final String logLine = line;
                    runOnUiThread(() -> {
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

    // 权限请求结果处理（转发给 SecurityHelper）
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        SecurityHelper.onRequestPermissionsResult(requestCode, grantResults, null);
        // 可根据需要再处理其他权限
    }
}