#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.h"

#define LOG_TAG "LlamaNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;
static const llama_vocab *g_vocab = nullptr;

struct GenerationParams {
    int32_t n_predict = 256;
    float temperature = 0.3f;
    float top_p = 0.9f;
    int32_t top_k = 40;
    int32_t seed = 1234;
};

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_llama_MainActivity_loadModel(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    LOGD("加载模型: %s", model_path_cstr);

    static bool initialized = false;
    if (!initialized) {
        llama_backend_init();
        initialized = true;
        LOGD("llama_backend 初始化完成");
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    env->ReleaseStringUTFChars(model_path, model_path_cstr);

    if (!g_model) {
        LOGE("模型加载失败");
        return JNI_FALSE;
    }

    // 获取 vocab (必须)
    g_vocab = llama_model_get_vocab(g_model);
    if (!g_vocab) {
        LOGE("获取 vocab 失败");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 2048;
    ctx_params.n_batch = 512;
    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("创建上下文失败");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGD("模型加载成功，上下文大小: %d", llama_n_ctx(g_ctx));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_llama_MainActivity_generateText(JNIEnv *env, jobject thiz, jstring prompt) {
    // 1. 检查模型
    if (!g_model || !g_ctx || !g_vocab) {
        LOGE("模型未初始化");
        return env->NewStringUTF("[错误] 模型未初始化");
    }
    
    // 2. 打印 vocab 信息
    int vocab_size = llama_vocab_n_tokens(g_vocab);
    LOGD("Vocab 大小: %d", vocab_size);
    
    // 3. 获取输入
    const char *prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
    LOGD("输入: '%s'", prompt_cstr);
    
    // 4. 尝试不同的 tokenize 方式
    std::vector<llama_token> tokens;
    
    // 方式1: 使用默认参数
    int n_tokens = llama_tokenize(g_vocab, prompt_cstr, strlen(prompt_cstr), nullptr, 0, true, false);
    LOGD("方式1 token 数量: %d", n_tokens);
    
    // 方式2: 不添加特殊 token
    if (n_tokens <= 0) {
        n_tokens = llama_tokenize(g_vocab, prompt_cstr, strlen(prompt_cstr), nullptr, 0, false, false);
        LOGD("方式2 (add_special=false) token 数量: %d", n_tokens);
    }
    
    // 方式3: 解析单个字符
    if (n_tokens <= 0) {
        // 尝试手动添加一个 token
        tokens.push_back(1);  // 常见的起始 token
        n_tokens = 1;
        LOGD("使用 fallback token: 1");
    }
    
    if (n_tokens <= 0) {
        env->ReleaseStringUTFChars(prompt, prompt_cstr);
        return env->NewStringUTF("[错误] 无法 tokenize");
    }
    
    tokens.resize(n_tokens);
    if (n_tokens > 1) {  // 避免二次调用错误
        llama_tokenize(g_vocab, prompt_cstr, strlen(prompt_cstr), tokens.data(), tokens.size(), true, false);
    }
    env->ReleaseStringUTFChars(prompt, prompt_cstr);
    
    // 打印 tokens
    for (int i = 0; i < n_tokens; i++) {
        LOGD("token[%d]=%d", i, tokens[i]);
    }
    
    // 5. 生成参数
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler *smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));
    
    // 6. 推理
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    std::string generated_text;
    llama_token eos_token = llama_vocab_eos(g_vocab);
    LOGD("EOS token: %d", eos_token);
    
    for (int i = 0; i < 100; i++) {  // 最多 100 个 token
        int ret = llama_decode(g_ctx, batch);
        if (ret != 0) {
            LOGE("llama_decode 失败: %d", ret);
            break;
        }
        
        llama_token token = llama_sampler_sample(smpl, g_ctx, -1);
        if (token < 0) break;
        llama_sampler_accept(smpl, token);
        
        if (token == eos_token) {
            LOGD("遇到 EOS");
            break;
        }
        
        char piece[64];
        int n_chars = llama_token_to_piece(g_vocab, token, piece, sizeof(piece), 0, true);
        if (n_chars > 0) {
            generated_text.append(piece, n_chars);
        }
        
        batch = llama_batch_get_one(&token, 1);
    }
    
    llama_sampler_free(smpl);
    LOGD("生成文本: [%s]", generated_text.c_str());
    
    if (generated_text.empty()) {
        return env->NewStringUTF("[生成失败]");
    }
    return env->NewStringUTF(generated_text.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_cleanup(JNIEnv *env, jobject thiz) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    LOGD("资源已释放");
}