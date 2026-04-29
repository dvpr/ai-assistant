package com.example.llama;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.llama.memory.MemoryManager;
import com.example.llama.memory.ConversationLogger;
import com.example.llama.memory.models.*;

/**
 * 对话处理器：负责意图识别、记忆存储/查询、普通对话的路由。
 */
public class ConversationProcessor {
    private final Activity activity;
    private final DebugLogger logger;
    private final MemoryManager memoryManager;
    private final ConversationLogger conversationLogger;
    private final NativeBridge nativeBridge;
    private CloudGenerator cloudGenerator;          // 可变，支持动态更新
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean useCloud = false;

    public ConversationProcessor(Activity activity, DebugLogger logger,
                                 MemoryManager memoryManager, ConversationLogger conversationLogger,
                                 CloudGenerator cloudGenerator, NativeBridge nativeBridge) {
        this.activity = activity;
        this.logger = logger;
        this.memoryManager = memoryManager;
        this.conversationLogger = conversationLogger;
        this.cloudGenerator = cloudGenerator;
        this.nativeBridge = nativeBridge;
    }

    /** 设置是否使用云端模式 */
    public void setUseCloud(boolean useCloud) {
        this.useCloud = useCloud;
    }

    /** 动态设置云端生成器实例（用于切换云端配置） */
    public void setCloudGenerator(CloudGenerator generator) {
        this.cloudGenerator = generator;
    }

    /** 处理用户输入的主入口 */
    public void processInput(String userInput, UiManager ui) {
        conversationLogger.logNormal(userInput, "");
        parseIntent(userInput, new IntentCallback() {
            @Override
            public void onStore(String key, String value, String category) {
                boolean success = memoryManager.storeMemory(category, key, value, userInput);
                ui.setResultText(success ? "✅ 已记住" : "❌ 保存失败");
                logger.log(success ? "记忆存储成功" : "记忆存储失败");
                conversationLogger.logStore(userInput, success ? "已记住" : "失败",
                        memoryManager.getCategoryByName(category) != null ? memoryManager.getCategoryByName(category).getId() : 0, 0);
            }

            @Override
            public void onQuery(String keywords) {
                performQuery(keywords, ui);
            }

            @Override
            public void onChat() {
                performChat(userInput, ui);
            }

            @Override
            public void onError(String error) {
                ui.setResultText("处理失败: " + error);
                logger.log("意图解析错误: " + error);
                performChat(userInput, ui);
            }
        });
    }

    // ==================== 意图解析 ====================
    private void parseIntent(String userInput, IntentCallback callback) {
        if (cloudGenerator != null) {
            String prompt = buildIntentParsingPrompt(userInput);
            cloudGenerator.generate(prompt, new TextGenerator.Callback() {
                @Override public void onStart() {}
                @Override public void onToken(String token) {}
                @Override
                public void onComplete(String fullText) {
                    String jsonStr = extractJsonFromAIResponse(fullText);
                    try {
                        JSONObject obj = new JSONObject(jsonStr);
                        String intent = obj.getString("intent");
                        if ("STORE".equals(intent)) {
                            callback.onStore(obj.getString("key"), obj.getString("value"), obj.getString("category"));
                        } else if ("QUERY".equals(intent)) {
                            callback.onQuery(obj.getString("keywords"));
                        } else {
                            callback.onChat();
                        }
                    } catch (JSONException e) {
                        callback.onChat();
                    }
                }
                @Override public void onError(String error) {
                    fallbackIntentParsing(userInput, callback);
                }
            });
        } else {
            fallbackIntentParsing(userInput, callback);
        }
    }

    private void fallbackIntentParsing(String userInput, IntentCallback callback) {
        if (userInput.contains("记一下") || userInput.contains("记住")) {
            String ip = extractIP(userInput);
            if (ip != null && userInput.contains("密码")) {
                callback.onStore("server_" + ip + "_password", "临时占位", "Security");
                return;
            }
            callback.onChat();
        } else if (userInput.contains("是什么") || userInput.contains("多少")) {
            callback.onQuery(userInput);
        } else {
            callback.onChat();
        }
    }

