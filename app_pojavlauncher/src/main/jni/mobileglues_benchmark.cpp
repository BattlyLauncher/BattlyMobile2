// MobileGlues benchmark bridge for Battly Mobile.
// Based on MobileGL-Dev/MobileGlues-plugin's LGPL-2.1 query bridge.

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <EGL/egl.h>

#include <mutex>
#include <string>

#define LOG_TAG "MGBenchmark"

namespace {

using EglGetDisplay = EGLDisplay (*)(EGLNativeDisplayType);
using EglInitialize = EGLBoolean (*)(EGLDisplay, EGLint*, EGLint*);
using EglChooseConfig = EGLBoolean (*)(EGLDisplay, const EGLint*, EGLConfig*, EGLint, EGLint*);
using EglCreatePbufferSurface = EGLSurface (*)(EGLDisplay, EGLConfig, const EGLint*);
using EglCreateContext = EGLContext (*)(EGLDisplay, EGLConfig, EGLContext, const EGLint*);
using EglMakeCurrent = EGLBoolean (*)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
using EglDestroySurface = EGLBoolean (*)(EGLDisplay, EGLSurface);
using EglDestroyContext = EGLBoolean (*)(EGLDisplay, EGLContext);
using EglTerminate = EGLBoolean (*)(EGLDisplay);
using BenchRun = const char* (*)(int, int);
using BenchProgress = int (*)();

std::mutex progress_mutex;
BenchProgress progress_function = nullptr;

template <typename T>
T load_symbol(void* library, const char* name) {
    return reinterpret_cast<T>(dlsym(library, name));
}

std::string error_json(const char* message) {
    return std::string("{\"error\":\"") + message + "\"}";
}

std::string run_benchmark(const char* library_path, int start_sections, int max_sections) {
    void* library = dlopen(library_path, RTLD_NOW | RTLD_LOCAL);
    if (!library) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "dlopen failed: %s", dlerror());
        return error_json("failed to load libmobileglues.so");
    }

    EglGetDisplay egl_get_display = load_symbol<EglGetDisplay>(library, "eglGetDisplay");
    EglInitialize egl_initialize = load_symbol<EglInitialize>(library, "eglInitialize");
    EglChooseConfig egl_choose_config = load_symbol<EglChooseConfig>(library, "eglChooseConfig");
    EglCreatePbufferSurface egl_create_surface =
            load_symbol<EglCreatePbufferSurface>(library, "eglCreatePbufferSurface");
    EglCreateContext egl_create_context = load_symbol<EglCreateContext>(library, "eglCreateContext");
    EglMakeCurrent egl_make_current = load_symbol<EglMakeCurrent>(library, "eglMakeCurrent");
    EglDestroySurface egl_destroy_surface = load_symbol<EglDestroySurface>(library, "eglDestroySurface");
    EglDestroyContext egl_destroy_context = load_symbol<EglDestroyContext>(library, "eglDestroyContext");
    EglTerminate egl_terminate = load_symbol<EglTerminate>(library, "eglTerminate");
    BenchRun bench = load_symbol<BenchRun>(library, "mg_multidraw_bench_run");
    BenchProgress progress = load_symbol<BenchProgress>(library, "mg_multidraw_bench_progress");

    if (!egl_get_display || !egl_initialize || !egl_choose_config || !egl_create_surface
            || !egl_create_context || !egl_make_current || !egl_destroy_surface
            || !egl_destroy_context || !egl_terminate || !bench) {
        dlclose(library);
        return error_json("MobileGlues benchmark symbols are missing");
    }

    EGLDisplay display = egl_get_display(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        dlclose(library);
        return error_json("eglGetDisplay failed");
    }
    EGLint major = 0;
    EGLint minor = 0;
    if (!egl_initialize(display, &major, &minor)) {
        dlclose(library);
        return error_json("eglInitialize failed");
    }

    const EGLint config_attributes[] = {
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8,
            EGL_GREEN_SIZE, 8,
            EGL_BLUE_SIZE, 8,
            EGL_ALPHA_SIZE, 8,
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint config_count = 0;
    if (!egl_choose_config(display, config_attributes, &config, 1, &config_count)
            || config_count <= 0) {
        egl_terminate(display);
        dlclose(library);
        return error_json("eglChooseConfig failed");
    }

    const EGLint surface_attributes[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    EGLSurface surface = egl_create_surface(display, config, surface_attributes);
    if (surface == EGL_NO_SURFACE) {
        egl_terminate(display);
        dlclose(library);
        return error_json("eglCreatePbufferSurface failed");
    }

    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = egl_create_context(display, config, EGL_NO_CONTEXT, context_attributes);
    if (context == EGL_NO_CONTEXT) {
        egl_destroy_surface(display, surface);
        egl_terminate(display);
        dlclose(library);
        return error_json("eglCreateContext failed");
    }
    if (!egl_make_current(display, surface, surface, context)) {
        egl_destroy_context(display, context);
        egl_destroy_surface(display, surface);
        egl_terminate(display);
        dlclose(library);
        return error_json("eglMakeCurrent failed");
    }

    {
        std::lock_guard<std::mutex> lock(progress_mutex);
        progress_function = progress;
    }
    const char* raw = bench(start_sections, max_sections);
    std::string result = raw ? raw : error_json("benchmark returned nothing");
    {
        std::lock_guard<std::mutex> lock(progress_mutex);
        progress_function = nullptr;
    }

    egl_make_current(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    egl_destroy_surface(display, surface);
    egl_destroy_context(display, context);
    egl_terminate(display);
    dlclose(library);
    return result;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_utils_MobileGluesBenchmarkNative_runBenchmark(
        JNIEnv* env, jclass, jstring library_path, jstring mg_directory,
        jstring angle_directory, jint start_sections, jint max_sections) {
    const char* library = env->GetStringUTFChars(library_path, nullptr);
    const char* directory = env->GetStringUTFChars(mg_directory, nullptr);
    const char* angle = env->GetStringUTFChars(angle_directory, nullptr);

    setenv("MG_PLUGIN_STATUS", "1", 1);
    unsetenv("MG_COUNT_LAUNCH");
    setenv("MG_DIR_PATH", directory, 1);
    if (angle[0] == '\0') {
        unsetenv("MG_ANGLE_DIR");
    } else {
        setenv("MG_ANGLE_DIR", angle, 1);
    }

    std::string result = run_benchmark(library, start_sections, max_sections);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", result.c_str());

    env->ReleaseStringUTFChars(library_path, library);
    env->ReleaseStringUTFChars(mg_directory, directory);
    env->ReleaseStringUTFChars(angle_directory, angle);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_net_kdt_pojavlaunch_utils_MobileGluesBenchmarkNative_getProgress(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(progress_mutex);
    return progress_function ? progress_function() : -1;
}
