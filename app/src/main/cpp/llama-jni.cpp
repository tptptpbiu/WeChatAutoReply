/**
 * llama.cpp JNI 桥接层
 * 
 * 提供 Kotlin 调用本地 llama.cpp 推理的接口
 * 主要功能：加载模型、生成回复、释放资源
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"
#include "common.h"

#define TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ============================================================
// 全局状态
// ============================================================

struct LlamaContext {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    bool is_loaded = false;
};

static LlamaContext g_llama;

// ============================================================
// 辅助函数
// ============================================================

static std::string jstring_to_string(JNIEnv * env, jstring jstr) {
    if (jstr == nullptr) return "";
    const char * chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

static jstring string_to_jstring(JNIEnv * env, const std::string & str) {
    return env->NewStringUTF(str.c_str());
}

// ============================================================
// JNI 函数
// ============================================================

extern "C" {

/**
 * 初始化 llama 后端
 */
JNIEXPORT void JNICALL
Java_com_example_wechatautoreply_ai_LlamaEngine_nativeInit(
    JNIEnv * env, jobject /* this */
) {
    LOGI("初始化 llama 后端");
    llama_backend_init();
}

/**
 * 加载模型
 * @param modelPath GGUF 模型文件路径
 * @param nThreads 推理线程数
 * @param nCtx 上下文长度
 * @return 是否成功
 */
JNIEXPORT jboolean JNICALL
Java_com_example_wechatautoreply_ai_LlamaEngine_nativeLoadModel(
    JNIEnv * env, jobject /* this */,
    jstring modelPath, jint nThreads, jint nCtx
) {
    // 如果已经加载了，先释放
    if (g_llama.is_loaded) {
        if (g_llama.ctx) {
            llama_free(g_llama.ctx);
            g_llama.ctx = nullptr;
        }
        if (g_llama.model) {
            llama_model_free(g_llama.model);
            g_llama.model = nullptr;
        }
        g_llama.is_loaded = false;
    }

    std::string path = jstring_to_string(env, modelPath);
    LOGI("加载模型: %s", path.c_str());

    // 模型参数
    auto model_params = llama_model_default_params();

    // 加载模型
    g_llama.model = llama_model_load_from_file(path.c_str(), model_params);
    if (!g_llama.model) {
        LOGE("加载模型失败: %s", path.c_str());
        return JNI_FALSE;
    }

    g_llama.vocab = llama_model_get_vocab(g_llama.model);

    // 上下文参数
    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = (int32_t)nCtx;
    ctx_params.n_threads = (int32_t)nThreads;
    ctx_params.n_threads_batch = (int32_t)nThreads;

    // 创建上下文
    g_llama.ctx = llama_init_from_model(g_llama.model, ctx_params);
    if (!g_llama.ctx) {
        LOGE("创建上下文失败");
        llama_model_free(g_llama.model);
        g_llama.model = nullptr;
        return JNI_FALSE;
    }

    g_llama.is_loaded = true;
    LOGI("模型加载成功！");
    return JNI_TRUE;
}

/**
 * 生成回复
 * @param prompt 完整的对话 prompt（已格式化）
 * @param maxTokens 最大生成 token 数
 * @param temperature 温度参数
 * @return 生成的文本
 */
JNIEXPORT jstring JNICALL
Java_com_example_wechatautoreply_ai_LlamaEngine_nativeGenerate(
    JNIEnv * env, jobject /* this */,
    jstring prompt, jint maxTokens, jfloat temperature
) {
    if (!g_llama.is_loaded || !g_llama.ctx || !g_llama.model) {
        LOGE("模型未加载");
        return string_to_jstring(env, "");
    }

    std::string prompt_str = jstring_to_string(env, prompt);
    LOGI("生成回复，prompt 长度: %zu", prompt_str.size());

    // 清空上下文中的 KV 缓存
    llama_memory_t mem = llama_get_memory(g_llama.ctx);
    if (mem) {
        llama_memory_clear(mem, true);
    }

    // Tokenize
    const int n_prompt_max = llama_n_ctx(g_llama.ctx);
    std::vector<llama_token> prompt_tokens(n_prompt_max);

    const int n_prompt_tokens = llama_tokenize(
        g_llama.vocab, prompt_str.c_str(), prompt_str.size(),
        prompt_tokens.data(), prompt_tokens.size(),
        true,   // add_special
        true    // parse_special
    );

    if (n_prompt_tokens < 0) {
        LOGE("Tokenize 失败");
        return string_to_jstring(env, "");
    }
    prompt_tokens.resize(n_prompt_tokens);

    LOGI("Prompt tokens: %d", n_prompt_tokens);

    // 处理 prompt（prefill）
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), n_prompt_tokens);
    if (llama_decode(g_llama.ctx, batch) != 0) {
        LOGE("Decode prompt 失败");
        return string_to_jstring(env, "");
    }

    // 采样器
    auto * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    // 生成 tokens
    std::string result;
    const llama_token eos_token = llama_vocab_eos(g_llama.vocab);
    const int max_gen = (int)maxTokens;

    for (int i = 0; i < max_gen; i++) {
        // 采样下一个 token
        llama_token new_token = llama_sampler_sample(sampler, g_llama.ctx, -1);

        // 检查是否结束
        if (llama_vocab_is_eog(g_llama.vocab, new_token)) {
            LOGI("遇到 EOS，停止生成");
            break;
        }

        // 转换 token 为文本
        char buf[256];
        int n = llama_token_to_piece(g_llama.vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // 检查停止标记（Qwen 的 ChatML 格式）
        if (result.find("<|im_end|>") != std::string::npos) {
            // 去掉停止标记
            size_t pos = result.find("<|im_end|>");
            result = result.substr(0, pos);
            break;
        }

        // 准备下一个 token 的 batch
        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_llama.ctx, next_batch) != 0) {
            LOGE("Decode 失败 at token %d", i);
            break;
        }
    }

    llama_sampler_free(sampler);

    LOGI("生成完成，结果长度: %zu", result.size());
    return string_to_jstring(env, result);
}

/**
 * 检查模型是否已加载
 */
JNIEXPORT jboolean JNICALL
Java_com_example_wechatautoreply_ai_LlamaEngine_nativeIsLoaded(
    JNIEnv * env, jobject /* this */
) {
    return g_llama.is_loaded ? JNI_TRUE : JNI_FALSE;
}

/**
 * 释放模型资源
 */
JNIEXPORT void JNICALL
Java_com_example_wechatautoreply_ai_LlamaEngine_nativeFree(
    JNIEnv * env, jobject /* this */
) {
    LOGI("释放模型资源");
    if (g_llama.ctx) {
        llama_free(g_llama.ctx);
        g_llama.ctx = nullptr;
    }
    if (g_llama.model) {
        llama_model_free(g_llama.model);
        g_llama.model = nullptr;
    }
    g_llama.is_loaded = false;
    llama_backend_free();
}

} // extern "C"
