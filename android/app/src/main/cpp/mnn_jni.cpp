/**
 * mnn_jni.cpp
 *
 * JNI bridge between the Kotlin MNNLLMEngine class and the MNN-LLM C++ library.
 *
 * Exposed functions (must match declarations in MNNLLMEngine.kt):
 *   nativeCreate(configPath)         -> jlong handle
 *   nativeChat(handle, messages)     -> jstring
 *   nativeChatWithCallback(handle, messages, callback)
 *   nativeReset(handle)
 *   nativeRelease(handle)
 *
 * The `messages` parameter is an OpenAI-style JSON array:
 *   [{"role":"system","content":"..."}, {"role":"user","content":"..."}, ...]
 *
 * Multi-turn context is maintained by the MNN LLM KV-cache across calls to
 * nativeChatWithCallback/nativeChat.  Call nativeReset to start fresh.
 */

#include <jni.h>
#include <string>
#include <sstream>
#include <streambuf>
#include <vector>
#include <android/log.h>

#include "llm.hpp"   // MNN-LLM header

#define LOG_TAG "MNNJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace MNN::Transformer;

// ── JSON helpers ─────────────────────────────────────────────────────────────

/**
 * Very small JSON string-value extractor.
 * Returns the value of the first key that exactly matches `key` after `startPos`.
 * Handles basic escaping but not nested objects/arrays inside the value.
 */
static std::string jsonGetString(const std::string& json,
                                 const std::string& key,
                                 size_t startPos = 0) {
    // Search for "key":
    std::string needle = "\"" + key + "\"";
    size_t kpos = json.find(needle, startPos);
    if (kpos == std::string::npos) return "";

    size_t colon = json.find(':', kpos + needle.size());
    if (colon == std::string::npos) return "";

    size_t vstart = json.find('"', colon + 1);
    if (vstart == std::string::npos) return "";
    vstart++; // skip opening quote

    std::string value;
    bool escape = false;
    for (size_t i = vstart; i < json.size(); ++i) {
        char c = json[i];
        if (escape) {
            switch (c) {
                case 'n':  value += '\n'; break;
                case 't':  value += '\t'; break;
                case 'r':  value += '\r'; break;
                case '"':  value += '"';  break;
                case '\\': value += '\\'; break;
                default:   value += c;    break;
            }
            escape = false;
        } else if (c == '\\') {
            escape = true;
        } else if (c == '"') {
            break; // end of string value
        } else {
            value += c;
        }
    }
    return value;
}

/**
 * Parse an OpenAI-style messages JSON array and return a list of
 * {role, content} pairs in order.
 */
struct Message { std::string role; std::string content; };

static std::vector<Message> parseMessages(const std::string& json) {
    std::vector<Message> result;
    size_t pos = 0;
    while (true) {
        size_t objStart = json.find('{', pos);
        if (objStart == std::string::npos) break;
        size_t objEnd = json.find('}', objStart);
        if (objEnd == std::string::npos) break;
        std::string obj = json.substr(objStart, objEnd - objStart + 1);
        Message m;
        m.role    = jsonGetString(obj, "role");
        m.content = jsonGetString(obj, "content");
        if (!m.role.empty() || !m.content.empty()) {
            result.push_back(m);
        }
        pos = objEnd + 1;
    }
    return result;
}

// ── Streaming streambuf ───────────────────────────────────────────────────────

/**
 * A std::streambuf implementation that forwards every write to a Java
 * TokenCallback.onToken(String, boolean) call.
 *
 * Word-level buffering: tokens are flushed on space/newline boundaries so
 * each JS callback carries a readable chunk rather than a single character.
 */
class JavaCallbackStreambuf : public std::streambuf {
public:
    JavaCallbackStreambuf(JNIEnv* env, jobject callback, jmethodID mid)
        : env_(env), callback_(callback), mid_(mid) {}

    // Flush any remaining buffered content as a final isDone=false token.
    void flushBuffer() {
        if (!buf_.empty()) {
            sendToken(buf_, false);
            buf_.clear();
        }
    }

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        buf_.append(s, static_cast<size_t>(n));
        // Flush on natural word boundaries to reduce JNI call overhead
        if (buf_.find(' ')  != std::string::npos ||
            buf_.find('\n') != std::string::npos ||
            buf_.size() > 64) {
            sendToken(buf_, false);
            buf_.clear();
        }
        return n;
    }

    int overflow(int c) override {
        if (c != EOF) {
            char ch = static_cast<char>(c);
            xsputn(&ch, 1);
        }
        return c;
    }

private:
    void sendToken(const std::string& token, bool isDone) {
        jstring jtoken = env_->NewStringUTF(token.c_str());
        if (jtoken) {
            env_->CallVoidMethod(callback_, mid_, jtoken,
                                 isDone ? JNI_TRUE : JNI_FALSE);
            env_->DeleteLocalRef(jtoken);
        }
    }

    JNIEnv*     env_;
    jobject     callback_;
    jmethodID   mid_;
    std::string buf_;
};

// ── JNI implementations ───────────────────────────────────────────────────────

