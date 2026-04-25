package com.example.llama;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelManager {
    private static final String PREF_NAME = "model_manager";
    private static final String KEY_MODEL_LIST = "model_list";
    private static final String KEY_CURRENT_MODEL = "current_model";
    
    private final SharedPreferences prefs;
    
    public ModelManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public void addModel(String modelPath, String fileName) {
        Set<String> models = prefs.getStringSet(KEY_MODEL_LIST, new HashSet<String>());
        Set<String> newModels = new HashSet<String>(models);
        newModels.add(modelPath + "||" + fileName);
        prefs.edit().putStringSet(KEY_MODEL_LIST, newModels).apply();
    }
    
    public List<ModelInfo> getAllModels() {
        List<ModelInfo> result = new ArrayList<ModelInfo>();
        Set<String> models = prefs.getStringSet(KEY_MODEL_LIST, new HashSet<String>());
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