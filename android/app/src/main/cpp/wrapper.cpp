/**
 * @file llama_jni.cpp
 * @brief Android JNI 桥接代码，用于在 Java 层调用 llama.cpp 进行本地推理。
 *
 * 本文件实现了模型加载、文本生成（支持流式输出）以及资源清理等功能。
 * 所有与 llama.cpp 的交互都通过此原生层完成。
 * 在阅读本文件前，需了解以下背景：
 * - JNI (Java Native Interface): Java 调用 C/C++ 代码的桥梁。
 * - llama.cpp: 一个纯 C/C++ 实现的大语言模型推理库，本文件使用其 API。
 * - 全局变量：用于在整个 JNI 调用生命周期内维护模型、上下文和回调。
 */

// ==================== 头文件 ====================
#include <jni.h>          // JNI 核心头文件，提供 JNI 函数、类型和宏
#include <android/log.h>  // Android 日志系统，用于在 Logcat 中输出调试信息
#include <string>         // C++ std::string 类
#include <vector>         // C++ std::vector 容器
#include <cstring>        // C 字符串操作函数，如 strlen
#include <cstdio>         // C 标准输入输出，如 FILE, fopen, fclose
#include <stdarg.h>       // 可变参数宏，用于实现可变参数函数 (va_list, vsnprintf)
#include "llama.h"        // llama.cpp 的头文件，定义了所有 llama API

// ==================== 日志宏定义 ====================
#define LOG_TAG "LlamaNative"   // 日志标签，在 Logcat 中用于过滤
// LOGD: 输出 DEBUG 级别日志，自动携带 LOG_TAG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
// LOGE: 输出 ERROR 级别日志
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ==================== 全局变量（跨越 JNI 调用，持久化状态） ====================
// 所有全局变量仅在 C++ 侧维护，需要小心管理生命周期，防止内存泄漏
static llama_model *g_model = nullptr;   // 指向已加载的 llama 模型
static llama_context *g_ctx = nullptr;   // 推理上下文，与模型绑定，管理 KV 缓存等
static const llama_vocab *g_vocab = nullptr; // 词汇表指针，从模型获取，用于 tokenize/decode

// ---- 调试日志回调 (Java -> C++) ----
// 一个 Java 对象，用于将 C++ 的日志信息显示在 Android UI 上
static jobject g_debugLogger = nullptr;   // Java DebugLogger 对象的全局引用
static jmethodID g_logMethod = nullptr;   // DebugLogger.log(String) 方法的 ID

// ---- 流式输出回调 (C++ -> Java) ----
// 当生成文本时，C++ 需要将片段实时传回 Java 层的 MainActivity / NativeBridge 的宿主 Activity
static jobject g_mainActivityObj = nullptr;    // MainActivity 对象的全局引用（需要绑定到实际 Activity）
static jmethodID g_onStreamChunkMid = nullptr; // onStreamChunk(String) 方法 ID
static jmethodID g_onStreamFinishMid = nullptr;// onStreamFinish() 方法 ID

// ==================== 工具函数：屏幕日志 ====================
void screenLog(JNIEnv* env, const char* msg) {
    if (g_debugLogger != nullptr && g_logMethod != nullptr) {
        jstring jmsg = env->NewStringUTF(msg);
        env->CallVoidMethod(g_debugLogger, g_logMethod, jmsg);
        env->DeleteLocalRef(jmsg);
    }
    LOGD("%s", msg);
}

void screenLogf(JNIEnv* env, const char* format, ...) {
    char buffer[1024];
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    screenLog(env, buffer);
}

// ==================== JNI 导出函数（类名改为 NativeBridge） ====================

