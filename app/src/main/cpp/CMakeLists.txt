cmake_minimum_required(VERSION 3.22.1)
project("llama-jni")

set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/../../../../llama.cpp)

if(NOT EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    message(FATAL_ERROR 
        "llama.cpp source not found!\n"
        "Please clone it: cd WeChatAutoReply && git clone https://github.com/ggerganov/llama.cpp"
    )
endif()

# 编译选项
set(BUILD_SHARED_LIBS OFF)
set(GGML_OPENMP OFF)
set(GGML_LLAMAFILE OFF)
set(LLAMA_BUILD_TESTS OFF)
set(LLAMA_BUILD_EXAMPLES OFF)
set(LLAMA_BUILD_SERVER OFF)
set(LLAMA_BUILD_COMMON ON)

# 添加 llama.cpp 子项目
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# 构建 JNI 桥接库
add_library(llama-jni SHARED llama-jni.cpp)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/common
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(llama-jni
    llama
    android
    log
)
