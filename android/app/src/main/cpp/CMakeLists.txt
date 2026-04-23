cmake_minimum_required(VERSION 3.22.1)

project("llama")

add_library(
    wrapper          # ✔ 名字要和 Java 一致
    SHARED
    wrapper.cpp      # ✔ 文件名要对
)

find_library(
    log-lib
    log
)

target_link_libraries(
    wrapper
    ${log-lib}
)