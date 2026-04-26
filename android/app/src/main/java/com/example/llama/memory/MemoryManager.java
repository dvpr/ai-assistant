package com.example.llama.memory;

import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

import com.example.llama.memory.models.Category;
import com.example.llama.memory.models.Memory;

public class MemoryManager {
    private MemoryDBHelper dbHelper;
    
    public MemoryManager(Context context) {
        this.dbHelper = new MemoryDBHelper(context);
    }
    
    // ========== 分类操作 ==========
    
    public List<Category> getAllCategories() {
        List<Category> categories = new ArrayList<>();
        Cursor cursor = dbHelper.getAllCategories();
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Category category = new Category(
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.CategoryEntry._ID)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_NAME)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION)),
                    cursor.getInt(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_IS_SETUP)) == 1
                );
                categories.add(category);
            }
            cursor.close();
        }
        return categories;
    }
    
    public Category getCategoryById(long id) {
        return dbHelper.getCategoryById(id);
    }
    
    public Category getCategoryByName(String name) {
        return dbHelper.getCategoryByName(name);
    }
    
    public boolean setupCategoryPolicy(long categoryId, String policyType, String policyCombination) {
        return dbHelper.updateCategoryPolicy(categoryId, policyType, policyCombination);
    }
    
    // ========== 记忆操作 ==========
    
    public boolean storeMemory(String categoryName, String keyText, String valueText, String rawInput) {
        Category category = dbHelper.getCategoryByName(categoryName);
        if (category == null) {
            return false;
        }
        
        // 检查是否已存在
        Memory existing = dbHelper.getMemoryByKey(keyText, category.getId());
        if (existing != null) {
            // 更新已有记忆
            return dbHelper.updateMemory(existing.getId(), valueText);
        } else {
            // 插入新记忆
            return dbHelper.insertMemory(category.getId(), keyText, valueText, rawInput) > 0;
        }
    }
    
    public Memory getMemory(String categoryName, String keyText) {
        Category category = dbHelper.getCategoryByName(categoryName);
        if (category == null) {
            return null;
        }
        return dbHelper.getMemoryByKey(keyText, category.getId());
    }
    
    public List<Memory> getAllMemories() {
        return dbHelper.getAllMemories();
    }
    
    public boolean deleteMemory(long id) {
        return dbHelper.deleteMemory(id);
    }
    
    // ========== 智能查询 ==========
    
    public String smartQuery(String userInput) {
        // 提取可能的关键词
        String normalizedInput = userInput.toLowerCase();
        
        // 遍历所有分类和记忆，尝试匹配
        List<Category> categories = getAllCategories();
        for (Category category : categories) {
            if (category.getName().toLowerCase().contains(normalizedInput) ||
                category.getDisplayName().contains(normalizedInput)) {
                return "你问的是" + category.getDisplayName() + "类别，请说更具体一些。";
            }
        }
        
        // 尝试匹配具体记忆
        List<Memory> memories = getAllMemories();
        for (Memory memory : memories) {
            String key = memory.getKeyText().toLowerCase();
            if (normalizedInput.contains(key) || 
                memory.getRawInput().toLowerCase().contains(normalizedInput)) {
                return memory.getValueText();
            }
        }
        
        return null; // 没有找到匹配的记忆
    }
}