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
// 当生成文本时，C++ 需要将片段实时传回 Java 层的 MainActivity
static jobject g_mainActivityObj = nullptr;    // MainActivity 对象的全局引用
static jmethodID g_onStreamChunkMid = nullptr; // onStreamChunk(String) 方法 ID
static jmethodID g_onStreamFinishMid = nullptr;// onStreamFinish() 方法 ID

// ==================== 生成参数结构体 ====================
// 集中管理采样参数，便于后续调整。当前在代码中直接硬编码，未使用此结构体的实例。
// struct GenerationParams {
//     int32_t n_predict = 2048;   // 最大生成的 token 数量（实际由循环逻辑控制）
//     float temperature = 0.3f;   // 温度参数，控制随机性
//     float top_p = 0.7f;         // nucleus sampling 概率阈值
//     int32_t top_k = 40;         // top-k 采样，只从概率最高的 k 个 token 中挑选
//     int32_t seed = 1234;        // 随机种子，保证结果可复现
// };

// ==================== 工具函数：屏幕日志 ====================
/**
 * @brief 将一条消息通过 JNI 调用发送到 Java 侧的 DebugLogger，同时输出到 Logcat。
 *
 * @param env JNI 环境指针，用于调用 Java 方法
 * @param msg 要输出的字符串
 *
 * 注意：此函数假设 g_debugLogger 和 g_logMethod 已经由 setDebugLogger 正确设置。
 *       如果未设置，则仅输出到 Logcat。
 */
void screenLog(JNIEnv* env, const char* msg) {
    // 检查 Java 端日志对象和方法是否已就绪
    if (g_debugLogger != nullptr && g_logMethod != nullptr) {
        // 将 C 字符串转换为 Java String 对象
        jstring jmsg = env->NewStringUTF(msg);
        // 调用 g_debugLogger.log(String) 方法
        env->CallVoidMethod(g_debugLogger, g_logMethod, jmsg);
        // 释放局部引用，避免 Java 内存泄漏（或延迟回收）
        env->DeleteLocalRef(jmsg);
    }
    // 同时输出到 Android 系统日志，便于开发者查看
    LOGD("%s", msg);
}

/**
 * @brief 格式化输出屏幕日志，用法同 printf。
 *
 * @param env JNI 环境指针
 * @param format 格式化字符串
 * @param ... 可变参数
 */
void screenLogf(JNIEnv* env, const char* format, ...) {
    char buffer[1024];  // 栈上分配缓冲区，注意不能超过 1024 字节
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, sizeof(buffer), format, args); // 安全格式化
    va_end(args);
    screenLog(env, buffer); // 转发给 screenLog
}

// ==================== JNI 导出函数 ====================
// 函数命名遵循 JNI 规范：Java_包名_类名_方法名，包名中的点替换为下划线。
// 所有以 "Java_com_example_llama_MainActivity_" 开头的函数均可从 Java 侧调用。

