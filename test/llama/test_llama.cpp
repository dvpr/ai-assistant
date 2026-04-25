#include "llama.h"
#include <iostream>
#include <string>
#include <vector>
#include <chrono>

void print_tokens(const llama_vocab* vocab, const std::vector<llama_token>& tokens) {
    std::cout << "Tokens (" << tokens.size() << "): ";
    for (size_t i = 0; i < tokens.size() && i < 20; i++) {
        std::cout << tokens[i] << " ";
    }
    if (tokens.size() > 20) std::cout << "...";
    std::cout << std::endl;
}

int main(int argc, char** argv) {
    if (argc < 2) {
        std::cerr << "用法: " << argv[0] << " <模型路径> [提示词]" << std::endl;
        return 1;
    }

    const char* model_path = argv[1];
    const char* prompt = (argc >= 3) ? argv[2] : "Hello";

    std::cout << "========================================" << std::endl;
    std::cout << "llama.cpp 测试程序" << std::endl;
    std::cout << "模型路径: " << model_path << std::endl;
    std::cout << "提示词: " << prompt << std::endl;
    std::cout << "========================================" << std::endl;

    // 初始化后端
    llama_backend_init();
    
    // 加载模型
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(model_path, model_params);
    if (!model) {
        std::cerr << "❌ 加载模型失败" << std::endl;
        return 1;
    }
    std::cout << "✓ 模型加载成功" << std::endl;

    // 获取 vocab
    const llama_vocab* vocab = llama_model_get_vocab(model);
    if (!vocab) {
        std::cerr << "❌ 获取 vocab 失败" << std::endl;
        llama_model_free(model);
        return 1;
    }
    std::cout << "✓ Vocab 大小: " << llama_vocab_n_tokens(vocab) << std::endl;

    // 创建上下文
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 512;
    ctx_params.n_batch = 512;
    llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        std::cerr << "❌ 创建上下文失败" << std::endl;
        llama_model_free(model);
        return 1;
    }
    std::cout << "✓ 上下文创建成功 (n_ctx=" << llama_n_ctx(ctx) << ")" << std::endl;

    // ========== 测试 tokenize ==========
    std::cout << "\n--- 测试 Tokenization ---" << std::endl;
    std::vector<llama_token> tokens;
    int n_tokens = llama_tokenize(vocab, prompt, strlen(prompt), nullptr, 0, true, false);
    std::cout << "llama_tokenize 返回: " << n_tokens << std::endl;
    
    if (n_tokens <= 0) {
        std::cerr << "❌ Tokenize 失败" << std::endl;
        llama_free(ctx);
        llama_model_free(model);
        return 1;
    }
    
    tokens.resize(n_tokens);
    llama_tokenize(vocab, prompt, strlen(prompt), tokens.data(), tokens.size(), true, false);
    print_tokens(vocab, tokens);
    std::cout << "✓ Tokenize 成功" << std::endl;

    // ========== 测试生成 ==========
    std::cout << "\n--- 测试文本生成 ---" << std::endl;
    
    // 采样器
    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(1234));

    // 初始 batch
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    
    std::string generated = prompt;
    llama_token eos_token = llama_vocab_eos(vocab);
    int generated_tokens = 0;
    const int max_tokens = 100;

    auto start_time = std::chrono::steady_clock::now();

    for (int i = 0; i < max_tokens; i++) {
        int ret = llama_decode(ctx, batch);
        if (ret != 0) {
            std::cerr << "❌ llama_decode 失败: " << ret << std::endl;
            break;
        }
        
        llama_token token = llama_sampler_sample(smpl, ctx, -1);
        if (token < 0) break;
        llama_sampler_accept(smpl, token);
        
        if (token == eos_token) {
            std::cout << "\n[遇到 EOS]" << std::endl;
            break;
        }
        
        char piece[128];
        int n_chars = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (n_chars > 0) {
            generated.append(piece, n_chars);
            generated_tokens++;
            std::cout << piece << std::flush;
        }
        
        batch = llama_batch_get_one(&token, 1);
    }
    
    auto end_time = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
    
    llama_sampler_free(smpl);
    llama_free(ctx);
    llama_model_free(model);
    
    std::cout << "\n\n========================================" << std::endl;
    std::cout << "生成统计:" << std::endl;
    std::cout << "  - 生成 token 数: " << generated_tokens << std::endl;
    std::cout << "  - 耗时: " << duration.count() << " ms" << std::endl;
    std::cout << "  - 速度: " << (generated_tokens * 1000.0 / duration.count()) << " tokens/s" << std::endl;
    std::cout << "========================================" << std::endl;
    
    if (generated_tokens == 0) {
        std::cerr << "❌ 未生成任何内容" << std::endl;
        return 1;
    }
    
    std::cout << "✅ 测试成功！" << std::endl;
    return 0;
}
