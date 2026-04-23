package com.example.llama;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    
    static {
        System.loadLibrary("wrapper");
    }
    
    private EditText editTextText;
    private Button button;
    private TextView textView;
    private TextView textView2;
    private boolean modelLoaded = false;
    
    // Native 方法声明
    private native boolean loadModel(String modelPath);
    private native float[] getEmbedding(String input);
    private native void cleanup();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化视图
        editTextText = findViewById(R.id.editTextText);
        button = findViewById(R.id.button);
        textView = findViewById(R.id.textView);
        textView2 = findViewById(R.id.textView2);
        
        // 设置按钮点击事件
        button.setOnClickListener(v -> processInput());
        button.setEnabled(false);
        
        // 初始化模型
        initializeModel();
    }
    
    private void initializeModel() {
        textView2.setText("正在准备模型文件...");
        
        // 在后台线程加载模型
        new Thread(() -> {
            try {
                // 复制模型文件到内部存储
                String modelPath = copyModelToInternalStorage();
                if (modelPath != null) {
                    // 加载模型
                    boolean success = loadModel(modelPath);
                    runOnUiThread(() -> {
                        if (success) {
                            modelLoaded = true;
                            button.setEnabled(true);
                            textView2.setText("模型加载成功！");
                            Toast.makeText(MainActivity.this, "模型加载成功", Toast.LENGTH_SHORT).show();
                        } else {
                            textView2.setText("模型加载失败");
                            Toast.makeText(MainActivity.this, "模型加载失败", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        textView2.setText("模型文件准备失败");
                        Toast.makeText(MainActivity.this, "无法复制模型文件", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textView2.setText("初始化失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "初始化失败", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private String copyModelToInternalStorage() throws IOException {
        String modelFileName = "gte-small-q8_0.gguf";
        File modelFile = new File(getFilesDir(), modelFileName);
        
        // 如果文件已存在，直接返回路径
        if (modelFile.exists()) {
            runOnUiThread(() -> textView2.setText("模型文件已存在，正在加载..."));
            return modelFile.getAbsolutePath();
        }
        
        runOnUiThread(() -> textView2.setText("正在复制模型文件..."));
        
        // 从 assets 复制模型文件
        try (InputStream inputStream = getAssets().open(modelFileName);
             FileOutputStream outputStream = new FileOutputStream(modelFile)) {
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            
            return modelFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void processInput() {
        String input = editTextText.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "请输入文本", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!modelLoaded) {
            Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show();
            return;
        }
        
        textView2.setText("正在处理...");
        button.setEnabled(false);
        
        // 在后台线程处理推理
        new Thread(() -> {
            try {
                float[] embedding = getEmbedding(input);
                runOnUiThread(() -> {
                    if (embedding != null) {
                        displayResult(embedding);
                        textView2.setText("处理完成");
                    } else {
                        textView.setText("获取 Embedding 失败");
                        textView2.setText("处理失败");
                    }
                    button.setEnabled(true);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    textView.setText("错误: " + e.getMessage());
                    textView2.setText("处理出错");
                    button.setEnabled(true);
                });
            }
        }).start();
    }
    
    private void displayResult(float[] embedding) {
        StringBuilder result = new StringBuilder();
        result.append("Embedding 维度: ").append(embedding.length).append("\n\n");
        result.append("前10个值:\n");
        
        for (int i = 0; i < Math.min(10, embedding.length); i++) {
            result.append(String.format("%.6f", embedding[i]));
            if (i < 9) {
                result.append(", ");
            }
            if (i == 4) {
                result.append("\n");
            }
        }
        
        result.append("\n\n... (共 ").append(embedding.length).append(" 个维度)");
        textView.setText(result.toString());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放模型资源
        try {
            cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}