#include <jni.h>
#include <pthread.h>
#include <cassert>
#include <memory.h>
#include <adlmidi.h>
#include <string>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

pthread_mutex_t g_lock;
bool mutex_created = false;

typedef int16_t sample_t;

static ADLMIDI_AudioFormat g_audioFormat
{
    ADLMIDI_SampleType_S16,
    sizeof(sample_t),
    sizeof(sample_t) * 2
};

typedef int (*AndroidAudioCallback)(sample_t *buffer, int num_samples);
bool OpenSLWrap_Init(AndroidAudioCallback cb);
void OpenSLWrap_Shutdown();

#if 1
#undef JNIEXPORT
#undef JNICALL
#define JNIEXPORT extern "C"
#define JNICALL
#endif

/************************************************************************************************
 * Minimal OpenSL ES wrapper implementation got from https://gist.github.com/hrydgard/3072540
 ************************************************************************************************/
/*
 * Was been modified:
 * - Added support of dynamic size per buffer chunk (allow callback return custom buffer size)
 * - Added posix mutexes to prevent rases on play/stop
 * - First chunk keep zeroed to don't take choppy glitch on begin
 */

// This is kinda ugly, but for simplicity I've left these as globals just like in the sample,
// as there's not really any use case for this where we have multiple audio devices yet.
// engine interfaces
static SLObjectItf  engineObject;
static SLEngineItf  engineEngine;
static SLObjectItf  outputMixObject;

// buffer queue player interfaces
static SLObjectItf  bqPlayerObject = nullptr;
static SLPlayItf    bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf    bqPlayerBufferQueue;
static SLMuteSoloItf                    bqPlayerMuteSolo;
static SLVolumeItf                      bqPlayerVolume;
#define BUFFER_SIZE 20480
#define BUFFER_SIZE_IN_SAMPLES (BUFFER_SIZE / 2)

// Double buffering.
static int      bufferLen[2] = {0, 0};
static sample_t buffer[2][BUFFER_SIZE_IN_SAMPLES];
static size_t   curBuffer = 0;
static AndroidAudioCallback audioCallback;

static double   g_gaining = 2.0;

ADL_MIDIPlayer* playingDevice = nullptr;

// This callback handler is called every time a buffer finishes playing.
// The documentation available is very unclear about how to best manage buffers.
// I've chosen to this approach: Instantly enqueue a buffer that was rendered to the last time,
// and then render the next. Hopefully it's okay to spend time in this callback after having enqueued.
static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
    assert(bq == bqPlayerBufferQueue);
    assert(nullptr == context);
    pthread_mutex_lock(&g_lock);
    sample_t *nextBuffer = buffer[curBuffer];
    int nextSize = bufferLen[curBuffer];
    if(nextSize > 0)
    {
        SLresult result;
        result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer,
                                                 static_cast<SLuint32>(nextSize * 2));
        // Comment from sample code:
        // the most likely other result is SL_RESULT_BUFFER_INSUFFICIENT,
        // which for this code example would indicate a programming error
        assert(SL_RESULT_SUCCESS == result);
        curBuffer ^= 1u;  // Switch buffer
    }
    // Render to the fresh buffer
    bufferLen[curBuffer] = audioCallback(buffer[curBuffer], BUFFER_SIZE_IN_SAMPLES);
    pthread_mutex_unlock(&g_lock);
}

// create the engine and output mix objects
bool OpenSLWrap_Init(AndroidAudioCallback cb)
{
    audioCallback = cb;
    SLresult result;

    memset(buffer, 0, BUFFER_SIZE * sizeof(sample_t));
    bufferLen[0] = BUFFER_SIZE_IN_SAMPLES;
    bufferLen[1] = BUFFER_SIZE_IN_SAMPLES;

    // create engine
    result = slCreateEngine(&engineObject, 0, nullptr, 0, nullptr, nullptr);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, nullptr, nullptr);
    assert(SL_RESULT_SUCCESS == result);
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);

    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};

    /* for Android 21+*/
//    SLAndroidDataFormat_PCM_EX format_pcm;
//    format_pcm.formatType = SL_ANDROID_DATAFORMAT_PCM_EX;
//    format_pcm.numChannels = 2;
//    format_pcm.sampleRate = SL_SAMPLINGRATE_44_1;
//    format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_32;
//    format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
//    format_pcm.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
//    format_pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
//    format_pcm.representation = SL_ANDROID_PCM_REPRESENTATION_FLOAT;

    /* for Android <21 */
    SLDataFormat_PCM format_pcm;
    format_pcm.formatType = SL_DATAFORMAT_PCM;
    format_pcm.numChannels = 2;
    format_pcm.samplesPerSec = SL_SAMPLINGRATE_44_1;
    format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
    format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
    format_pcm.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    format_pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;

    SLDataSource audioSrc = {&loc_bufq, &format_pcm};

    // configure audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, nullptr};

    // create audio player
    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 2, ids, req);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                             &bqPlayerBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, nullptr);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    assert(SL_RESULT_SUCCESS == result);

    // Render and enqueue a first buffer. (or should we just play the buffer empty?)
    curBuffer = 0;//Just pass silence (this frame is always produces chopping, but next are fine)
    //bufferLen[curBuffer] = audioCallback(buffer[curBuffer], BUFFER_SIZE_IN_SAMPLES);
    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[curBuffer], sizeof(buffer[curBuffer]));
    if (SL_RESULT_SUCCESS != result) {
        return false;
    }
    curBuffer ^= 1u;
    return true;
}

