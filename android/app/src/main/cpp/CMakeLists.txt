cmake_minimum_required(VERSION 3.22.1)

project("llama")

# 设置 C++ 标准（llama.cpp 需要 C++17）
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 添加 llama.cpp 作为子项目
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# 创建你的 JNI 封装库
add_library(
    wrapper
    SHARED
    wrapper.cpp
)

# 查找必要的系统库
find_library(log-lib log)

# 链接所有必需的库
target_link_libraries(
    wrapper
    llama           # llama.cpp 核心库
    ${log-lib}      # Android 日志
    # 如果需要，还可以添加：
    # c++_shared    # C++ 标准库（如果出现 std:: 相关链接错误）
)

# 可选：明确指定头文件路径（通常 add_subdirectory 已处理）
target_include_directories(wrapper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
)