    // ==================== 查询记忆 ====================
    private void performQuery(String keywords, UiManager ui) {
        ui.setResultText("🔍 正在查询...");
        List<Memory> results = new ArrayList<>();
        for (Memory m : memoryManager.getAllMemories()) {
            if (m.getRawInput().contains(keywords) || m.getKeyText().contains(keywords)) {
                results.add(m);
            }
        }
        if (results.isEmpty()) {
            ui.setResultText("未找到相关信息");
            return;
        }
        Memory memory = results.get(0);
        Category category = memoryManager.getCategoryById(memory.getCategoryId());
        BiometricHelper.authenticate(activity, new BiometricHelper.Callback() {
            @Override public void onSuccess() {
                ui.setResultText(category.getDisplayName() + "：" + memory.getValueText());
            }
            @Override public void onError(String error) {
                ui.setResultText("验证失败: " + error);
            }
        });
    }

    // ==================== 普通对话 ====================
    private void performChat(String userInput, UiManager ui) {
        if (useCloud && cloudGenerator != null) {
            startCloudChat(userInput, ui);
        } else {
            startLocalChat(userInput, ui);
        }
    }

    private void startCloudChat(String userInput, UiManager ui) {
        logger.log("☁️ 调用云端 API");
        ui.setResultText("☁️ 云端思考中...");
        cloudGenerator.generate(userInput, new TextGenerator.Callback() {
            @Override public void onStart() { logger.log("云端请求已发送"); }
            @Override
            public void onToken(String token) {
                activity.runOnUiThread(() -> ui.appendResultText(token));
            }
            @Override
            public void onComplete(String fullText) {
                activity.runOnUiThread(() -> logger.log("✅ 云端生成完成，长度: " + fullText.length()));
            }
            @Override
            public void onError(String error) {
                activity.runOnUiThread(() -> {
                    logger.log("❌ 云端错误: " + error);
                    ui.setResultText("云端错误: " + error);
                });
            }
        });
    }

    private void startLocalChat(String userInput, UiManager ui) {
        logger.log("📱 调用本地模型（流式）");
        ui.setResultText("📱 本地生成中...");
        new Thread(() -> nativeBridge.generateText(userInput)).start();
    }

    // ==================== 辅助方法 ====================
    private String extractJsonFromAIResponse(String response) {
        if (response == null) return "";
        response = response.trim();
        if (response.startsWith("```json")) response = response.substring(7);
        else if (response.startsWith("```")) response = response.substring(3);
        if (response.endsWith("```")) response = response.substring(0, response.length() - 3);
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start != -1 && end != -1 && start < end) return response.substring(start, end + 1);
        return response;
    }

    private String buildIntentParsingPrompt(String userInput) {
        return "你是一个智能助手，需要判断用户的意图，并提取相关信息。意图分为三种：\n" +
               "1. STORE：用户想要记住、存储某些信息（如密码、生日、计划）。\n" +
               "2. QUERY：用户想要查询之前记住的信息（如密码是多少、生日是哪天）。\n" +
               "3. CHAT：普通对话，不需要存储或查询记忆。\n\n" +
               "输出必须为 JSON 格式，字段：intent, key, value, category。\n" +
               "- 当 intent 为 STORE 时，需提取 key（关键词）、value（要记住的值）、category（从 Security, Personal, Work, Health, Finance 中选择）。\n" +
               "- 当 intent 为 QUERY 时，需提取 keywords（查询的关键词描述）。\n" +
               "- 当 intent 为 CHAT 时，无需其他字段。\n\n" +
               "示例1：\n输入：帮我记一下 1.1.1.1 的密码是 admin123\n输出：{\"intent\": \"STORE\", \"key\": \"server_1.1.1.1_password\", \"value\": \"admin123\", \"category\": \"Security\"}\n\n" +
               "示例2：\n输入：妈妈的生日是什么时候\n输出：{\"intent\": \"QUERY\", \"keywords\": \"妈妈的生日\"}\n\n" +
               "示例3：\n输入：今天天气真好\n输出：{\"intent\": \"CHAT\"}\n\n" +
               "现在请处理：\n输入：" + userInput;
    }

    private String extractIP(String input) {
        if (input == null) return null;
        Pattern pattern = Pattern.compile("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b");
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }
}