//shut down the native audio system
void OpenSLWrap_Shutdown()
{
    pthread_mutex_lock(&g_lock);
    SLresult result;
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
    assert(SL_RESULT_SUCCESS == result);

    memset(buffer, 0, BUFFER_SIZE * sizeof(sample_t));
    bufferLen[0] = BUFFER_SIZE_IN_SAMPLES;
    bufferLen[1] = BUFFER_SIZE_IN_SAMPLES;

    if (bqPlayerObject != nullptr) {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = nullptr;
        bqPlayerPlay = nullptr;
        bqPlayerBufferQueue = nullptr;
        bqPlayerMuteSolo = nullptr;
        bqPlayerVolume = nullptr;
    }
    if (outputMixObject != nullptr) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
    }
    if (engineObject != nullptr) {
        (*engineObject)->Destroy(engineObject);
        engineObject = nullptr;
        engineEngine = nullptr;
    }
    pthread_mutex_unlock(&g_lock);
}

/************************************************************************************************
 ********************** Minimal OpenSL ES wrapper implementation END ****************************
 ************************************************************************************************/

int audioCallbackFunction(sample_t *buffer, int num_samples)
{
    ADL_UInt8 *buff = (ADL_UInt8*)buffer;
    int ret = adl_playFormat(playingDevice, num_samples,
            buff, buff + g_audioFormat.containerSize,
            &g_audioFormat);

    if((g_gaining > 0.1) && (g_gaining != 1.0))
    {
        for(size_t i = 0; i < num_samples; i++)
        {
            buffer[i] = static_cast<sample_t>(static_cast<double>(buffer[i]) * g_gaining);
        }
    }

    return ret;
}

void infiniteLoopStream(ADL_MIDIPlayer* device)
{
    if(mutex_created) {
        assert(pthread_mutex_init(&g_lock, nullptr) == 0);
        mutex_created=true;
    }
    playingDevice = device;
    pthread_mutex_lock(&g_lock);
    OpenSLWrap_Init(audioCallbackFunction);
    pthread_mutex_unlock(&g_lock);
}




