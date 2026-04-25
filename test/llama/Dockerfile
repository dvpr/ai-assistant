FROM ubuntu:22.04

RUN apt-get update && apt-get install -y \
    wget \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# 下载模型
RUN wget -O /workspace/tinyllama.gguf \
    "https://hf-mirror.com/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q2_K.gguf"

# 下载预编译的 llama.cpp (Linux x86_64)
RUN wget -O /workspace/llama-cli \
    "https://github.com/ggerganov/llama.cpp/releases/download/b3681/llama-b3681-bin-ubuntu-x64.zip" && \
    apt-get install -y unzip && \
    unzip /workspace/llama-cli -d /workspace/ && \
    chmod +x /workspace/llama-cli && \
    rm /workspace/llama-cli

CMD ["/workspace/llama-cli", "-m", "/workspace/tinyllama.gguf", "-p", "Hello", "-n", "32"]