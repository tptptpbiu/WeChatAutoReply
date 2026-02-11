cmake_minimum_required(VERSION 3.22.1)
project("llama-jni")

# ============================================================
# llama.cpp 源码路径
# 需要先克隆 llama.cpp 到项目根目录:
#   cd WeChatAutoReply && git clone https://github.com/ggerganov/llama.cpp
# ============================================================
set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)

# 检查 llama.cpp 是否存在
if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR 
        "llama.cpp source not found!\n"
        "Please clone it: cd WeChatAutoReply && git clone https://github.com/ggerganov/llama.cpp"
    )
endif()

# 编译选项
set(BUILD_SHARED_LIBS OFF)
set(GGML_OPENMP OFF)       # 手机上关闭 OpenMP
set(GGML_LLAMAFILE OFF)    # 不需要
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)

# 添加 llama.cpp 子项目
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# 构建 JNI 桥接库
add_library(llama-jni SHARED llama-jni.cpp)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
)

target_link_libraries(llama-jni
    llama
    common
    android
    log
)