#define ADLDEV (ADL_MIDIPlayer*)device

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_startPlaying(JNIEnv *env, jobject /*instance*/, jlong device)
{
    infiniteLoopStream(ADLDEV);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_stopPlaying(JNIEnv *env, jobject /*instance*/)
{
    OpenSLWrap_Shutdown();
}


JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1errorString(JNIEnv *env, jobject /*instance*/)
{
    pthread_mutex_lock(&g_lock);
    const char* adlMIDIerr = adl_errorString();
    pthread_mutex_unlock(&g_lock);
    return env->NewStringUTF(adlMIDIerr);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_setGaining(JNIEnv *env, jobject /*instance*/, jdouble gaining)
{
    pthread_mutex_lock(&g_lock);
    g_gaining = (double)gaining;
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1errorInfo(JNIEnv *env, jobject /*instance*/, jlong device)
{
    pthread_mutex_lock(&g_lock);
    const char* adlMIDIerr = adl_errorInfo(ADLDEV);
    pthread_mutex_unlock(&g_lock);
    return env->NewStringUTF(adlMIDIerr);
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_stringFromJNI(JNIEnv *env, jobject /* this */)
{
    std::string hello = "OPL3 Emulator is ready";
    return env->NewStringUTF(hello.c_str());
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setNumChips(JNIEnv *env, jobject /*instance*/, jlong device,
                                                       jint numCards) {
    pthread_mutex_lock(&g_lock);
    jint ret = (jint)adl_setNumChips(ADLDEV, (int)numCards);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1getBanksCount(JNIEnv *env, jobject /*instance*/)
{
    return (jint)adl_getBanksCount();
}


JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setNumFourOpsChn(JNIEnv *env, jobject /*instance*/,
                                                            jlong device, jint ops4) {
    pthread_mutex_lock(&g_lock);
    jint ret = (jint)adl_setNumFourOpsChn(ADLDEV, (int)ops4);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jlong JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1init(JNIEnv *env, jobject /*instance*/, jlong sampleRate)
{
    if(mutex_created) {
        assert(pthread_mutex_init(&g_lock, nullptr) == 0);
        mutex_created=true;
    }
    struct ADL_MIDIPlayer *p = adl_init((long)sampleRate);
    if(p)
        adl_switchEmulator(p, ADLMIDI_EMU_DOSBOX);
    return (jlong)p;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setPercMode(JNIEnv *env, jobject /*instance*/, jlong device,
                                                       jint percmod)
{
    pthread_mutex_lock(&g_lock);
    adl_setPercMode(ADLDEV, (int)percmod);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setHVibrato(JNIEnv *env, jobject /*instance*/, jlong device,
                                                       jint hvibrato)
{
    pthread_mutex_lock(&g_lock);
    adl_setHVibrato(ADLDEV, (int)hvibrato);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setHTremolo(JNIEnv *env, jobject /*instance*/, jlong device,
                                                       jint htremo)
{
    pthread_mutex_lock(&g_lock);
    adl_setHTremolo(ADLDEV, (int)htremo);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setScaleModulators(JNIEnv *env, jobject /*instance*/,
                                                              jlong device, jint smod)
{
    pthread_mutex_lock(&g_lock);
    adl_setScaleModulators(ADLDEV, (int)smod);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setLoopEnabled(JNIEnv *env, jobject /*instance*/,
                                                          jlong device, jint loopEn)
{
    pthread_mutex_lock(&g_lock);
    adl_setLoopEnabled(ADLDEV, (int)loopEn);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openBankFile(JNIEnv *env, jobject /*instance*/, jlong device,
                                                        jstring file_) {
    pthread_mutex_lock(&g_lock);
    const char *file = env->GetStringUTFChars(file_, 0);
    jint ret = adl_openBankFile(ADLDEV, (char*)file);
    env->ReleaseStringUTFChars(file_, file);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openFile(JNIEnv *env, jobject /*instance*/, jlong device,
                                                    jstring file_) {
    pthread_mutex_lock(&g_lock);
    const char *file = env->GetStringUTFChars(file_, 0);
    int ret = adl_openFile(ADLDEV, (char*)file);
    env->ReleaseStringUTFChars(file_, file);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openData(JNIEnv *env, jobject /*instance*/, jlong device,
                                                    jbyteArray array_) {
    pthread_mutex_lock(&g_lock);
    jbyte *array = env->GetByteArrayElements(array_, nullptr);
    jsize length =  env->GetArrayLength(array_);
    jint ret = adl_openData(ADLDEV, array, static_cast<unsigned long>(length));
    env->ReleaseByteArrayElements(array_, array, 0);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1reset(JNIEnv *env, jobject /*instance*/, jlong device) {
    pthread_mutex_lock(&g_lock);
    adl_reset(ADLDEV);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1close(JNIEnv *env, jobject /*instance*/, jlong device)
{
    pthread_mutex_lock(&g_lock);
    adl_close(ADLDEV);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1play(JNIEnv *env, jobject /*instance*/, jlong device,
                                                jshortArray buffer_)
{
    pthread_mutex_lock(&g_lock);
    jshort *buffer = env->GetShortArrayElements(buffer_, nullptr);
    jsize  length =  env->GetArrayLength(buffer_);
    short* outBuff = reinterpret_cast<short*>(buffer);
    jint gotSamples = adl_play(ADLDEV, length, outBuff);
    env->ReleaseShortArrayElements(buffer_, buffer, 0);
    pthread_mutex_unlock(&g_lock);
    return gotSamples;
}


JNIEXPORT jdouble JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1positionTell(JNIEnv *env, jobject /*instance*/, jlong device)
{
    pthread_mutex_lock(&g_lock);
    jdouble ret = static_cast<jdouble>(adl_positionTell(ADLDEV));
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jdouble JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1totalTimeLength(JNIEnv *env, jobject /*instance*/, jlong device)
{
    pthread_mutex_lock(&g_lock);
    jdouble ret = static_cast<jdouble>(adl_totalTimeLength(ADLDEV));
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1positionSeek(JNIEnv *env, jobject /*instance*/, jlong device,
                                                               jdouble seconds)
{
    pthread_mutex_lock(&g_lock);
    adl_positionSeek(ADLDEV, (double)seconds);
    pthread_mutex_unlock(&g_lock);
}



JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setBank(JNIEnv *env, jobject /*instance*/, jlong device,
                                                   jint bank)
{
    pthread_mutex_lock(&g_lock);
    jint ret = static_cast<jint>(adl_setBank(ADLDEV, bank));
    pthread_mutex_unlock(&g_lock);
    return ret;
}

extern const char* const banknames[];

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1getBankName(JNIEnv *env, jobject /*instance*/, jint bank)
{
    return env->NewStringUTF(banknames[(int)bank]);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setVolumeRangeModel(JNIEnv *env, jobject /*instance*/,
                                                               jlong device, jint volumeModel) {
    pthread_mutex_lock(&g_lock);
    adl_setVolumeRangeModel(ADLDEV, (int)volumeModel);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setRunAtPcmRate(JNIEnv *env, jobject /*instance*/,
                                                           jlong device, jint enabled)
{
    pthread_mutex_lock(&g_lock);
    jint ret = (jint)adl_setRunAtPcmRate(ADLDEV, (int)enabled);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setSoftPanEnabled(JNIEnv *env, jobject /*instance*/,
                                                                  jlong device, jint enabled)
{
    pthread_mutex_lock(&g_lock);
    adl_setSoftPanEnabled(ADLDEV, (int)enabled);
    pthread_mutex_unlock(&g_lock);
}