extern "C" {

/**
 * Create and initialise a MNN LLM instance.
 * Returns a jlong handle (reinterpret_cast of Llm*), or 0 on failure.
 */
JNIEXPORT jlong JNICALL
Java_com_me_chat_ai_up_admin_mnn_MNNLLMEngine_nativeCreate(
        JNIEnv* env, jobject /*thiz*/, jstring configPath) {
    const char* path = env->GetStringUTFChars(configPath, nullptr);
    std::string configStr(path ? path : "");
    env->ReleaseStringUTFChars(configPath, path);

    LOGI("nativeCreate: %s", configStr.c_str());
    try {
        Llm* llm = Llm::createLLM(configStr);
        if (!llm) {
            LOGE("Llm::createLLM returned null for %s", configStr.c_str());
            return 0L;
        }
        llm->initRuntime();
        LOGI("LLM initialised, handle=%p", static_cast<void*>(llm));
        return reinterpret_cast<jlong>(llm);
    } catch (const std::exception& e) {
        LOGE("nativeCreate exception: %s", e.what());
        return 0L;
    } catch (...) {
        LOGE("nativeCreate unknown exception");
        return 0L;
    }
}

/**
 * Run single-turn inference (blocking).  Returns the complete response string.
 */
JNIEXPORT jstring JNICALL
Java_com_me_chat_ai_up_admin_mnn_MNNLLMEngine_nativeChat(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jstring messages) {
    Llm* llm = reinterpret_cast<Llm*>(handle);
    if (!llm) return env->NewStringUTF("[error: null handle]");

    const char* msg = env->GetStringUTFChars(messages, nullptr);
    std::string msgsJson(msg ? msg : "");
    env->ReleaseStringUTFChars(messages, msg);

    try {
        auto msgs = parseMessages(msgsJson);
        // Feed each message; last user message triggers generation
        std::string result;
        llm->reset();
        for (const auto& m : msgs) {
            if (m.role == "user") {
                result = llm->response(m.content, nullptr);
            }
        }
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception& e) {
        LOGE("nativeChat exception: %s", e.what());
        std::string err = std::string("[error] ") + e.what();
        return env->NewStringUTF(err.c_str());
    } catch (...) {
        LOGE("nativeChat unknown exception");
        return env->NewStringUTF("[error] unknown");
    }
}

/**
 * Run streaming inference.
 *
 * Tokens are delivered via TokenCallback.onToken(String token, boolean isDone).
 * A final call with isDone=true signals the end of generation.
 */
JNIEXPORT void JNICALL
Java_com_me_chat_ai_up_admin_mnn_MNNLLMEngine_nativeChatWithCallback(
        JNIEnv* env, jobject /*thiz*/,
        jlong handle, jstring messages, jobject callback) {
    Llm* llm = reinterpret_cast<Llm*>(handle);
    if (!llm) {
        LOGE("nativeChatWithCallback: null handle");
        return;
    }

    // Resolve onToken method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(
            callbackClass, "onToken", "(Ljava/lang/String;Z)V");
    if (!onToken) {
        LOGE("Could not find TokenCallback.onToken()");
        return;
    }

    const char* msg = env->GetStringUTFChars(messages, nullptr);
    std::string msgsJson(msg ? msg : "");
    env->ReleaseStringUTFChars(messages, msg);

    try {
        auto msgs = parseMessages(msgsJson);

        // Set up streaming streambuf
        JavaCallbackStreambuf buf(env, callback, onToken);
        std::ostream os(&buf);

        // Reset context and replay the full conversation so multi-turn works
        llm->reset();
        for (size_t i = 0; i < msgs.size(); ++i) {
            const auto& m = msgs[i];
            if (m.role != "user") continue;
            bool isLast = true;
            for (size_t j = i + 1; j < msgs.size(); ++j) {
                if (msgs[j].role == "user") { isLast = false; break; }
            }
            if (isLast) {
                // Stream the final user turn
                llm->response(m.content, &os);
                buf.flushBuffer();
            } else {
                // Feed earlier turns silently to build KV cache
                llm->response(m.content, nullptr);
            }
        }

        // Signal completion
        jstring doneStr = env->NewStringUTF("");
        env->CallVoidMethod(callback, onToken, doneStr, JNI_TRUE);
        env->DeleteLocalRef(doneStr);

    } catch (const std::exception& e) {
        LOGE("nativeChatWithCallback exception: %s", e.what());
        std::string errMsg = std::string("[推理错误] ") + e.what();
        jstring jErr = env->NewStringUTF(errMsg.c_str());
        env->CallVoidMethod(callback, onToken, jErr, JNI_TRUE);
        env->DeleteLocalRef(jErr);
    } catch (...) {
        LOGE("nativeChatWithCallback unknown exception");
        jstring jErr = env->NewStringUTF("[推理错误] unknown");
        env->CallVoidMethod(callback, onToken, jErr, JNI_TRUE);
        env->DeleteLocalRef(jErr);
    }
}

/**
 * Reset the KV-cache (clears conversation context).
 */
JNIEXPORT void JNICALL
Java_com_me_chat_ai_up_admin_mnn_MNNLLMEngine_nativeReset(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    Llm* llm = reinterpret_cast<Llm*>(handle);
    if (llm) {
        LOGI("nativeReset");
        llm->reset();
    }
}

/**
 * Release the model and free all native resources.
 */
JNIEXPORT void JNICALL
Java_com_me_chat_ai_up_admin_mnn_MNNLLMEngine_nativeRelease(
        JNIEnv* env, jobject /*thiz*/, jlong handle) {
    Llm* llm = reinterpret_cast<Llm*>(handle);
    if (llm) {
        LOGI("nativeRelease");
        llm->release();
        delete llm;
    }
}

} // extern "C"