/**
 * @brief 设置 C++ 层的日志记录器（来自 Java）。
 *
 * Java 方法签名: public native void setDebugLogger(DebugLogger logger);
 *
 * @param env   JNI 环境
 * @param thiz  MainActivity 对象引用（未使用）
 * @param logger Java 侧传入的 DebugLogger 对象
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_setDebugLogger(JNIEnv *env, jobject thiz, jobject logger) {
    // 为避免重复创建全局引用导致内存泄漏，先清理旧的全局引用
    if (g_debugLogger != nullptr) {
        env->DeleteGlobalRef(g_debugLogger);
    }
    // 创建全局引用，确保 logger 对象在 C++ 层面持久化，不会因 JVM 回收而失效
    g_debugLogger = env->NewGlobalRef(logger);
    // 获取 logger 的类对象
    jclass loggerClass = env->GetObjectClass(logger);
    // 查找并缓存 log(String) 方法的 ID，参数签名为 "(Ljava/lang/String;)V"
    g_logMethod = env->GetMethodID(loggerClass, "log", "(Ljava/lang/String;)V");
    // 输出一条确认消息，表明绑定成功
    screenLog(env, "🔧 DebugLogger 已绑定到 C++ 层");
}

/**
 * @brief 加载指定路径的 GGUF 模型文件。
 *
 * Java 方法签名: public native boolean loadModel(String modelPath);
 *
 * @param env        JNI 环境
 * @param thiz       MainActivity 对象引用
 * @param model_path 模型文件的绝对路径（Java String）
 * @return JNI_TRUE 加载成功，JNI_FALSE 失败
 *
 * 加载流程：
 * 1. 检查模型文件是否存在。
 * 2. 初始化 llama.cpp 后端（全局一次）。
 * 3. 使用默认参数加载模型。
 * 4. 获取词汇表并验证。
 * 5. 创建推理上下文。
 * 任何一步失败都会清理已分配资源并返回 false。
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llama_MainActivity_loadModel(JNIEnv *env, jobject thiz, jstring model_path) {
    // 将 Java 字符串转换为 C 字符串，GetStringUTFChars 会分配内存
    const char *model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    screenLogf(env, "========== 开始加载模型 ==========");
    screenLogf(env, "模型路径: %s", model_path_cstr);

    // ---- 1. 检查文件是否存在 ----
    // 使用 C 标准库的 fopen 尝试以只读模式打开文件
    FILE *f = fopen(model_path_cstr, "r");
    if (f) {
        fclose(f);  // 立即关闭，仅用于检查存在性
        screenLog(env, "✓ 模型文件存在");
    } else {
        screenLogf(env, "✗ 模型文件不存在: %s", model_path_cstr);
        // 释放之前从 GetStringUTFChars 获取的字符串空间
        env->ReleaseStringUTFChars(model_path, model_path_cstr);
        return JNI_FALSE;
    }

    // ---- 2. 初始化 llama 后端（全局仅一次） ----
    static bool initialized = false;
    if (!initialized) {
        // 执行后端初始化，例如内存分配、线程池等
        llama_backend_init();
        initialized = true;
        screenLog(env, "llama_backend 初始化完成");
    }

    // ---- 3. 加载模型 ----
    // ==============================
    // 模型加载参数（适配 llama.cpp b4818）
    // ==============================
    llama_model_params model_params = llama_model_default_params();

    // 把多少层模型放到GPU上运行
    // Redmi K60：12 最稳最快；想更稳就设 8
    model_params.n_gpu_layers = 12;

    // 仅使用单个GPU（手机只有一个，固定0）
    model_params.main_gpu = 0;

    // 安卓必须关：内存映射，会导致读取模型失败
    model_params.use_mmap = false;

    // 安卓必须关：锁定物理内存，会触发权限问题
    model_params.use_mlock = false;

    screenLog(env, "开始加载模型文件...");
    // 核心加载函数，从文件路径读取模型权重到内存
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    // 加载完成后即可释放 C 字符串
    env->ReleaseStringUTFChars(model_path, model_path_cstr);

    if (!g_model) {
        screenLog(env, "✗ 模型加载失败！");
        return JNI_FALSE;
    }
    screenLog(env, "✓ 模型文件加载成功");
    screenLogf(env, "GPU layers: %d", model_params.n_gpu_layers);
    screenLogf(env, "llama backend: %s", llama_print_system_info());
    #ifdef GGML_USE_VULKAN
    screenLog(env, "✅ Vulkan 已启用");
    #else
        screenLog(env, "❌ Vulkan 未启用");
    #endif

    #ifdef GGML_USE_OPENCL
        screenLog(env, "✅ OpenCL 已启用");
    #else
        screenLog(env, "❌ OpenCL 未启用");
    #endif

    // ---- 4. 获取词汇表 ----
    // 词汇表用于 token 与文本之间的双向转换
    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        screenLog(env, "✗ 获取 vocab 失败！");
        // 词汇表获取失败时，必须释放模型并清空指针
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    int vocab_size = llama_vocab_n_tokens(g_vocab);
    screenLogf(env, "✓ 词汇表大小: %d", vocab_size);

    // 打印模型关键超参数，便于调试
    screenLog(env, "模型参数信息:");
    screenLogf(env, " - n_embd: %d", llama_model_n_embd(g_model));  // 嵌入维度
    screenLogf(env, " - n_layer: %d", llama_model_n_layer(g_model)); // Transformer 层数
    screenLogf(env, " - n_head: %d", llama_model_n_head(g_model));   // 注意力头数

    // ---- 5. 创建推理上下文 ----
    // 上下文管理 KV 缓存、计算图等
    // ==============================
    // 上下文参数（适配 llama.cpp b4818）
    // ==============================
    llama_context_params ctx_params = llama_context_default_params();

    // // 上下文窗口大小（能记住多少对话）
    // // K60：8192 最稳，不闪退
    // ctx_params.n_ctx = 2048;

    // // CPU 线程数
    // // 骁龙8+ 设 4 最流畅，不卡UI
    // ctx_params.n_threads = 8;
    // ctx_params.n_threads_batch = 4;

    // // 批处理大小（手机通用 512）
    // ctx_params.n_batch = 2048;
    // ctx_params.n_ubatch = 512;

    // // Qwen2 模型必须用的默认值，不要改
    // ctx_params.rope_freq_base = 10000.0f;
    // ctx_params.rope_freq_scale = 1.0f;
    ctx_params.n_ctx = 1024;        // ❗从2048降一半（巨大提升）
    ctx_params.n_threads = 4;       // ❗最佳
    ctx_params.n_threads_batch = 4;

    ctx_params.n_batch = 256;       // ❗从2048 → 256（核心优化）
    ctx_params.n_ubatch = 128;

    // 1.0 表示无惩罚，>1.0 惩罚重复
    // ctx_params.repeat_penalty = 1.1f; 

    // 创建上下文
    // g_ctx = llama_new_context_with_model(g_model, ctx_params);

    screenLogf(env, "创建上下文 n_ctx=%d, n_batch=%d", ctx_params.n_ctx, ctx_params.n_batch);
    g_ctx = llama_init_from_model(g_model, ctx_params);

    if (!g_ctx) {
        screenLog(env, "✗ 创建上下文失败！");
        // 上下文创建失败时，释放模型资源并清空所有全局指针
        llama_model_free(g_model);
        g_model = nullptr;
        g_vocab = nullptr;
        return JNI_FALSE;
    }

    // 打印实际使用的上下文大小（可能因模型或内存限制而调整）
    screenLogf(env, "✓ 上下文创建成功，实际 n_ctx=%d", llama_n_ctx(g_ctx));
    screenLog(env, "========== 模型加载完成 ==========");
    return JNI_TRUE;
}

/**
 * @brief 注册流式回调，保存 MainActivity 对象及回调方法的 ID。
 *
 * Java 方法签名: public native void registerStreamCallback();
 *
 * 调用该方法后，C++ 侧将持有 MainActivity 的全局引用，
 * 以便在生成文本时主动调用 onStreamChunk 和 onStreamFinish。
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_registerStreamCallback(JNIEnv *env, jobject thiz) {
    // 清理之前可能持有的全局引用（避免重复注册导致内存泄漏）
    if (g_mainActivityObj != nullptr) {
        env->DeleteGlobalRef(g_mainActivityObj);
    }
    // 保存 MainActivity 对象的全局引用
    g_mainActivityObj = env->NewGlobalRef(thiz);

    // 获取 MainActivity 的类对象
    jclass cls = env->GetObjectClass(thiz);
    // 缓存 onStreamChunk(String) 方法 ID，签名 "(Ljava/lang/String;)V"
    g_onStreamChunkMid = env->GetMethodID(cls, "onStreamChunk", "(Ljava/lang/String;)V");
    // 缓存 onStreamFinish() 方法 ID，签名 "()V"
    g_onStreamFinishMid = env->GetMethodID(cls, "onStreamFinish", "()V");

    screenLog(env, "✅ 流式回调已注册");
}

/**
 * @brief 开始流式文本生成。该方法会阻塞直到生成完成或遇到终止条件。
 *
 * Java 方法签名: public native String generateText(String prompt);
 *
 * 设计说明：
 * - 为了在 Android 上避免 ANR，此方法应在后台线程中调用。
 * - 返回值固定为空字符串，所有生成内容通过 onStreamChunk 流式传回 Java。
 * - 生成过程中，每生成一个 token 都会检查是否满足推送条件（如达到字符数限制或遇到标点），
 *   然后立即调用回调，实现流式效果。
 *
 * 生成流程：
 * 1. 检查模型是否已加载。
 * 2. 将 prompt 文本 tokenize 为 token 序列。
 * 3. 初始化采样器链（top-k → top-p → temperature → distribution sampler）。
 * 4. 循环进行解码：llama_decode → 采样下一个 token → 拼接文本 → 推送到 Java。
 * 5. 直到遇到 EOS token 或达到安全上限。
 * 6. 最后清理资源，通知 Java 侧生成完成。
 */
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llama_MainActivity_generateText(JNIEnv *env, jobject thiz, jstring prompt) {
    // ---- 前置检查 ----
    screenLog(env, "🚀 generateText 被调用");
    screenLogf(env, "g_model=%p, g_ctx=%p, g_vocab=%p", g_model, g_ctx, g_vocab);
    if (!g_model || !g_ctx || !g_vocab) {
        screenLog(env, "❌ 模型未初始化，请先加载模型");
        // 返回一个提示字符串，Java 层可据此显示错误
        return env->NewStringUTF("[错误] 模型未初始化");
    }

    screenLog(env, "========== 开始生成文本 ==========");

    // 调试信息：验证 vocab 是否可用，并测试一个 token 的解码结果
    screenLogf(env, "g_vocab 指针: %p", g_vocab);
    int vocab_size = llama_vocab_n_tokens(g_vocab);
    screenLogf(env, "当前词汇表大小: %d", vocab_size);

    // 测试解码 token ID 为 1 的 token 对应的文本片段（piece）
    char test_piece[64];
    int test_len = llama_token_to_piece(g_vocab, 1, test_piece, sizeof(test_piece), 0, true);
    screenLogf(env, "测试 token[1] -> 文本: %s (长度 %d)", test_piece, test_len);

    // ---- 1. 获取并保存 prompt ----
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    screenLogf(env, "输入提示词: %s", prompt_cstr);

    // ---- 2. Tokenize (分词) ----
    std::vector<llama_token> tokens;
    int n_tokens = 0;
    int text_len = strlen(prompt_cstr);
    tokens.resize(512); // 初始预留 512 个 token 的空间，大多数 prompt 不会超过

    // llama_tokenize 返回实际 token 数量，若缓冲区不足则返回 -所需大小
    // 参数：vocab, 输入文本, 文本长度, 输出缓冲区, 缓冲区大小, 是否添加 BOS, 是否特殊 token 处理
    n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);
    if (n_tokens < 0) {
        // 缓冲区不足，根据返回的负值获取实际所需大小并重新分配
        int required_size = -n_tokens;
        screenLogf(env, "需要扩容: %d", required_size);
        tokens.resize(required_size);
        // 再次调用，此时缓冲区大小足够
        n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);
    }

    // 如果分词失败（返回 0 表示成功但为 0 token？实际上至少应有 BOS token）
    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        screenLog(env, "❌ Tokenize 失败");
        return env->NewStringUTF("[错误] Tokenize 失败");
    }
    tokens.resize(n_tokens); // 调整为实际大小
    env->ReleaseStringUTFChars(prompt, prompt_cstr); // 释放 prompt 的 C 字符串

    screenLogf(env, "成功 tokenize，共 %d 个 tokens", n_tokens);
    // 打印前 10 个 token ID，便于调试
    for (int i = 0; i < std::min(10, n_tokens); i++) {
        screenLogf(env, " token[%d]=%d", i, tokens[i]);
    }

    // ---- 3. 初始化采样器 (Sampler) ----
    // 采样器决定了如何从模型输出的概率分布中选择下一个 token。
    // 使用链式采样器，按顺序应用多个采样策略。
    auto sparams = llama_sampler_chain_default_params();   // 获取链式采样器默认参数
    llama_sampler *smpl = llama_sampler_chain_init(sparams); // 创建采样链

    // 向链中添加具体的采样器，注意添加顺序即为应用顺序
    // // Top-K 采样：只保留概率最高的 40 个 token，其他置零
    // llama_sampler_chain_add(smpl, llama_sampler_init_top_k(20));
    // // Top-P 采样：从概率累加到 0.9 的 token 集合中挑选，min_keep 为 1 保证至少一个可用
    // llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.8f, 1));
    // // 温度采样：调整概率分布的尖锐程度，较低的温度（如0.2）使输出更确定，较高则更多样
    // llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.4f));
    // // 重复惩罚移到这里
    // llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
    //     128,    // ✅ last_n（记忆长度）
    //     1.2f,   // ✅ repeat_penalty（关键）
    //     0.0f,   // frequency_penalty
    //     0.0f    // presence_penalty
    // ));
    // // 分布采样器：根据最终概率分布随机抽取一个 token，并设定随机种子 1234 以实现可复现
    // llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(10));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    // 字符串累加器，用于存储完整生成文本（但目前实际未返回完整结果，仅在日志中统计）
    std::string generated_text;
    // 获取 EOS (End of Sentence) token ID，遇到此 token 表示生成自然结束
    // llama_token eos_token = llama_vocab_eos(g_vocab);
    llama_token eos_token = llama_vocab_eot(g_vocab);
    screenLogf(env, "EOS token: %d", eos_token);

    int generated_tokens = 0;           // 已生成的 token 计数
    const int SAFE_MAX_TOKENS = 80;   // 安全生成上限，防止死循环或异常长文本导致 OOM
    std::string current_segment;        // 累积的文本片段，用于流式推送
    const int CHUNK_CHAR_LIMIT = 40;    // 当累积字符数达到此限制时，触发一次推送
    const char* puncts = "，。！？；："; // 中文标点，遇到这些字符也立即推送
    bool callbackOk = (g_mainActivityObj != nullptr && g_onStreamChunkMid != nullptr);

    // 创建初始 batch，包含所有 prompt tokens，进行第一次解码
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    // llama_batch batch = llama_batch_init(512, 0, 1);

    // ---- 4. 核心生成循环 ----
    // 无限循环直到内部 break
    while (true) {
        // 安全保护：当生成 token 数达到上限时强制停止，并输出警告
        if (generated_tokens >= SAFE_MAX_TOKENS) {
            screenLog(env, "⚠️ 达到安全最大生成上限，主动停止");
            break;
        }

        // 执行解码，输入当前 batch（可能只包含一个 token），更新上下文状态
        int ret = llama_decode(g_ctx, batch);
        if (ret != 0) {
            // 非 0 表示错误，常见原因如上下文溢出（超过 n_ctx）
            screenLogf(env, "❌ llama_decode 失败: %d", ret);
            break;
        }

        // 使用采样链从当前上下文中采样下一个 token（-1 表示最后一个位置的 logits）
        llama_token token = llama_sampler_sample(smpl, g_ctx, -1);
        if (token < 0) break; // 无效 token
        // 通知采样器该 token 已被接受（影响某些依赖于状态的采样器，如温度？实际上链式采样器无状态）
        llama_sampler_accept(smpl, token);

        // 遇到 EOS token，表示生成结束，自然退出循环
        if (token == eos_token) {
            screenLog(env, "✅ 检测到EOS，自然结束生成");
            break;
        }

        // 将 token 转换为可读的文本片段（piece）
        char piece[64];
        int n_chars = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);

        std::string last_output;
        int repeat_count = 0;
        if (n_chars > 0) {
            std::string piece_str(piece, n_chars);

            if (piece_str.find("<|im_end|>") != std::string::npos ||
                piece_str.find("<|endoftext|>") != std::string::npos) {
                screenLog(env, "🛑 命中 stop token");
                break;
            }

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
            // ========== 修复版：遇到标点立即停止 ==========
            // char first = piece[0];
            // if (first == u'。' || first == u'？' || first == u'！' || first == '.' || first == '?' || first == '!') {
                
            //     // 追加最后一段内容
            //     generated_text.append(piece, n_chars);
            //     current_segment.append(piece, n_chars);

            //     // 推送最后一段
            //     if (!current_segment.empty() && g_mainActivityObj != nullptr && g_onStreamChunkMid != nullptr) {
            //         jstring jChunk = env->NewStringUTF(current_segment.c_str());
            //         env->CallVoidMethod(g_mainActivityObj, g_onStreamChunkMid, jChunk);
            //         env->DeleteLocalRef(jChunk);
            //         current_segment.clear();
            //     }

            //     screenLog(env, "✅ 遇到结束标点，强制停止生成");
            //     break;
            // }
            // ============================================

            // 累加到全量文本（用于日志统计）
            generated_text.append(piece, n_chars);
            // 累加到当前推送片段
            current_segment.append(piece, n_chars);

            // ---- 判断是否需要立即推送片段 ----
            bool needFlush = false;
            // 条件1：累积字符数超过限制
            if (current_segment.size() >= CHUNK_CHAR_LIMIT) {
                needFlush = true;
            }
            // 条件2：碰到中文标点符号（通过检查最新 piece 的第一个字符是否为标点）
            //       因为一个中文标点通常单独作为一个 token。
            for (int p = 0; puncts[p] != '\0'; p++) {
                if (piece[0] == puncts[p]) {
                    needFlush = true;
                    break;
                }
            }

            // 如果满足推送条件，且 Java 侧的回调已注册，则通过 JNI 调用传递
            if (needFlush) {
                if (callbackOk) {
                    // 推送片段
                    jstring jChunk = env->NewStringUTF(current_segment.c_str());
                    env->CallVoidMethod(g_mainActivityObj, g_onStreamChunkMid, jChunk);
                    env->DeleteLocalRef(jChunk);
                    // screenLogf(env, "📤 推送片段: %s ", current_segment.c_str());
                } else {
                    screenLog(env, "⚠️ 回调未注册，跳过推送");
                }
                current_segment.clear();
            }

            // 每生成 20 个 token 输出一条进度日志
            if (generated_tokens % 20 == 0) {
                // screenLogf(env, "📊 %d tokens", generated_tokens);
            }
        }

        // 准备下一次解码的 batch：仅包含刚生成的这一个 token
        batch = llama_batch_get_one(&token, 1);
        generated_tokens++;
    } // 结束 while 循环

    // ---- 5. 推送残余片段 ----
    // 生成结束后，current_segment 中可能还有未推送的字符（例如不足 15 个字符的结尾）
    if (!current_segment.empty() && g_mainActivityObj != nullptr) {
        jstring jChunk = env->NewStringUTF(current_segment.c_str());
        env->CallVoidMethod(g_mainActivityObj, g_onStreamChunkMid, jChunk);
        env->DeleteLocalRef(jChunk);
    }

    // 通知 Java 侧生成已经完成
    if (g_mainActivityObj != nullptr && g_onStreamFinishMid != nullptr) {
        env->CallVoidMethod(g_mainActivityObj, g_onStreamFinishMid);
    }

    // ---- 6. 清理本次生成使用的资源 ----
    llama_sampler_free(smpl); // 释放采样链

    screenLogf(env, "✅ 生成完成: %d tokens, %zu 字符", generated_tokens, generated_text.size());
    screenLog(env, "========== 生成结束 ==========");

    // 返回空字符串，因为所有文本都已通过流式回调传递
    // Java 层不应依赖此返回值来获取生成内容。
    return env->NewStringUTF("");
}

/**
 * @brief 释放所有全局 C++ 资源（模型、上下文）以及 JNI 全局引用。
 *
 * Java 方法签名: public native void cleanup();
 *
 * 应在不再需要模型或 Activity 销毁时调用，以避免内存泄漏。
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_cleanup(JNIEnv *env, jobject thiz) {
    screenLog(env, "========== 清理资源 ==========");

    // 释放推理上下文
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
        screenLog(env, "✓ 上下文已释放");
    }

    // 释放模型
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
        screenLog(env, "✓ 模型已释放");
    }

    // 词汇表指针随模型失效，无需额外释放，只需置空
    g_vocab = nullptr;

    // 清理 JNI 全局引用，防止 Java 对象无法被 GC 回收
    // if (g_debugLogger != nullptr) {
    //     env->DeleteGlobalRef(g_debugLogger);
    //     g_debugLogger = nullptr;
    // }
    // if (g_mainActivityObj != nullptr) {
    //     env->DeleteGlobalRef(g_mainActivityObj);
    //     g_mainActivityObj = nullptr;
    // }

    // 注意：jmethodID 是永久有效的，无需删除，但全局引用需要手动删除。

    screenLog(env, "========== 清理完成 ==========");
}