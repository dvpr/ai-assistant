package com.example.llama.memory;

import android.provider.BaseColumns;

public final class MemoryContract {
    private MemoryContract() {}
    
    // 分类表
    public static class CategoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "categories";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
        public static final String COLUMN_POLICY_TYPE = "policy_type";
        public static final String COLUMN_POLICY_COMBINATION = "policy_combination";
        public static final String COLUMN_IS_SETUP = "is_setup";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }
    
    // 记忆数据表
    public static class MemoryEntry implements BaseColumns {
        public static final String TABLE_NAME = "memories";
        public static final String COLUMN_CATEGORY_ID = "category_id";
        public static final String COLUMN_KEY_TEXT = "key_text";
        public static final String COLUMN_VALUE_TEXT = "value_text";
        public static final String COLUMN_RAW_INPUT = "raw_input";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }
    
    // 对话日志表
    public static class ConversationLogEntry implements BaseColumns {
        public static final String TABLE_NAME = "conversation_logs";
        public static final String COLUMN_SESSION_ID = "session_id";
        public static final String COLUMN_USER_INPUT = "user_input";
        public static final String COLUMN_AI_RESPONSE = "ai_response";
        public static final String COLUMN_INTENT_TYPE = "intent_type";
        public static final String COLUMN_CATEGORY_ID = "category_id";
        public static final String COLUMN_MEMORY_ID = "memory_id";
        public static final String COLUMN_CREATED_AT = "created_at";
    }
    
    // 对话日志安全策略表
    public static class ConversationLogPolicyEntry implements BaseColumns {
        public static final String TABLE_NAME = "conversation_log_policy";
        public static final String COLUMN_POLICY_TYPE = "policy_type";
        public static final String COLUMN_POLICY_COMBINATION = "policy_combination";
        public static final String COLUMN_IS_SETUP = "is_setup";
        public static final String COLUMN_UPDATED_AT = "updated_at";
    }
    
    // 用户凭证表
    public static class UserSecretEntry implements BaseColumns {
        public static final String TABLE_NAME = "user_secrets";
        public static final String COLUMN_SECRET_TYPE = "secret_type";
        public static final String COLUMN_SECRET_HASH = "secret_hash";
        public static final String COLUMN_SALT = "salt";
        public static final String COLUMN_CREATED_AT = "created_at";
    }
}