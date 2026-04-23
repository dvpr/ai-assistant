#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include "llama.h"

// 全局实例
static llama_model *g_model = nullptr;
static llama_context *g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dvpr_aiassistant_MainActivity_loadModel(
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
    
    // 新版 API：使用 llama_model_load_from_file 替代 llama_load_model_from_file
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;  // CPU only
    
    g_model = llama_model_load_from_file(model_path_cstr, model_params);
    
    env->ReleaseStringUTFChars(model_path, model_path_cstr);
    
    if (g_model == nullptr) {
        return JNI_FALSE;
    }
    
    // 新版 API：使用 llama_init_from_model 替代 llama_new_context_with_model
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;      // 上下文长度
    ctx_params.n_threads = 4;    // 线程数
    
    g_ctx = llama_init_from_model(g_model, ctx_params);
    
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_dvpr_aiassistant_MainActivity_getEmbedding(
    JNIEnv *env,
    jobject thiz,
    jstring input) {
    
    if (g_model == nullptr || g_ctx == nullptr) {
        return nullptr;
    }
    
    const char *input_cstr = env->GetStringUTFChars(input, nullptr);
    
    // 新版 API：llama_tokenize 的第一个参数需要是 llama_vocab*
    // 从 model 获取 vocab
    const llama_vocab *vocab = llama_model_get_vocab(g_model);
    
    // Tokenize
    std::vector<llama_token> tokens;
    tokens.resize(strlen(input_cstr) + 3);
    int n_tokens = llama_tokenize(vocab, input_cstr, strlen(input_cstr),
                                   tokens.data(), tokens.size(), true, false);
    tokens.resize(n_tokens);
    
    // 新版 API：使用 llama_decode 替代 llama_eval
    // 准备 batch
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    
    // 执行推理
    if (llama_decode(g_ctx, batch) != 0) {
        env->ReleaseStringUTFChars(input, input_cstr);
        return nullptr;
    }
    
    // 新版 API：获取 embedding
    // 注意：对于没有序列的 embedding，需要获取指定的序列 embedding
    const float *embeddings = llama_get_embeddings_seq(g_ctx, 0);
    if (embeddings == nullptr) {
        // 尝试旧版 API 兼容
        embeddings = llama_get_embeddings_ith(g_ctx, 0);
    }
    
    // 新版 API：使用 llama_model_n_embd 替代 llama_n_embd
    int n_embd = llama_model_n_embd(g_model);
    
    // 返回给 Java
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embeddings);
    
    env->ReleaseStringUTFChars(input, input_cstr);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_dvpr_aiassistant_MainActivity_cleanup(
    JNIEnv *env,
    jobject thiz) {
    if (g_ctx) {
        // 新版 API：使用 llama_free 替代 llama_free
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        // 新版 API：使用 llama_model_free 替代 llama_free_model
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}

} // extern "C"