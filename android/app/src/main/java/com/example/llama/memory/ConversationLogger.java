package com.example.llama.memory;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.llama.memory.models.ConversationLog;
import com.example.llama.memory.models.ConversationLogPolicy;

public class ConversationLogger {
    private MemoryDBHelper dbHelper;
    private String currentSessionId;
    private boolean accessVerified = false;
    private long lastVerifyTime = 0;
    private static final long VERIFY_TIMEOUT = 5 * 60 * 1000; // 5分钟有效
    
    public ConversationLogger(Context context) {
        this.dbHelper = new MemoryDBHelper(context);
        this.currentSessionId = generateSessionId();
    }
    
    private String generateSessionId() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return "session_" + sdf.format(new Date());
    }
    
    // 新会话（每天自动或手动）
    public void newSession() {
        currentSessionId = generateSessionId();
    }
    
    // 记录对话
    public void log(String userInput, String aiResponse, String intentType, Long categoryId, Long memoryId) {
        dbHelper.insertConversationLog(currentSessionId, userInput, aiResponse, intentType, categoryId, memoryId);
    }
    
    // 记录普通对话
    public void logNormal(String userInput, String aiResponse) {
        dbHelper.insertConversationLog(currentSessionId, userInput, aiResponse, "NORMAL", null, null);
    }
    
    // 记录记忆存储
    public void logStore(String userInput, String aiResponse, long categoryId, long memoryId) {
        dbHelper.insertConversationLog(currentSessionId, userInput, aiResponse, "STORE", categoryId, memoryId);
    }
    
    // 记录记忆查询
    public void logQuery(String userInput, String aiResponse, long categoryId, long memoryId) {
        dbHelper.insertConversationLog(currentSessionId, userInput, aiResponse, "QUERY", categoryId, memoryId);
    }
    
    // 获取对话历史（需要验证）
    public List<ConversationLog> getHistory() {
        if (!checkAccess()) {
            return null;
        }
        return dbHelper.getConversationLogs();
    }
    
    // 获取对话数量（不需要验证，只显示计数）
    public int getHistoryCount() {
        return dbHelper.getConversationLogCount();
    }
    
    // 清除所有日志（需要验证）
    public boolean clearHistory() {
        if (!checkAccess()) {
            return false;
        }
        return dbHelper.clearConversationLogs();
    }
    
    // 检查访问权限
    private boolean checkAccess() {
        long now = System.currentTimeMillis();
        if (accessVerified && (now - lastVerifyTime) < VERIFY_TIMEOUT) {
            return true;
        }
        
        // 需要重新验证
        ConversationLogPolicy policy = dbHelper.getConversationLogPolicy();
        boolean verified = SecurityVerifier.verifyConversationLog(policy);
        
        if (verified) {
            accessVerified = true;
            lastVerifyTime = now;
        }
        return verified;
    }
    
    // 重置验证状态（切换用户或退出时调用）
    public void resetVerification() {
        accessVerified = false;
    }
    
    // 获取当前会话ID
    public String getCurrentSessionId() {
        return currentSessionId;
    }
    
    // 更新对话日志的安全策略（需要管理员验证）
    public boolean updatePolicy(String policyType, String policyCombination) {
        // 修改日志策略需要更高权限
        if (!SecurityVerifier.verifyAdmin()) {
            return false;
        }
        return dbHelper.updateConversationLogPolicy(policyType, policyCombination);
    }
    
    // 获取当前安全策略
    public ConversationLogPolicy getPolicy() {
        return dbHelper.getConversationLogPolicy();
    }
    
    // 导出日志为文本（需要额外验证）
    public String exportHistory() {
        if (!checkAccess()) {
            return null;
        }
        List<ConversationLog> logs = dbHelper.getConversationLogs();
        StringBuilder sb = new StringBuilder();
        for (ConversationLog log : logs) {
            sb.append("[").append(log.getFormattedTime()).append("]\n");
            sb.append("用户: ").append(log.getUserInput()).append("\n");
            sb.append("AI: ").append(log.getAiResponse()).append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }
}