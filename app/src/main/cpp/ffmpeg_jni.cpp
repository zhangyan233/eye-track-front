#include <jni.h>
#include <string>
#include <android/log.h>
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/opt.h"

#define LOG_TAG "FFmpegJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_example_eye_track.utils_FFmpegJNI_runFFmpegCommand(JNIEnv *env, jobject instance, jobjectArray command) {
    int argc = env->GetArrayLength(command);
    char **argv = new char*[argc];
    for (int i = 0; i < argc; i++) {
        jstring string = (jstring) env->GetObjectArrayElement(command, i);
        const char *rawString = env->GetStringUTFChars(string, 0);
        argv[i] = strdup(rawString);
        env->ReleaseStringUTFChars(string, rawString);
    }

    int result = ffmpeg_main(argc, argv);
    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    delete[] argv;

    return result;
}
