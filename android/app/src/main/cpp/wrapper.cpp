#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.h"

// 全局实例
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;

extern "C" {

// 统一使用 com_example_llama 包名
JNIEXPORT jboolean JNICALL
Java_com_example_llama_MainActivity_loadModel(
    JNIEnv *env,
    jobject thiz,
    jstring model_path) {
    
    const char *model_path_cstr = env->GetStringUTFChars(model_path, nullptr);
    
    // 初始化 llama（只需一次）
    static bool initialized = false;
    if (!initialized) {
        llama_backend_init();
        initialized = true;
    }
    
    // 加载模型
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only
    
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    
    env->ReleaseStringUTFChars(model_path, model_path_cstr);
    
    if (g_model == nullptr) {
        return JNI_FALSE;
    }
    
    // 创建上下文
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;      // 上下文长度
    ctx_params.n_threads = 4;    // 线程数
    
    g_ctx = llama_init_from_model(g_model, ctx_params);
    
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_llama_MainActivity_getEmbedding(
    JNIEnv *env,
    jobject thiz,
    jstring input) {
    
    if (g_model == nullptr || g_ctx == nullptr) {
        return nullptr;
    }
    
    const char *input_cstr = env->GetStringUTFChars(input, nullptr);
    
    // 从 model 获取 vocab
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    
    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(strlen(input_cstr) + 3);
    int n_tokens = llama_tokenize(vocab, input_cstr, strlen(input_cstr),
                                   tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);
    
    // 准备 batch 并执行推理
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        env->ReleaseStringUTFChars(input, input_cstr);
        return nullptr;
    }
    
    // 获取 embedding
    const float *embeddings = llama_get_embeddings_seq(g_ctx, 0);
    if (embeddings == nullptr) {
        embeddings = llama_get_embeddings_ith(g_ctx, 0);
    }
    
    // 获取 embedding 维度
    int n_embd = llama_model_n_embd(g_model);
    
    // 返回给 Java
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embeddings);
    
    env->ReleaseStringUTFChars(input, input_cstr);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_llama_MainActivity_cleanup(
    JNIEnv *env,
    jobject thiz) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}

} // extern "C"