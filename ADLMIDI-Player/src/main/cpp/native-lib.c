#include <jni.h>
#include <pthread.h>
#include <assert.h>
#include <memory.h>
#include <stdlib.h>
#include <adlmidi.h>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include <sys/system_properties.h>

static pthread_mutex_t g_lock;
static jboolean mutex_created = JNI_FALSE;

typedef int8_t sample_t;

static struct ADLMIDI_AudioFormat g_audioFormat =
{
    ADLMIDI_SampleType_S16,
    sizeof(int16_t),
    sizeof(int16_t) * 2
};

typedef int (*AndroidAudioCallback)(sample_t *buffer, int num_samples);
static bool OpenSLWrap_Init(AndroidAudioCallback cb);
static void OpenSLWrap_Shutdown();


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
static SLObjectItf  bqPlayerObject = NULL;
static SLPlayItf    bqPlayerPlay;
static SLAndroidSimpleBufferQueueItf    bqPlayerBufferQueue;
static SLVolumeItf                      bqPlayerVolume;
#define BUFFER_SIZE 20480
#define BUFFER_SIZE_IN_SAMPLES (BUFFER_SIZE / 2)

// Double buffering.
static int      bufferLen[2] = {0, 0};
static sample_t buffer[2][BUFFER_SIZE * 4];
static size_t   curBuffer = 0;
static AndroidAudioCallback audioCallback;

static double   g_gaining = 2.0;

static struct ADL_MIDIPlayer* playingDevice = NULL;

// This callback handler is called every time a buffer finishes playing.
// The documentation available is very unclear about how to best manage buffers.
// I've chosen to this approach: Instantly enqueue a buffer that was rendered to the last time,
// and then render the next. Hopefully it's okay to spend time in this callback after having enqueued.
static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context)
{
    int nextSize;
    sample_t *nextBuffer;
    SLresult result;

    assert(bq == bqPlayerBufferQueue);
    assert(NULL == context);

    pthread_mutex_lock(&g_lock);
    nextBuffer = buffer[curBuffer];
    nextSize = bufferLen[curBuffer];
    if(nextSize > 0)
    {
        result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, nextBuffer,
                                                 (SLuint32)(nextSize * g_audioFormat.containerSize));
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
    long sdk_ver = 0;
    char sdk_ver_str[92] = {'\0'};
    SLresult result;

    SLDataLocator_AndroidSimpleBufferQueue locBufQ = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2};

    SLDataFormat_PCM format_pcm;
    SLAndroidDataFormat_PCM_EX format_pcm_ex;
    SLDataSource audioSrc;

    const SLInterfaceID ids[2] = {SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
    const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};

    SLDataLocator_OutputMix loc_outMix;
    SLDataSink audioSnk;


    if(__system_property_get("ro.build.version.sdk", sdk_ver_str))
    {
        sdk_ver = strtol(sdk_ver_str, NULL, 10);
    }

    if(sdk_ver >= 21)
    {
        g_audioFormat.type = ADLMIDI_SampleType_F32;
        g_audioFormat.containerSize = sizeof(float);
        g_audioFormat.sampleOffset = sizeof(float) * 2;
    }

    audioCallback = cb;

    memset(buffer, 0, BUFFER_SIZE * g_audioFormat.sampleOffset);
    bufferLen[0] = BUFFER_SIZE_IN_SAMPLES;
    bufferLen[1] = BUFFER_SIZE_IN_SAMPLES;

    // create engine
    result = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
    assert(SL_RESULT_SUCCESS == result);
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, NULL, NULL);
    assert(SL_RESULT_SUCCESS == result);
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);

    if(sdk_ver >= 21)
    {
        /* for Android 21+*/
        format_pcm_ex.formatType = SL_ANDROID_DATAFORMAT_PCM_EX;
        format_pcm_ex.numChannels = 2;
        format_pcm_ex.sampleRate = SL_SAMPLINGRATE_44_1;
        format_pcm_ex.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_32;
        format_pcm_ex.containerSize = SL_PCMSAMPLEFORMAT_FIXED_32;
        format_pcm_ex.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
        format_pcm_ex.endianness = SL_BYTEORDER_LITTLEENDIAN;
        format_pcm_ex.representation = SL_ANDROID_PCM_REPRESENTATION_FLOAT;
        audioSrc.pLocator = &locBufQ;
        audioSrc.pFormat = &format_pcm_ex;
    }
    else
    {
        /* for Android <21 */
        format_pcm.formatType = SL_DATAFORMAT_PCM;
        format_pcm.numChannels = 2;
        format_pcm.samplesPerSec = SL_SAMPLINGRATE_44_1;
        format_pcm.bitsPerSample = SL_PCMSAMPLEFORMAT_FIXED_16;
        format_pcm.containerSize = SL_PCMSAMPLEFORMAT_FIXED_16;
        format_pcm.channelMask = SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
        format_pcm.endianness = SL_BYTEORDER_LITTLEENDIAN;
        audioSrc.pLocator = &locBufQ;
        audioSrc.pFormat = &format_pcm;
    }

    // configure audio sink
    loc_outMix.locatorType = SL_DATALOCATOR_OUTPUTMIX;
    loc_outMix.outputMix = outputMixObject;
    audioSnk.pLocator = &loc_outMix;
    audioSnk.pFormat = NULL;

    // create audio player

    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &bqPlayerObject, &audioSrc, &audioSnk, 2, ids, req);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->Realize(bqPlayerObject, SL_BOOLEAN_FALSE);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_PLAY, &bqPlayerPlay);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_BUFFERQUEUE,
                                             &bqPlayerBufferQueue);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerBufferQueue)->RegisterCallback(bqPlayerBufferQueue, bqPlayerCallback, NULL);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerObject)->GetInterface(bqPlayerObject, SL_IID_VOLUME, &bqPlayerVolume);
    assert(SL_RESULT_SUCCESS == result);
    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_PLAYING);
    assert(SL_RESULT_SUCCESS == result);

    // Render and enqueue a first buffer. (or should we just play the buffer empty?)
    curBuffer = 0;//Just pass silence (this frame is always produces chopping, but next are fine)
    //bufferLen[curBuffer] = audioCallback(buffer[curBuffer], BUFFER_SIZE_IN_SAMPLES);
    result = (*bqPlayerBufferQueue)->Enqueue(bqPlayerBufferQueue, buffer[curBuffer], sizeof(buffer[curBuffer]));

    if(SL_RESULT_SUCCESS != result)
        return false;

    curBuffer ^= 1u;
    return true;
}

