#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <lame.h>

#define LAME_NATIVE_INVALID_BUFFER -100

static lame_global_flags* lame_from_handle(jlong handle) {
    return (lame_global_flags*)(intptr_t)handle;
}

JNIEXPORT jlong JNICALL Java_com_drunkbatya_vunder_core_audio_LameNative_create(
    JNIEnv* env,
    jclass clazz,
    jint sample_rate,
    jint channels) {
    (void)env;
    (void)clazz;
    lame_global_flags* flags = lame_init();
    if(flags == NULL) return 0;

    bool success = false;
    do {
        if(lame_set_in_samplerate(flags, sample_rate) < 0) break;
        if(lame_set_num_channels(flags, channels) < 0) break;
        if(lame_set_VBR(flags, vbr_mtrh) < 0) break;
        if(lame_set_VBR_quality(flags, 0.0f) < 0) break;
        if(lame_set_quality(flags, 0) < 0) break;
        if(lame_init_params(flags) < 0) break;
        success = true;
    } while(0);

    if(!success) {
        lame_close(flags);
        return 0;
    }
    return (jlong)(intptr_t)flags;
}

JNIEXPORT jint JNICALL Java_com_drunkbatya_vunder_core_audio_LameNative_encode(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jobject pcm,
    jint frame_count,
    jobject mp3) {
    (void)clazz;
    lame_global_flags* flags = lame_from_handle(handle);
    short* samples = (*env)->GetDirectBufferAddress(env, pcm);
    unsigned char* output = (*env)->GetDirectBufferAddress(env, mp3);
    jlong output_capacity = (*env)->GetDirectBufferCapacity(env, mp3);
    if(samples == NULL || output == NULL || output_capacity <= 0) return LAME_NATIVE_INVALID_BUFFER;

    if(lame_get_num_channels(flags) == 1) {
        return lame_encode_buffer(flags, samples, NULL, frame_count, output, (int)output_capacity);
    }
    return lame_encode_buffer_interleaved(flags, samples, frame_count, output, (int)output_capacity);
}

JNIEXPORT jint JNICALL Java_com_drunkbatya_vunder_core_audio_LameNative_flush(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jobject mp3) {
    (void)clazz;
    unsigned char* output = (*env)->GetDirectBufferAddress(env, mp3);
    jlong output_capacity = (*env)->GetDirectBufferCapacity(env, mp3);
    if(output == NULL || output_capacity <= 0) return LAME_NATIVE_INVALID_BUFFER;
    return lame_encode_flush(lame_from_handle(handle), output, (int)output_capacity);
}

JNIEXPORT jint JNICALL Java_com_drunkbatya_vunder_core_audio_LameNative_lameTag(
    JNIEnv* env,
    jclass clazz,
    jlong handle,
    jobject mp3) {
    (void)clazz;
    unsigned char* output = (*env)->GetDirectBufferAddress(env, mp3);
    jlong output_capacity = (*env)->GetDirectBufferCapacity(env, mp3);
    if(output == NULL || output_capacity <= 0) return LAME_NATIVE_INVALID_BUFFER;

    size_t tag_size = lame_get_lametag_frame(lame_from_handle(handle), output, (size_t)output_capacity);
    if(tag_size > (size_t)output_capacity) return LAME_NATIVE_INVALID_BUFFER;
    return (jint)tag_size;
}

JNIEXPORT void JNICALL Java_com_drunkbatya_vunder_core_audio_LameNative_close(
    JNIEnv* env,
    jclass clazz,
    jlong handle) {
    (void)env;
    (void)clazz;
    lame_close(lame_from_handle(handle));
}
