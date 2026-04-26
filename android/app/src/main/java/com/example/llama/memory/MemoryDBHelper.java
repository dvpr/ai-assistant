package com.example.llama.memory;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

import com.example.llama.memory.models.Category;
import com.example.llama.memory.models.Memory;
import com.example.llama.memory.models.ConversationLog;
import com.example.llama.memory.models.ConversationLogPolicy;

public class MemoryDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "memory.db";
    private static final int DATABASE_VERSION = 1;
    
    public MemoryDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建分类表
        String createCategoryTable = 
            "CREATE TABLE " + MemoryContract.CategoryEntry.TABLE_NAME + " (" +
            MemoryContract.CategoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            MemoryContract.CategoryEntry.COLUMN_NAME + " TEXT UNIQUE NOT NULL, " +
            MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME + " TEXT NOT NULL, " +
            MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE + " TEXT, " +
            MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION + " TEXT, " +
            MemoryContract.CategoryEntry.COLUMN_IS_SETUP + " INTEGER DEFAULT 0, " +
            MemoryContract.CategoryEntry.COLUMN_CREATED_AT + " INTEGER, " +
            MemoryContract.CategoryEntry.COLUMN_UPDATED_AT + " INTEGER)";
        db.execSQL(createCategoryTable);
        
        // 创建记忆数据表
        String createMemoryTable = 
            "CREATE TABLE " + MemoryContract.MemoryEntry.TABLE_NAME + " (" +
            MemoryContract.MemoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID + " INTEGER NOT NULL, " +
            MemoryContract.MemoryEntry.COLUMN_KEY_TEXT + " TEXT NOT NULL, " +
            MemoryContract.MemoryEntry.COLUMN_VALUE_TEXT + " TEXT NOT NULL, " +
            MemoryContract.MemoryEntry.COLUMN_RAW_INPUT + " TEXT, " +
            MemoryContract.MemoryEntry.COLUMN_CREATED_AT + " INTEGER, " +
            MemoryContract.MemoryEntry.COLUMN_UPDATED_AT + " INTEGER, " +
            "FOREIGN KEY(" + MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID + 
            ") REFERENCES " + MemoryContract.CategoryEntry.TABLE_NAME + "(" + 
            MemoryContract.CategoryEntry._ID + "))";
        db.execSQL(createMemoryTable);
        
        // 创建对话日志表
        String createConversationLogTable = 
            "CREATE TABLE " + MemoryContract.ConversationLogEntry.TABLE_NAME + " (" +
            MemoryContract.ConversationLogEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            MemoryContract.ConversationLogEntry.COLUMN_SESSION_ID + " TEXT, " +
            MemoryContract.ConversationLogEntry.COLUMN_USER_INPUT + " TEXT, " +
            MemoryContract.ConversationLogEntry.COLUMN_AI_RESPONSE + " TEXT, " +
            MemoryContract.ConversationLogEntry.COLUMN_INTENT_TYPE + " TEXT, " +
            MemoryContract.ConversationLogEntry.COLUMN_CATEGORY_ID + " INTEGER, " +
            MemoryContract.ConversationLogEntry.COLUMN_MEMORY_ID + " INTEGER, " +
            MemoryContract.ConversationLogEntry.COLUMN_CREATED_AT + " INTEGER, " +
            "FOREIGN KEY(" + MemoryContract.ConversationLogEntry.COLUMN_CATEGORY_ID + 
            ") REFERENCES " + MemoryContract.CategoryEntry.TABLE_NAME + "(" + 
            MemoryContract.CategoryEntry._ID + "))";
        db.execSQL(createConversationLogTable);
        
        // 创建对话日志安全策略表
        String createLogPolicyTable = 
            "CREATE TABLE " + MemoryContract.ConversationLogPolicyEntry.TABLE_NAME + " (" +
            MemoryContract.ConversationLogPolicyEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_TYPE + " TEXT, " +
            MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_COMBINATION + " TEXT, " +
            MemoryContract.ConversationLogPolicyEntry.COLUMN_IS_SETUP + " INTEGER DEFAULT 0, " +
            MemoryContract.ConversationLogPolicyEntry.COLUMN_UPDATED_AT + " INTEGER)";
        db.execSQL(createLogPolicyTable);
        
        // 创建用户凭证表
        String createUserSecretTable = 
            "CREATE TABLE " + MemoryContract.UserSecretEntry.TABLE_NAME + " (" +
            MemoryContract.UserSecretEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            MemoryContract.UserSecretEntry.COLUMN_SECRET_TYPE + " TEXT UNIQUE NOT NULL, " +
            MemoryContract.UserSecretEntry.COLUMN_SECRET_HASH + " TEXT, " +
            MemoryContract.UserSecretEntry.COLUMN_SALT + " TEXT, " +
            MemoryContract.UserSecretEntry.COLUMN_CREATED_AT + " INTEGER)";
        db.execSQL(createUserSecretTable);
        
        // 插入预设数据
        insertDefaultCategories(db);
        insertDefaultLogPolicy(db);
    }
    
    private void insertDefaultCategories(SQLiteDatabase db) {
        String[] categories = {"Security", "Personal", "Work", "Health", "Finance"};
        String[] displayNames = {"安全信息", "个人信息", "工作信息", "健康信息", "财务信息"};
        String[] policyTypes = {"fingerprint", "device_lock", null, "device_lock", "fingerprint"};
        
        for (int i = 0; i < categories.length; i++) {
            ContentValues values = new ContentValues();
            values.put(MemoryContract.CategoryEntry.COLUMN_NAME, categories[i]);
            values.put(MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME, displayNames[i]);
            values.put(MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE, policyTypes[i]);
            values.put(MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION, policyTypes[i]);
            values.put(MemoryContract.CategoryEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
            db.insert(MemoryContract.CategoryEntry.TABLE_NAME, null, values);
        }
    }
    
    private void insertDefaultLogPolicy(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_TYPE, "combination");
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_COMBINATION, "fingerprint AND pin AND device_lock");
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_IS_SETUP, 0);
        db.insert(MemoryContract.ConversationLogPolicyEntry.TABLE_NAME, null, values);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + MemoryContract.MemoryEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MemoryContract.CategoryEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MemoryContract.ConversationLogEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MemoryContract.ConversationLogPolicyEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + MemoryContract.UserSecretEntry.TABLE_NAME);
        onCreate(db);
    }
    
    // ========== 分类操作 ==========
    
    public long insertCategory(String name, String displayName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MemoryContract.CategoryEntry.COLUMN_NAME, name);
        values.put(MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME, displayName);
        values.put(MemoryContract.CategoryEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
        return db.insert(MemoryContract.CategoryEntry.TABLE_NAME, null, values);
    }
    
    public Cursor getAllCategories() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(MemoryContract.CategoryEntry.TABLE_NAME, null, null, null, null, null, null);
    }
    
    public Category getCategoryById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(MemoryContract.CategoryEntry.TABLE_NAME, null,
            MemoryContract.CategoryEntry._ID + "=?", new String[]{String.valueOf(id)}, 
            null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Category category = new Category(
                cursor.getLong(cursor.getColumnIndex(MemoryContract.CategoryEntry._ID)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION)),
                cursor.getInt(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_IS_SETUP)) == 1
            );
            cursor.close();
            return category;
        }
        if (cursor != null) cursor.close();
        return null;
    }
    
    public Category getCategoryByName(String name) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(MemoryContract.CategoryEntry.TABLE_NAME, null,
            MemoryContract.CategoryEntry.COLUMN_NAME + "=?", new String[]{name}, 
            null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Category category = new Category(
                cursor.getLong(cursor.getColumnIndex(MemoryContract.CategoryEntry._ID)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_NAME)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_DISPLAY_NAME)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION)),
                cursor.getInt(cursor.getColumnIndex(MemoryContract.CategoryEntry.COLUMN_IS_SETUP)) == 1
            );
            cursor.close();
            return category;
        }
        if (cursor != null) cursor.close();
        return null;
    }
    
    public boolean updateCategoryPolicy(long categoryId, String policyType, String policyCombination) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MemoryContract.CategoryEntry.COLUMN_POLICY_TYPE, policyType);
        values.put(MemoryContract.CategoryEntry.COLUMN_POLICY_COMBINATION, policyCombination);
        values.put(MemoryContract.CategoryEntry.COLUMN_IS_SETUP, 1);
        values.put(MemoryContract.CategoryEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
        return db.update(MemoryContract.CategoryEntry.TABLE_NAME, values,
            MemoryContract.CategoryEntry._ID + "=?", new String[]{String.valueOf(categoryId)}) > 0;
    }
    
    // ========== 记忆操作 ==========
    
    public long insertMemory(long categoryId, String keyText, String valueText, String rawInput) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID, categoryId);
        values.put(MemoryContract.MemoryEntry.COLUMN_KEY_TEXT, normalizeKey(keyText));
        values.put(MemoryContract.MemoryEntry.COLUMN_VALUE_TEXT, valueText);
        values.put(MemoryContract.MemoryEntry.COLUMN_RAW_INPUT, rawInput);
        values.put(MemoryContract.MemoryEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
        values.put(MemoryContract.MemoryEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
        return db.insert(MemoryContract.MemoryEntry.TABLE_NAME, null, values);
    }
    
    public Memory getMemoryByKey(String keyText, long categoryId) {
        SQLiteDatabase db = getReadableDatabase();
        String normalizedKey = normalizeKey(keyText);
        Cursor cursor = db.query(MemoryContract.MemoryEntry.TABLE_NAME, null,
            MemoryContract.MemoryEntry.COLUMN_KEY_TEXT + "=? AND " + 
            MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID + "=?",
            new String[]{normalizedKey, String.valueOf(categoryId)}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            Memory memory = new Memory(
                cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry._ID)),
                cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_KEY_TEXT)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_VALUE_TEXT)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_RAW_INPUT)),
                cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_CREATED_AT))
            );
            cursor.close();
            return memory;
        }
        if (cursor != null) cursor.close();
        return null;
    }
    
    public boolean updateMemory(long id, String valueText) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MemoryContract.MemoryEntry.COLUMN_VALUE_TEXT, valueText);
        values.put(MemoryContract.MemoryEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
        return db.update(MemoryContract.MemoryEntry.TABLE_NAME, values,
            MemoryContract.MemoryEntry._ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }
    
    public boolean deleteMemory(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(MemoryContract.MemoryEntry.TABLE_NAME,
            MemoryContract.MemoryEntry._ID + "=?", new String[]{String.valueOf(id)}) > 0;
    }
    
    public List<Memory> getAllMemories() {
        List<Memory> memories = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(MemoryContract.MemoryEntry.TABLE_NAME, null,
            null, null, null, null, MemoryContract.MemoryEntry.COLUMN_CREATED_AT + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                Memory memory = new Memory(
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry._ID)),
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_CATEGORY_ID)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_KEY_TEXT)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_VALUE_TEXT)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_RAW_INPUT)),
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.MemoryEntry.COLUMN_CREATED_AT))
                );
                memories.add(memory);
            }
            cursor.close();
        }
        return memories;
    }
    
    private String normalizeKey(String keyText) {
        // 规范化key，去除特殊字符，转小写
        return keyText.toLowerCase()
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "_")
            .replaceAll("_+", "_");
    }
    
    // ========== 对话日志操作 ==========
    
    public long insertConversationLog(String sessionId, String userInput, String aiResponse, 
                                       String intentType, Long categoryId, Long memoryId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MemoryContract.ConversationLogEntry.COLUMN_SESSION_ID, sessionId);
        values.put(MemoryContract.ConversationLogEntry.COLUMN_USER_INPUT, userInput);
        values.put(MemoryContract.ConversationLogEntry.COLUMN_AI_RESPONSE, aiResponse);
        values.put(MemoryContract.ConversationLogEntry.COLUMN_INTENT_TYPE, intentType);
        if (categoryId != null) {
            values.put(MemoryContract.ConversationLogEntry.COLUMN_CATEGORY_ID, categoryId);
        }
        if (memoryId != null) {
            values.put(MemoryContract.ConversationLogEntry.COLUMN_MEMORY_ID, memoryId);
        }
        values.put(MemoryContract.ConversationLogEntry.COLUMN_CREATED_AT, System.currentTimeMillis());
        return db.insert(MemoryContract.ConversationLogEntry.TABLE_NAME, null, values);
    }
    
    public List<ConversationLog> getConversationLogs() {
        List<ConversationLog> logs = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(MemoryContract.ConversationLogEntry.TABLE_NAME, null,
            null, null, null, null, MemoryContract.ConversationLogEntry.COLUMN_CREATED_AT + " DESC");
        if (cursor != null) {
            while (cursor.moveToNext()) {
                ConversationLog log = new ConversationLog(
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.ConversationLogEntry._ID)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogEntry.COLUMN_SESSION_ID)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogEntry.COLUMN_USER_INPUT)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogEntry.COLUMN_AI_RESPONSE)),
                    cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogEntry.COLUMN_INTENT_TYPE)),
                    cursor.getLong(cursor.getColumnIndex(MemoryContract.ConversationLogEntry.COLUMN_CREATED_AT))
                );
                logs.add(log);
            }
            cursor.close();
        }
        return logs;
    }
    
    public int getConversationLogCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + MemoryContract.ConversationLogEntry.TABLE_NAME, null);
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }
    
    public boolean clearConversationLogs() {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(MemoryContract.ConversationLogEntry.TABLE_NAME, null, null) > 0;
    }
    
    // ========== 对话日志安全策略操作 ==========
    
    public ConversationLogPolicy getConversationLogPolicy() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(MemoryContract.ConversationLogPolicyEntry.TABLE_NAME, null,
            null, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            ConversationLogPolicy policy = new ConversationLogPolicy(
                cursor.getLong(cursor.getColumnIndex(MemoryContract.ConversationLogPolicyEntry._ID)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_TYPE)),
                cursor.getString(cursor.getColumnIndex(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_COMBINATION)),
                cursor.getInt(cursor.getColumnIndex(MemoryContract.ConversationLogPolicyEntry.COLUMN_IS_SETUP)) == 1
            );
            cursor.close();
            return policy;
        }
        if (cursor != null) cursor.close();
        return null;
    }
    
    public boolean updateConversationLogPolicy(String policyType, String policyCombination) {
        SQLiteDatabase db = getWritableDatabase();
        // 先删除旧的
        db.delete(MemoryContract.ConversationLogPolicyEntry.TABLE_NAME, null, null);
        // 插入新的
        ContentValues values = new ContentValues();
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_TYPE, policyType);
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_POLICY_COMBINATION, policyCombination);
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_IS_SETUP, 1);
        values.put(MemoryContract.ConversationLogPolicyEntry.COLUMN_UPDATED_AT, System.currentTimeMillis());
        return db.insert(MemoryContract.ConversationLogPolicyEntry.TABLE_NAME, null, values) > 0;
    }
}