//shut down the native audio system
void OpenSLWrap_Shutdown()
{
    SLresult result;

    result = (*bqPlayerPlay)->SetPlayState(bqPlayerPlay, SL_PLAYSTATE_STOPPED);
    assert(SL_RESULT_SUCCESS == result);

    memset(buffer, 0, BUFFER_SIZE * g_audioFormat.sampleOffset);
    bufferLen[0] = BUFFER_SIZE_IN_SAMPLES;
    bufferLen[1] = BUFFER_SIZE_IN_SAMPLES;

    if (bqPlayerObject != NULL)
    {
        (*bqPlayerObject)->Destroy(bqPlayerObject);
        bqPlayerObject = NULL;
        bqPlayerPlay = NULL;
        bqPlayerBufferQueue = NULL;
        bqPlayerVolume = NULL;
    }

    if (outputMixObject != NULL)
    {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
    }

    if (engineObject != NULL)
    {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }
}

/************************************************************************************************
 ********************** Minimal OpenSL ES wrapper implementation END ****************************
 ************************************************************************************************/

int audioCallbackFunction(sample_t *output, int num_samples)
{
    ADL_UInt8 *buff;
    int ret;
    size_t i;

    buff = (ADL_UInt8*)(output);

    ret = adl_playFormat(playingDevice, num_samples,
                         buff, buff + g_audioFormat.containerSize,
                         &g_audioFormat);

    if((g_gaining > 0.1) && (g_gaining != 1.0))
    {
        if(g_audioFormat.type == ADLMIDI_SampleType_F32)
        {
            float *bu = (float*)(output);
            for(i = 0; i < num_samples; i++)
            {
                *bu *= (float)g_gaining;
                bu++;
            }
        }
        else
        {
            int16_t *bu = (int16_t *)(output);
            for(i = 0; i < num_samples; i++)
            {
                *bu = (int16_t)((double)(*bu) * g_gaining);
                bu++;
            }
        }
    }

    return ret;
}

