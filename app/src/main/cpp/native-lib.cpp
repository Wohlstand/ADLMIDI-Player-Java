#include <jni.h>
#include <string>
#include "ADLMIDI/adlmidi.h"

#if 1
#undef JNIEXPORT
#undef JNICALL
#define JNIEXPORT extern "C"
#define JNICALL
#endif

#define ADLDEV (ADL_MIDIPlayer*)device

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1errorString(JNIEnv *env, jobject instance)
{
    const char* adlMIDIerr = adl_errorString();
    return env->NewStringUTF(adlMIDIerr);
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_stringFromJNI(JNIEnv *env, jobject /* this */)
{
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setNumCards(JNIEnv *env, jobject instance, jlong device,
                                                       jint numCards) {
    return (jint)adl_setNumCards(ADLDEV, (int)numCards);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1getBanksCount(JNIEnv *env, jobject instance)
{
    return (jint)adl_getBanksCount();
}


JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setNumFourOpsChn(JNIEnv *env, jobject instance,
                                                            jlong device, jint ops4) {
    return (jint)adl_setNumFourOpsChn(ADLDEV, (int)ops4);
}

JNIEXPORT jlong JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1init(JNIEnv *env, jobject instance, jlong sampleRate)
{
    return (jlong)adl_init((long)sampleRate);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setPercMode(JNIEnv *env, jobject instance, jlong device,
                                                       jint percmod) {
    adl_setPercMode(ADLDEV, (int)percmod);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setHVibrato(JNIEnv *env, jobject instance, jlong device,
                                                       jint hvibrato)
{
    adl_setHVibrato(ADLDEV, (int)hvibrato);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setHTremolo(JNIEnv *env, jobject instance, jlong device,
                                                       jint htremo)
{
    adl_setHTremolo(ADLDEV, (int)htremo);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setScaleModulators(JNIEnv *env, jobject instance,
                                                              jlong device, jint smod)
{
    adl_setScaleModulators(ADLDEV, (int)smod);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setLoopEnabled(JNIEnv *env, jobject instance,
                                                          jlong device, jint loopEn)
{
    adl_setLoopEnabled(ADLDEV, (int)loopEn);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1openFile(JNIEnv *env, jobject instance, jlong device,
                                                    jstring file_) {
    const char *file = env->GetStringUTFChars(file_, 0);
    int ret = adl_openFile(ADLDEV, (char*)file);
    env->ReleaseStringUTFChars(file_, file);
    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1openData(JNIEnv *env, jobject instance, jlong device,
                                                    jbyteArray array_) {
    jbyte *array = env->GetByteArrayElements(array_, NULL);
    jsize length =  env->GetArrayLength(array_);
    jint ret = adl_openData(ADLDEV, array, length);
    env->ReleaseByteArrayElements(array_, array, 0);
    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1reset(JNIEnv *env, jobject instance, jlong device) {
    adl_reset(ADLDEV);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1close(JNIEnv *env, jobject instance, jlong device)
{
    adl_close(ADLDEV);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1play(JNIEnv *env, jobject instance, jlong device,
                                                jshortArray buffer_)
{
    jshort *buffer = env->GetShortArrayElements(buffer_, NULL);
    jsize  length =  env->GetArrayLength(buffer_);
    short* outBuff = (short*)buffer;
    jint gotSamples = adl_play(ADLDEV, length, outBuff);
    env->ReleaseShortArrayElements(buffer_, buffer, 0);
    return gotSamples;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1setBank(JNIEnv *env, jobject instance, jlong device,
                                                   jint bank)
{
    return (jint)adl_setBank(ADLDEV, bank);
}

extern const char* const banknames[];

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_Player_adl_1getBankName(JNIEnv *env, jobject instance, jint bank)
{
    return env->NewStringUTF(banknames[(int)bank]);
}