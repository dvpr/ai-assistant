#!/bin/bash

echo "========================================="
echo "构建并测试 llama.cpp Docker 环境"
echo "========================================="

# 构建镜像
docker build -t llama-test-env .

# 运行测试
docker run --rm llama-test-env