void infiniteLoopStream(struct ADL_MIDIPlayer* device)
{
    if(mutex_created)
    {
        assert(pthread_mutex_init(&g_lock, NULL) == 0);
        mutex_created = true;
    }

    pthread_mutex_lock(&g_lock);
    playingDevice = device;
    OpenSLWrap_Init(audioCallbackFunction);
    pthread_mutex_unlock(&g_lock);
}




#define ADLDEV (struct ADL_MIDIPlayer*)device

JNIEXPORT void JNICALL Java_ru_wohlsoft_adlmidiplayer_PlayerService_startPlaying(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    (void)env; (void)instance;
    infiniteLoopStream(ADLDEV);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_stopPlaying(
    JNIEnv *env,
    jclass instance
)
{
    (void)env; (void)instance;
    OpenSLWrap_Shutdown();
}


JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1errorString(
    JNIEnv *env,
    jclass instance
)
{
    const char* adlMidiErr;
    jstring ret;

    (void)instance;

    pthread_mutex_lock(&g_lock);
    adlMidiErr = adl_errorString();
    ret = (*env)->NewStringUTF(env, adlMidiErr);
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_setGaining(
    JNIEnv *env,
    jclass instance,
    jdouble gaining
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    g_gaining = (double)gaining;
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1errorInfo(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    const char* adlMidiErr;
    jstring ret;

    (void)instance;

    pthread_mutex_lock(&g_lock);
    adlMidiErr = adl_errorInfo(ADLDEV);
    ret = (*env)->NewStringUTF(env, adlMidiErr);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_stringFromJNI(JNIEnv *env, jclass clazz)
{
    (void)clazz;
    return (*env)->NewStringUTF(env, "OPL3 Emulator is ready");
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setEmulator(
    JNIEnv *env,
    jclass type,
    jlong device,
    jint emulator
)
{
    jint ret;
    (void)type; (void)env;

    pthread_mutex_lock(&g_lock);
    ret = (jint)adl_switchEmulator(ADLDEV, (int)emulator);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setNumChips(
    JNIEnv *env,
    jclass clazz,
    jlong device,
    jint numCards
)
{
    jint ret;

    (void)env; (void)clazz;

    pthread_mutex_lock(&g_lock);
    ret = (jint)adl_setNumChips(ADLDEV, (int)numCards);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1getBanksCount(
    JNIEnv *env,
    jclass instance
)
{
    (void)env; (void)instance;
    return (jint)adl_getBanksCount();
}


JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setNumFourOpsChn(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint ops4
)
{
    jint ret;

    (void)env; (void)instance;

    pthread_mutex_lock(&g_lock);
    ret = (jint)adl_setNumFourOpsChn(ADLDEV, (int)ops4);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jlong JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1init(
    JNIEnv *env,
    jclass instance,
    jlong sampleRate
)
{
    struct ADL_MIDIPlayer *p;

    (void)env; (void)instance;

    if(mutex_created)
    {
        assert(pthread_mutex_init(&g_lock, NULL) == 0);
        mutex_created=true;
    }
    p = adl_init((long)sampleRate);

    if(p)
        adl_switchEmulator(p, ADLMIDI_EMU_DOSBOX);

    return (jlong)p;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setHVibrato(JNIEnv *env,
    jclass instance,
    jlong device,
    jint hvibrato
)
{
    (void)env;(void)instance;
    pthread_mutex_lock(&g_lock);
    adl_setHVibrato(ADLDEV, (int)hvibrato);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setHTremolo(
    JNIEnv *env,
    jclass instance,
    jlong device, jint htremo
)
{
    (void)env;(void)instance;
    pthread_mutex_lock(&g_lock);
    adl_setHTremolo(ADLDEV, (int)htremo);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setScaleModulators(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint smod
)
{
    (void)env;(void)instance;
    pthread_mutex_lock(&g_lock);
    adl_setScaleModulators(ADLDEV, (int)smod);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setLoopEnabled(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint loopEn
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    adl_setLoopEnabled(ADLDEV, (int)loopEn);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openBankFile(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jstring file_
)
{
    const char *file;
    jint ret;

    (void)instance;

    pthread_mutex_lock(&g_lock);
    file = (*env)->GetStringUTFChars(env, file_, NULL);
    ret = adl_openBankFile(ADLDEV, (char*)file);
    (*env)->ReleaseStringUTFChars(env, file_, file);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openFile(
        JNIEnv *env,
        jclass instance,
        jlong device,
        jstring file_
)
{
    const char *file;
    int ret;

    (void)instance;

    pthread_mutex_lock(&g_lock);
    file = (*env)->GetStringUTFChars(env, file_, NULL);
    ret = adl_openFile(ADLDEV, (char*)file);
    (*env)->ReleaseStringUTFChars(env, file_, file);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1openData(
        JNIEnv *env,
        jclass instance,
        jlong device,
        jbyteArray array_
)
{
    jbyte *array;
    jsize length;
    jint ret;

    (void)instance;

    pthread_mutex_lock(&g_lock);
    array = (*env)->GetByteArrayElements(env, array_, NULL);
    length =  (*env)->GetArrayLength(env, array_);
    ret = adl_openData(ADLDEV, array, (unsigned long)(length));
    (*env)->ReleaseByteArrayElements(env, array_, array, 0);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1reset(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    adl_reset(ADLDEV);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1close(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    adl_close(ADLDEV);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1play(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jshortArray buffer_
)
{
    short  *outBuff;
    jshort *buff;
    jsize   length;
    jint    gotSamples;

    (void)instance;

    pthread_mutex_lock(&g_lock);

    buff = (*env)->GetShortArrayElements(env, buffer_, NULL);
    length = (*env)->GetArrayLength(env, buffer_);
    outBuff = (short*)(buff);
    gotSamples = adl_play(ADLDEV, length, outBuff);
    (*env)->ReleaseShortArrayElements(env, buffer_, buff, 0);

    pthread_mutex_unlock(&g_lock);

    return gotSamples;
}


JNIEXPORT jdouble JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1positionTell(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    jdouble ret;

    (void)env; (void)instance;

    pthread_mutex_lock(&g_lock);
    ret = (jdouble)(adl_positionTell(ADLDEV));
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT jdouble JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1totalTimeLength(
    JNIEnv *env,
    jclass instance,
    jlong device
)
{
    jdouble ret;

    (void)env; (void)instance;

    pthread_mutex_lock(&g_lock);
    ret = (jdouble)(adl_totalTimeLength(ADLDEV));
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1positionSeek(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jdouble seconds
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    adl_positionSeek(ADLDEV, (double)seconds);
    pthread_mutex_unlock(&g_lock);
}



JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setBank(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint bank
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    jint ret = (jint)(adl_setBank(ADLDEV, bank));
    pthread_mutex_unlock(&g_lock);
    return ret;
}

JNIEXPORT jstring JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1getBankName(
    JNIEnv *env,
    jclass instance,
    jint bank
)
{
    (void)instance;
    return (*env)->NewStringUTF(env, adl_getBankNames()[(int)bank]);
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setVolumeRangeModel(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint volumeModel
)
{
    (void)env; (void)instance;

    pthread_mutex_lock(&g_lock);
    adl_setVolumeRangeModel(ADLDEV, (int)volumeModel);
    pthread_mutex_unlock(&g_lock);
}

JNIEXPORT jint JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setRunAtPcmRate(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint enabled
)
{
    jint ret;

    (void)env; (void)instance;

    pthread_mutex_lock(&g_lock);
    ret = (jint)adl_setRunAtPcmRate(ADLDEV, (int)enabled);
    pthread_mutex_unlock(&g_lock);

    return ret;
}

JNIEXPORT void JNICALL
Java_ru_wohlsoft_adlmidiplayer_PlayerService_adl_1setSoftPanEnabled(
    JNIEnv *env,
    jclass instance,
    jlong device,
    jint enabled
)
{
    (void)env; (void)instance;
    pthread_mutex_lock(&g_lock);
    adl_setSoftPanEnabled(ADLDEV, (int)enabled);
    pthread_mutex_unlock(&g_lock);
}
