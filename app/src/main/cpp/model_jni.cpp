#include <jni.h>
#include "llama.h"
#include "ggml-backend.h"
#include <algorithm>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

namespace {
using Clock = std::chrono::steady_clock;
std::once_flag backend_once;
std::string utf8(JNIEnv *env, jbyteArray value) {
    const auto length = env->GetArrayLength(value);
    std::string result(length, '\0');
    env->GetByteArrayRegion(value, 0, length, reinterpret_cast<jbyte *>(result.data()));
    return result;
}
bool expired(void *value) { return Clock::now() >= *static_cast<Clock::time_point *>(value); }
std::string format_chat(llama_model *model, const std::string &system, const std::string &user) {
    char architecture[64] = {};
    llama_model_meta_val_str(model, "general.architecture", architecture, sizeof(architecture));
    if (std::string(architecture).find("gemma4") == 0) {
        // Official text-only template with thinking disabled; tokenizer adds BOS.
        return "<|turn>system\n" + system + "<turn|>\n<|turn>user\n" + user + "<turn|>\n<|turn>model\n";
    }
    llama_chat_message messages[] = {{"system", system.c_str()}, {"user", user.c_str()}};
    const char *tmpl = llama_model_chat_template(model, nullptr);
    if (!tmpl) tmpl = "chatml";
    std::vector<char> formatted((system.size() + user.size()) * 2 + 1024);
    int size = llama_chat_apply_template(tmpl, messages, 2, true, formatted.data(), formatted.size());
    if (size < 0) throw std::runtime_error("This model's chat template is unsupported. Choose a recommended model.");
    if (size > static_cast<int>(formatted.size())) {
        formatted.resize(size + 1);
        size = llama_chat_apply_template(tmpl, messages, 2, true, formatted.data(), formatted.size());
    }
    if (size <= 0) throw std::runtime_error("Could not prepare the local conversation.");
    return std::string(formatted.data(), size);
}
// The weights stay resident after the first reply. Re-reading a 1B GGUF from storage on
// every message was the reason an offline answer took so long to appear.
std::mutex inference_mutex;
std::unique_ptr<llama_model, decltype(&llama_model_free)> resident_model(nullptr, llama_model_free);
std::string resident_path;

llama_model *resident(const std::string &path) {
    if (resident_model && resident_path == path) return resident_model.get();
    if (resident_model) {
        resident_model.reset();
        resident_path.clear();
    }
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    model_params.check_tensors = false;
    resident_model.reset(llama_model_load_from_file(path.c_str(), model_params));
    if (!resident_model) throw std::runtime_error("Model could not load. Check available RAM or choose the smaller 1.1B model.");
    resident_path = path;
    return resident_model.get();
}

std::string generate(const std::string &path, const std::string &system, const std::string &user, int max_tokens) {
    std::call_once(backend_once, [] { llama_backend_init(); ggml_backend_load_all(); });
    std::lock_guard<std::mutex> guard(inference_mutex);
    llama_model *model = resident(path);
    const auto *vocab = llama_model_get_vocab(model);
    const auto prompt = format_chat(model, system, user);
    const int count = -llama_tokenize(vocab, prompt.data(), prompt.size(), nullptr, 0, true, true);
    if (count <= 0 || count + max_tokens > 2048) {
        throw std::runtime_error("Message exceeds the local model's context. Please send a shorter question.");
    }
    std::vector<llama_token> tokens(count);
    if (llama_tokenize(vocab, prompt.data(), prompt.size(), tokens.data(), count, true, true) < 0) {
        throw std::runtime_error("Local tokenizer failed. Choose a recommended model.");
    }
    auto deadline = Clock::now() + std::chrono::seconds(120);
    auto params = llama_context_default_params();
    params.n_ctx = 2048;
    params.n_batch = 256;
    params.n_ubatch = 128;
    params.n_threads = std::max(1u, std::min(6u, std::thread::hardware_concurrency()));
    params.n_threads_batch = params.n_threads;
    params.abort_callback = expired;
    params.abort_callback_data = &deadline;
    std::unique_ptr<llama_context, decltype(&llama_free)> ctx(llama_init_from_model(model, params), llama_free);
    if (!ctx) throw std::runtime_error("Not enough free memory for the model context. Close other apps and retry.");
    for (int offset = 0; offset < count; offset += 256) {
        auto batch = llama_batch_get_one(tokens.data() + offset, std::min(256, count - offset));
        if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("Local prompt evaluation stopped. Free some memory and retry.");
    }
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params()), llama_sampler_free);
    if (!sampler) throw std::runtime_error("Could not allocate the local sampler.");
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.1f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.65f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    std::string output;
    for (int i = 0; i < max_tokens; ++i) {
        if (expired(&deadline)) throw std::runtime_error("Local inference timed out. Try a shorter question or the smaller model.");
        llama_token token = llama_sampler_sample(sampler.get(), ctx.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        std::vector<char> piece(256);
        int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (size < 0) {
            piece.resize(-size);
            size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (size < 0) throw std::runtime_error("Could not decode the model's output.");
        output.append(piece.data(), size);
        if (i + 1 < max_tokens) {
            auto batch = llama_batch_get_one(&token, 1);
            if (llama_decode(ctx.get(), batch) != 0) throw std::runtime_error("Local generation stopped. Free some memory and retry.");
        }
    }
    return output;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_assistant_model_LocalModelRuntime_warmUpNative(
        JNIEnv *env, jobject, jstring path) {
    try {
        const char *raw = env->GetStringUTFChars(path, nullptr);
        if (!raw) return;
        std::string model_path(raw);
        env->ReleaseStringUTFChars(path, raw);
        std::call_once(backend_once, [] { llama_backend_init(); ggml_backend_load_all(); });
        std::lock_guard<std::mutex> guard(inference_mutex);
        resident(model_path);
    } catch (...) {
        // A warm-up failure is not an error: the next real reply reports it properly.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_jarvis_assistant_model_LocalModelRuntime_releaseNative(JNIEnv *, jobject) {
    try {
        std::lock_guard<std::mutex> guard(inference_mutex);
        if (resident_model) {
            resident_model.reset();
            resident_path.clear();
        }
    } catch (...) {
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_jarvis_assistant_model_LocalModelRuntime_generateNative(
        JNIEnv *env, jobject, jstring path, jbyteArray system, jbyteArray user, jint max_tokens) {
    try {
        const char *raw = env->GetStringUTFChars(path, nullptr);
        if (!raw) return nullptr;
        std::string model_path(raw);
        env->ReleaseStringUTFChars(path, raw);
        const auto result = generate(model_path, utf8(env, system), utf8(env, user), std::max(1, std::min(256, static_cast<int>(max_tokens))));
        auto bytes = env->NewByteArray(result.size());
        if (bytes) env->SetByteArrayRegion(bytes, 0, result.size(), reinterpret_cast<const jbyte *>(result.data()));
        return bytes;
    } catch (const std::bad_alloc &) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Not enough free RAM. Close other apps or use the 1.1B model.");
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
    } catch (...) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "The offline engine failed. Reopen Offline AI Models and retry setup.");
    }
    return nullptr;
}