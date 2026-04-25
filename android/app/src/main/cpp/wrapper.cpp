#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdio>
#include <stdarg.h>
#include "llama.h"

#define LOG_TAG "LlamaNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 全局变量
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;

// 屏幕日志回调
static jobject g_debugLogger = nullptr;
static jmethodID g_logMethod = nullptr;

struct GenerationParams {
    int32_t n_predict = 256;
    float temperature = 0.3f;
    float top_p = 0.9f;
    int32_t top_k = 40;
    int32_t seed = 1234;
};

// 屏幕日志函数
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

extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_setDebugLogger(JNIEnv *env, jobject thiz, jobject logger) {
    if (g_debugLogger != nullptr) {
        env->DeleteGlobalRef(g_debugLogger);
    }
    g_debugLogger = env->NewGlobalRef(logger);
    
    jclass loggerClass = env->GetObjectClass(logger);
    g_logMethod = env->GetMethodID(loggerClass, "log", "(Ljava/lang/String;)V");
    
    screenLog(env, "🔧 DebugLogger 已绑定到 C++ 层");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llama_MainActivity_loadModel(JNIEnv *env, jobject thiz, jstring model_path) {
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
    
    // 初始化 llama 后端（只需一次）
    static bool initialized = false;
    if (!initialized) {
        llama_backend_init();
        initialized = true;
        screenLog(env, "llama_backend 初始化完成");
    }
    
    // 加载模型
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    screenLog(env, "开始加载模型文件...");
    
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    env->ReleaseStringUTFChars(model_path, model_path_cstr);
    
    if (!g_model) {
        screenLog(env, "✗ 模型加载失败！");
        return JNI_FALSE;
    }
    screenLog(env, "✓ 模型文件加载成功");
    
    // 获取 vocab（关键！每次加载新模型都要重新获取）
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
    screenLogf(env, "  - n_embd: %d", llama_model_n_embd(g_model));
    screenLogf(env, "  - n_layer: %d", llama_model_n_layer(g_model));
    screenLogf(env, "  - n_head: %d", llama_model_n_head(g_model));
    
    // 创建上下文
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
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

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llama_MainActivity_generateText(JNIEnv *env, jobject thiz, jstring prompt) {
    // 先检查模型状态
    if (!g_model || !g_ctx || !g_vocab) {
        screenLog(env, "❌ 模型未初始化，请先加载模型");
        return env->NewStringUTF("[错误] 模型未初始化");
    }
    
    screenLog(env, "========== 开始生成文本 ==========");
    
    // 调试：打印 vocab 信息和测试 token
    screenLogf(env, "g_vocab 指针: %p", g_vocab);
    int vocab_size = llama_vocab_n_tokens(g_vocab);
    screenLogf(env, "当前词汇表大小: %d", vocab_size);
    
    char test_piece[64];
    int test_len = llama_token_to_piece(g_vocab, 1, test_piece, sizeof(test_piece), 0, true);
    screenLogf(env, "测试 token[1] -> 文本: %s (长度 %d)", test_piece, test_len);
    
    // 获取输入
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    screenLogf(env, "输入提示词: %s", prompt_cstr);
    
    // 4. Tokenize 重试机制（替代原来的方法）
    std::vector<llama_token> tokens;
    int n_tokens = 0;
    int text_len = strlen(prompt_cstr);

    // 预留一个稍大的初始空间（比如 512）
    tokens.resize(512); 
    n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);

    // 如果返回负数，说明空间不够，取其绝对值得到真实需要的 token 数
    if (n_tokens < 0) {
        int required_size = -n_tokens;
        screenLogf(env, "需要扩容: %d", required_size);
        tokens.resize(required_size);
        n_tokens = llama_tokenize(g_vocab, prompt_cstr, text_len, tokens.data(), tokens.size(), true, false);
    }

    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        screenLog(env, "❌ Tokenize 失败");
        return env->NewStringUTF("[错误] 无需解析输入文本");
    }

    tokens.resize(n_tokens); // 最后调整到准确大小
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    
    if (n_tokens <= 0) {
        screenLog(env, "❌ Tokenize 填充失败");
        return env->NewStringUTF("[错误] Tokenize 填充失败");
    }
    
    screenLogf(env, "成功 tokenize，共 %d 个 tokens", n_tokens);
    
    // 打印前几个 token
    for (int i = 0; i < std::min(10, n_tokens); i++) {
        screenLogf(env, "  token[%d]=%d", i, tokens[i]);
    }
    
    // 采样器
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));
    
    // 生成
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    std::string generated_text;
    llama_token eos_token = llama_vocab_eos(g_vocab);
    screenLogf(env, "EOS token: %d", eos_token);
    
    int generated_tokens = 0;
    const int max_tokens = 256;
    
    for (int i = 0; i < max_tokens; i++) {
        int ret = llama_decode(g_ctx, batch);
        if (ret != 0) {
            screenLogf(env, "❌ llama_decode 失败: %d", ret);
            break;
        }
        
        llama_token token = llama_sampler_sample(smpl, g_ctx, -1);
        if (token < 0) break;
        llama_sampler_accept(smpl, token);
        
        if (token == eos_token) {
            screenLog(env, "遇到 EOS token，停止生成");
            break;
        }
        
        char piece[64];
        int n_chars = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (n_chars > 0) {
            generated_text.append(piece, n_chars);
            generated_tokens++;
            
            if (generated_tokens % 20 == 0) {
                screenLogf(env, "📊 生成进度: %d tokens", generated_tokens);
            }
        }
        
        batch = llama_batch_get_one(&token, 1);
    }
    
    llama_sampler_free(smpl);
    
    screenLogf(env, "✅ 生成完成: %d tokens, %zu 字符", generated_tokens, generated_text.size());
    if (!generated_text.empty()) {
        screenLogf(env, "生成文本: [%s]", generated_text.c_str());
    }
    screenLog(env, "========== 生成结束 ==========");
    
    if (generated_text.empty()) {
        return env->NewStringUTF("[生成失败]");
    }
    return env->NewStringUTF(generated_text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_cleanup(JNIEnv *env, jobject thiz) {
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
    
    screenLog(env, "========== 清理完成 ==========");
}