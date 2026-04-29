cmake_minimum_required(VERSION 3.22.1)
project("llama")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

FetchContent_Declare(
    llama.cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG b8951
)

# ❗关键：彻底关闭 Vulkan
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama.cpp)

add_library(
    wrapper
    SHARED
    wrapper.cpp
)

find_library(log-lib log)

target_link_libraries(
    wrapper
    llama
    ${log-lib}
)

target_include_directories(wrapper PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
)