/**
 * @brief 设置 C++ 层的日志记录器（来自 Java）。
 * Java 方法签名: public native void setDebugLogger(DebugLogger logger);
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_NativeBridge_setDebugLogger(JNIEnv *env, jobject thiz, jobject logger) {
    if (g_debugLogger != nullptr) {
        env->DeleteGlobalRef(g_debugLogger);
    }
    g_debugLogger = env->NewGlobalRef(logger);
    jclass loggerClass = env->GetObjectClass(logger);
    g_logMethod = env->GetMethodID(loggerClass, "log", "(Ljava/lang/String;)V");
    screenLog(env, "🔧 DebugLogger 已绑定到 C++ 层");
}

/**
 * @brief 加载指定路径的 GGUF 模型文件。
 * Java 方法签名: public native boolean loadModel(String modelPath);
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llama_NativeBridge_loadModel(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    screenLogf(env, "========== 开始加载模型 ==========");
    screenLogf(env, "模型路径: %s", model_path_cstr);

    // 检查文件是否存在
    FILE *f = fopen(model_path_cstr, "r");
    if (f) {
        fclose(f);
        screenLog(env, "✓ 模型文件存在");
    } else {
        screenLogf(env, "✗ 模型文件不存在: %s", model_path_cstr);
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        return JNI_FALSE;
    }

    // 初始化 llama 后端（全局仅一次）
    static bool initialized = false;
    if (!initialized) {
        llama_backend_init();
        initialized = true;
        screenLog(env, "llama_backend 初始化完成");
    }

    // ---- 加载模型 ----
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 12;      // GPU 加速层数
    model_params.main_gpu = 0;
    model_params.use_mmap = false;       // Android 必须关
    model_params.use_mlock = false;      // Android 必须关
    screenLog(env, "开始加载模型文件...");
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    env->ReleaseStringUTFChars(model_path, model_path_cstr);

    if (!g_model) {
        screenLog(env, "✗ 模型加载失败！");
        return JNI_FALSE;
    }
    screenLog(env, "✓ 模型文件加载成功");
    screenLogf(env, "GPU layers: %d", model_params.n_gpu_layers);
    screenLog(env, llama_print_system_info());

    // ---- 获取词汇表 ----
    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        screenLog(env, "✗ 获取 vocab 失败！");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    int vocab_size = llama_vocab_n_tokens(g_vocab);
    screenLogf(env, "✓ 词汇表大小: %d", vocab_size);

    // 打印模型参数
    screenLog(env, "模型参数信息:");
    screenLogf(env, " - n_embd: %d", llama_model_n_embd(g_model));
    screenLogf(env, " - n_layer: %d", llama_model_n_layer(g_model));
    screenLogf(env, " - n_head: %d", llama_model_n_head(g_model));

    // ---- 创建上下文 ----
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = 4;
    ctx_params.n_threads_batch = 4;
    ctx_params.n_batch = 256;
    ctx_params.n_ubatch = 128;

    screenLogf(env, "创建上下文 n_ctx=%d, n_batch=%d", ctx_params.n_ctx, ctx_params.n_batch);
    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        screenLog(env, "✗ 创建上下文失败！");
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    screenLogf(env, "✓ 上下文创建成功，实际 n_ctx=%d", llama_n_ctx(g_ctx));
    screenLog(env, "========== 模型加载完成 ==========");
    return JNI_TRUE;
}

/**
 * @brief 注册流式回调，保存 Activity 对象及回调方法 ID。
 * Java 方法签名: public native void registerStreamCallback();
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_NativeBridge_registerStreamCallback(JNIEnv *env, jobject thiz) {
    if (g_mainActivityObj != nullptr) {
        env->DeleteGlobalRef(g_mainActivityObj);
    }
    g_mainActivityObj = env->NewGlobalRef(thiz);
    jclass cls = env->GetObjectClass(thiz);
    g_onStreamChunkMid = env->GetMethodID(cls, "onStreamChunk", "(Ljava/lang/String;)V");
    g_onStreamFinishMid = env->GetMethodID(cls, "onStreamFinish", "()V");
    screenLog(env, "✅ 流式回调已注册");
}

/**
 * @brief 开始流式文本生成。
 * Java 方法签名: public native String generateText(String prompt);
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llama_NativeBridge_generateText(JNIEnv *env, jobject thiz, jstring prompt) {
    screenLog(env, "🚀 generateText 被调用");
    screenLogf(env, "g_model=%p, g_ctx=%p, g_vocab=%p", g_model, g_ctx, g_vocab);
    if (!g_model || !g_ctx || !g_vocab) {
        screenLog(env, "❌ 模型未初始化");
        return env->NewStringUTF("[错误] 模型未初始化");
    }

    screenLog(env, "========== 开始生成文本 ==========");
    screenLogf(env, "g_vocab 指针: %p", g_vocab);
    int vocab_size = llama_vocab_n_tokens(g_vocab);
    screenLogf(env, "当前词汇表大小: %d", vocab_size);

    char test_piece[64];
    int test_len = llama_token_to_piece(g_vocab, 1, test_piece, sizeof(test_piece), 0, true);
    screenLogf(env, "测试 token[1] -> 文本: %s (长度 %d)", test_piece, test_len);

    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    screenLogf(env, "输入提示词: %s", prompt_cstr);

    // ---- Tokenize ----
    std::vector<llama_token> tokens;
    int n_tokens = 0;
    int text_len = strlen(prompt_cstr);
    tokens.resize(512);
    n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);
    if (n_tokens < 0) {
        int required_size = -n_tokens;
        screenLogf(env, "需要扩容: %d", required_size);
        tokens.resize(required_size);
        n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);
    }
    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        screenLog(env, "❌ Tokenize 失败");
        return env->NewStringUTF("[错误] Tokenize 失败");
    }
    tokens.resize(n_tokens);
    env->ReleaseStringUTFChars(prompt, prompt_cstr);

    screenLogf(env, "成功 tokenize，共 %d 个 tokens", n_tokens);
    for (int i = 0; i < std::min(10, n_tokens); i++) {
        screenLogf(env, " token[%d]=%d", i, tokens[i]);
    }

    // ---- 采样器 ----
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    // 适配新版 API：使用简单采样器（可自定义）
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(10));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    std::string generated_text;
    llama_token eos_token = llama_vocab_eot(g_vocab);  // 使用 End of Turn
    screenLogf(env, "EOS token: %d", eos_token);

    int generated_tokens = 0;
    const int SAFE_MAX_TOKENS = 80;
    std::string current_segment;
    const int CHUNK_CHAR_LIMIT = 40;
    const char* puncts = "，。！？；：";
    bool callbackOk = (g_mainActivityObj != nullptr && g_onStreamChunkMid != nullptr);

    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());

    // 循环外定义重复检测变量
    std::string last_output;
    int repeat_count = 0;

    while (true) {
        if (generated_tokens >= SAFE_MAX_TOKENS) {
            screenLog(env, "⚠️ 达到安全最大生成上限，主动停止");
            break;
        }

        int ret = llama_decode(g_ctx, batch);
        if (ret != 0) {
            screenLogf(env, "❌ llama_decode 失败: %d", ret);
            break;
        }

        llama_token token = llama_sampler_sample(smpl, g_ctx, -1);
        if (token < 0) break;
        llama_sampler_accept(smpl, token);

        if (token == eos_token) {
            screenLog(env, "✅ 检测到EOS，自然结束生成");
            break;
        }

        char piece[64];
        int n_chars = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (n_chars > 0) {
            std::string piece_str(piece, n_chars);
            // 检测 stop token
            if (piece_str.find("<|im_end|>") != std::string::npos ||
                piece_str.find("<|endoftext|>") != std::string::npos) {
                screenLog(env, "🛑 命中 stop token");
                break;
            }
            // 重复检测
            if (piece_str == last_output) {
                repeat_count++;
            } else {
                repeat_count = 0;
            }
            last_output = piece_str;
            if (repeat_count >= 5) {
                screenLog(env, "⚠️ 检测到重复输出，强制停止");
                break;
            }

            generated_text.append(piece_str);
            current_segment.append(piece_str);

            bool needFlush = false;
            if (current_segment.size() >= CHUNK_CHAR_LIMIT) needFlush = true;
            for (int p = 0; puncts[p] != '\0'; p++) {
                if (piece[0] == puncts[p]) {
                    needFlush = true;
                    break;
                }
            }

            if (needFlush) {
                if (callbackOk) {
                    jstring jChunk = env->NewStringUTF(current_segment.c_str());
                    env->CallVoidMethod(g_mainActivityObj, g_onStreamChunkMid, jChunk);
                    env->DeleteLocalRef(jChunk);
                } else {
                    screenLog(env, "⚠️ 回调未注册，跳过推送");
                }
                current_segment.clear();
            }
        }

        batch = llama_batch_get_one(&token, 1);
        generated_tokens++;
    }

    // 推送剩余片段
    if (!current_segment.empty() && g_mainActivityObj != nullptr) {
        jstring jChunk = env->NewStringUTF(current_segment.c_str());
        env->CallVoidMethod(g_mainActivityObj, g_onStreamChunkMid, jChunk);
        env->DeleteLocalRef(jChunk);
    }
    if (g_mainActivityObj != nullptr && g_onStreamFinishMid != nullptr) {
        env->CallVoidMethod(g_mainActivityObj, g_onStreamFinishMid);
    }

    llama_sampler_free(smpl);
    screenLogf(env, "✅ 生成完成: %d tokens, %zu 字符", generated_tokens, generated_text.size());
    screenLog(env, "========== 生成结束 ==========");
    return env->NewStringUTF("");
}

/**
 * @brief 释放所有全局 C++ 资源。
 * Java 方法签名: public native void cleanup();
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_NativeBridge_cleanup(JNIEnv *env, jobject thiz) {
    screenLog(env, "========== 清理资源 ==========");
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        screenLog(env, "✓ 上下文已释放");
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        screenLog(env, "✓ 模型已释放");
    }
    g_vocab = nullptr;
    // 注意：不删除 g_debugLogger 和 g_mainActivityObj 的全局引用，避免影响后续使用
    screenLog(env, "========== 清理完成 ==========");
}