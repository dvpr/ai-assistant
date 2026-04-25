package com.example.llama;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import java.io.File;
import java.util.List;

public class ModelSelectionFragment extends Fragment {
    private DebugLogger logger;
    private ModelManager modelManager;
    private TextGeneratorCallback textGeneratorCallback;
    private LinearLayout modelListContainer;
    
    public interface TextGeneratorCallback {
        void onSwitchToLocal(String modelPath);
        void onSwitchToCloud(String apiKey, String apiUrl, String modelName);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger = DebugLogger.getInstance();
        modelManager = new ModelManager(getActivity());
        if (getActivity() instanceof TextGeneratorCallback) {
            textGeneratorCallback = (TextGeneratorCallback) getActivity();
        }
    }
    
    @Override
    public View onCreateView(android.view.LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        
        // 按钮行
        LinearLayout buttonRow = new LinearLayout(getActivity());
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        
        Button localBtn = new Button(getActivity());
        localBtn.setText("本地模型");
        localBtn.setOnClickListener(v -> showLocalModelDialog());
        buttonRow.addView(localBtn);
        
        Button cloudBtn = new Button(getActivity());
        cloudBtn.setText("云端DeepSeek");
        cloudBtn.setOnClickListener(v -> showCloudDialog());
        buttonRow.addView(cloudBtn);
        
        root.addView(buttonRow);
        
        // 模型列表标题
        TextView title = new TextView(getActivity());
        title.setText("已下载模型：");
        root.addView(title);
        
        // 模型列表
        modelListContainer = new LinearLayout(getActivity());
        modelListContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(modelListContainer);
        
        // 下载区域
        TextView downloadTitle = new TextView(getActivity());
        downloadTitle.setText("下载新模型：");
        root.addView(downloadTitle);
        
        LinearLayout downloadRow = new LinearLayout(getActivity());
        downloadRow.setOrientation(LinearLayout.HORIZONTAL);
        
        EditText urlInput = new EditText(getActivity());
        urlInput.setHint("粘贴下载链接");
        urlInput.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3));
        downloadRow.addView(urlInput);
        
        Button downloadBtn = new Button(getActivity());
        downloadBtn.setText("下载");
        downloadBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) startDownload(url);
        });
        downloadRow.addView(downloadBtn);
        
        root.addView(downloadRow);
        
        refreshModelList();
        return root;
    }
    
    private void refreshModelList() {
        modelListContainer.removeAllViews();
        List<ModelManager.ModelInfo> models = modelManager.getAllModels();
        String currentPath = modelManager.getCurrentModel();
        
        for (ModelManager.ModelInfo model : models) {
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            
            String name = model.name;
            if (model.path.equals(currentPath)) {
                name = "✓ " + name;
            }
            
            TextView nameView = new TextView(getActivity());
            nameView.setText(name);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
            
            Button useBtn = new Button(getActivity());
            useBtn.setText("使用");
            useBtn.setOnClickListener(v -> {
                modelManager.setCurrentModel(model.path);
                refreshModelList();
                if (textGeneratorCallback != null) {
                    textGeneratorCallback.onSwitchToLocal(model.path);
                }
                logger.log("使用模型: " + model.name);
            });
            
            row.addView(nameView);
            row.addView(useBtn);
            modelListContainer.addView(row);
        }
        
        if (models.isEmpty()) {
            TextView empty = new TextView(getActivity());
            empty.setText("暂无模型");
            modelListContainer.addView(empty);
        }
    }
    
    private void showLocalModelDialog() {
        List<ModelManager.ModelInfo> models = modelManager.getAllModels();
        if (models.isEmpty()) {
            new AlertDialog.Builder(getActivity())
                .setTitle("提示")
                .setMessage("没有已下载的模型")
                .setPositiveButton("确定", null)
                .show();
            return;
        }
        
        String[] names = new String[models.size()];
        for (int i = 0; i < models.size(); i++) {
            names[i] = models.get(i).name;
        }
        
        new AlertDialog.Builder(getActivity())
            .setTitle("选择模型")
            .setItems(names, (dialog, which) -> {
                ModelManager.ModelInfo selected = models.get(which);
                modelManager.setCurrentModel(selected.path);
                refreshModelList();
                if (textGeneratorCallback != null) {
                    textGeneratorCallback.onSwitchToLocal(selected.path);
                }
            })
            .show();
    }
    
    private void showCloudDialog() {
        // 创建带多个输入框的对话框
        final LinearLayout dialogLayout = new LinearLayout(getActivity());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 32, 48, 16);
        
        // API URL 输入框
        final TextView urlLabel = new TextView(getActivity());
        urlLabel.setText("API URL:");
        urlLabel.setTextSize(12);
        urlLabel.setTextColor(0xFF666666);
        dialogLayout.addView(urlLabel);
        
        final EditText urlInput = new EditText(getActivity());
        urlInput.setHint("https://open.bigmodel.cn/api/paas/v4/chat/completions");
        urlInput.setText("https://open.bigmodel.cn/api/paas/v4/chat/completions");
        dialogLayout.addView(urlInput);
        
        // 模型名称输入框
        final TextView modelLabel = new TextView(getActivity());
        modelLabel.setText("模型名称:");
        modelLabel.setTextSize(12);
        modelLabel.setTextColor(0xFF666666);
        modelLabel.setPadding(0, 16, 0, 0);
        dialogLayout.addView(modelLabel);
        
        final EditText modelInput = new EditText(getActivity());
        modelInput.setHint("glm-4-flash");
        modelInput.setText("glm-4-flash");
        dialogLayout.addView(modelInput);
        
        // API Key 输入框
        final TextView keyLabel = new TextView(getActivity());
        keyLabel.setText("API Key:");
        keyLabel.setTextSize(12);
        keyLabel.setTextColor(0xFF666666);
        keyLabel.setPadding(0, 16, 0, 0);
        dialogLayout.addView(keyLabel);
        
        final EditText keyInput = new EditText(getActivity());
        keyInput.setHint("输入你的 API Key");
        dialogLayout.addView(keyInput);
        
        new AlertDialog.Builder(getActivity())
            .setTitle("☁️ 云端模型配置")
            .setView(dialogLayout)
            .setPositiveButton("连接", (dialog, which) -> {
                String url = urlInput.getText().toString().trim();
                String model = modelInput.getText().toString().trim();
                String apiKey = keyInput.getText().toString().trim();
                
                if (!url.isEmpty() && !model.isEmpty() && !apiKey.isEmpty()) {
                    logger.log("🔑 配置云端: " + model);
                    logger.log("🔑 API URL: " + url);
                    if (textGeneratorCallback != null) {
                        textGeneratorCallback.onSwitchToCloud(apiKey, url, model);
                    }
                    logger.log("✅ 云端配置完成");
                } else {
                    logger.log("❌ 请填写完整配置信息");
                    Toast.makeText(getActivity(), "请填写 URL、模型名称和 API Key", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void startDownload(String url) {
        // 先获取文件名用于显示
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".gguf")) {
            fileName += ".gguf";
        }
        
        // 创建进度对话框（必须声明为 final）
        final ProgressDialog progressDialog = new ProgressDialog(getActivity());
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMessage("下载中: " + fileName);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        DownloadTask task = new DownloadTask(getActivity(), logger, modelManager, new DownloadTask.Callback() {
            @Override
            public void onProgress(int percent) {
                getActivity().runOnUiThread(() -> progressDialog.setProgress(percent));
            }
            
            @Override
            public void onSuccess(String modelPath, String fileName) {
                getActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    refreshModelList();
                    logger.log("✅ 下载完成: " + fileName);
                    Toast.makeText(getActivity(), "下载完成: " + fileName, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                getActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    logger.log("❌ 下载失败: " + error);
                    Toast.makeText(getActivity(), "下载失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
        task.execute(url);
    }
}