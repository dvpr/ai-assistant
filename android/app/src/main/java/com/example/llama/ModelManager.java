package com.example.llama;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.File;

public class ModelManager {
    private static final String PREF_NAME = "model_manager";
    private static final String KEY_MODEL_LIST = "model_list";
    private static final String KEY_CURRENT_MODEL = "current_model";
    
    private final SharedPreferences prefs;
    private final Context context;
    
    public ModelManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void addModel(String modelPath, String fileName) {
        Set<String> models = prefs.getStringSet(KEY_MODEL_LIST, new HashSet<String>());
        Set<String> newModels = new HashSet<String>(models);
        newModels.add(modelPath + "||" + fileName);
        prefs.edit().putStringSet(KEY_MODEL_LIST, newModels).apply();
    }
    
    /**
     * 扫描应用私有目录下的所有 .gguf 文件，并同步到 SharedPreferences。
     * 如果文件存在但不在记录中则添加，如果记录存在但文件已被删除则移除。
     */
    public void syncModels() {
        File[] files = context.getFilesDir().listFiles();
        if (files == null) return;

        Set<String> currentRecords = new HashSet<>(prefs.getStringSet(KEY_MODEL_LIST, new HashSet<>()));
        Set<String> newRecords = new HashSet<>();

        // 遍历文件系统，收集所有 .gguf 文件
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".gguf")) {
                String entry = file.getAbsolutePath() + "||" + file.getName();
                newRecords.add(entry);
            }
        }

        // 对比变化
        boolean changed = !newRecords.equals(currentRecords);
        if (changed) {
            prefs.edit().putStringSet(KEY_MODEL_LIST, newRecords).apply();
        }
    }

    /**
     * 获取所有模型（自动同步文件系统）
     */
    public List<ModelInfo> getAllModels() {
        // 每次查询前同步一次（或者按需调用）
        syncModels();

        List<ModelInfo> result = new ArrayList<>();
        Set<String> models = prefs.getStringSet(KEY_MODEL_LIST, new HashSet<>());
        for (String entry : models) {
            String[] parts = entry.split("\\|\\|");
            if (parts.length == 2) {
                result.add(new ModelInfo(parts[0], parts[1]));
            }
        }
        return result;
    }
    
    public void setCurrentModel(String modelPath) {
        prefs.edit().putString(KEY_CURRENT_MODEL, modelPath).apply();
    }
    
    public String getCurrentModel() {
        return prefs.getString(KEY_CURRENT_MODEL, null);
    }
    
    public void removeModel(String modelPath) {
        Set<String> models = prefs.getStringSet(KEY_MODEL_LIST, new HashSet<String>());
        Set<String> newModels = new HashSet<String>();
        for (String entry : models) {
            if (!entry.startsWith(modelPath + "||")) {
                newModels.add(entry);
            }
        }
        prefs.edit().putStringSet(KEY_MODEL_LIST, newModels).apply();
        
        if (modelPath.equals(getCurrentModel())) {
            prefs.edit().remove(KEY_CURRENT_MODEL).apply();
        }
    }
    
    public static class ModelInfo {
        public final String path;
        public final String name;
        
        public ModelInfo(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }
}