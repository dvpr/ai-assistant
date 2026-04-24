#!/bin/bash

# FTP 配置
FTP_USER="dvpr"
FTP_HOST="192.168.3.51:2121"
# 本地要上传的文件（改成你实际路径）
LOCAL_FILE="app/build/outputs/apk/debug/app-debug.apk"

# 生成时间戳（格式：年月日-时分秒）
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
# 远程文件名 = 原文件名 + 时间戳
REMOTE_FILE="app-debug-${TIMESTAMP}.apk"

case "$1" in
    build)
        docker build -t android-ai .
        ;;
    publish)
        docker run --rm -v "$(pwd)":/workspace -v ./.gradle:/root/.gradle android-ai

        echo "=== 开始上传到 FTP ==="
        echo "远程文件：$REMOTE_FILE"
        
        # 核心：curl 上传 + 自动重命名为带时间戳的文件
        curl -T "$LOCAL_FILE" "ftp://$FTP_HOST/$REMOTE_FILE" --user "$FTP_USER" -sS --show-error

        if [ $? -eq 0 ]; then
            echo -e "\n✅ 上传成功：$REMOTE_FILE"
        else
            echo -e "\n❌ 上传失败"
            exit 1
        fi
        ;;
    *)
        echo "Usage: $0 {build|publish}"
        exit 1
        ;;
esac
