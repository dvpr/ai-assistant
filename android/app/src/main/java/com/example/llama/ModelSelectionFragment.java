package com.example.llama;

import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
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
        void onSwitchToCloud(String apiKey);
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
        EditText input = new EditText(getActivity());
        input.setHint("输入 API Key");
        
        new AlertDialog.Builder(getActivity())
            .setTitle("DeepSeek")
            .setView(input)
            .setPositiveButton("确定", (dialog, which) -> {
                String key = input.getText().toString().trim();
                if (!key.isEmpty() && textGeneratorCallback != null) {
                    textGeneratorCallback.onSwitchToCloud(key);
                    logger.log("切换到云端");
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void startDownload(String url) {
        AlertDialog progress = new AlertDialog.Builder(getActivity())
            .setTitle("下载中")
            .setMessage("请稍候...")
            .setCancelable(false)
            .create();
        progress.show();
        
        DownloadTask task = new DownloadTask(logger, modelManager, new DownloadTask.Callback() {
            @Override
            public void onProgress(int percent) {}
            @Override
            public void onSuccess(String path, String name) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    refreshModelList();
                    logger.log("下载完成: " + name);
                    Toast.makeText(getActivity(), "下载完成", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onError(String error) {
                getActivity().runOnUiThread(() -> {
                    progress.dismiss();
                    logger.log("下载失败: " + error);
                    Toast.makeText(getActivity(), "下载失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
        task.execute(url);
    }
}