cmake_minimum_required(VERSION 3.22.1)

project("llama")

# 设置 C++ 标准（llama.cpp 需要 C++17）
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 引入 FetchContent 模块
include(FetchContent)

# 声明从 GitHub 拉取 llama.cpp
FetchContent_Declare(
    llama.cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    # 建议使用固定的 commit hash 或 tag，避免因上游更新导致意外问题
    # 例如：GIT_TAG b1217  (某个稳定版本)
    GIT_TAG b8913  # 推荐使用具体的 release tag，保证构建的稳定性
)

# 使内容可用，会自动执行 clone 和构建
FetchContent_MakeAvailable(llama.cpp)

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
    llama          # llama.cpp 构建出的核心库（名称由上游定义）
    ${log-lib}     # Android 日志
    # 如果需要 C++ 标准库的显式链接（某些环境下可能已自动包含）：
    # c++_shared
)

# 包含必要的头文件路径
# llama_SOURCE_DIR 是由 FetchContent_MakeAvailable 自动定义的变量，
# 指向拉取后的源码根目录
target_include_directories(wrapper PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
)