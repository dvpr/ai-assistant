#include <jni.h>
#include <string>
#include <fstream>
#include <iostream>
#include "llama.h"  // 引入 llama.cpp 的头文件

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_llama_MainActivity_infer(JNIEnv* env, jobject, jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);

    // 获取 Java 层传来的文件路径
    jclass contextClass = env->GetObjectClass(env);  // 获取上下文类
    jmethodID methodID = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
    jobject fileDir = env->CallObjectMethod(env, methodID);  // 调用 getFilesDir() 方法
    jclass fileClass = env->GetObjectClass(fileDir);
    jmethodID getPathMethod = env->GetMethodID(fileClass, "getPath", "()Ljava/lang/String;");
    jstring pathString = (jstring)env->CallObjectMethod(fileDir, getPathMethod);  // 获取文件路径

    // 获取文件路径并拼接模型文件路径
    const char* path = env->GetStringUTFChars(pathString, nullptr);
    std::string modelPath = std::string(path) + "/gte-small-q8_0.gguf";  // 拼接完整的路径

    // 加载模型
    llama::LlamaModel model;
    if (!model.load(modelPath.c_str())) {
        return env->NewStringUTF("Error loading model");
    }

    // 执行推理
    std::string result = model.infer(inputStr);

    // 释放内存并返回结果
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(result.c_str());
}