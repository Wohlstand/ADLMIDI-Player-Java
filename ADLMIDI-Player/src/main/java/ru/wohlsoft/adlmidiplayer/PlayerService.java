package ru.wohlsoft.adlmidiplayer;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class PlayerService extends Service {
    private static int FOREGROUND_ID=4478;
    final String LOG_TAG = "PlayerService";

    private SharedPreferences   m_setup = null;

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";
    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

    public static final String ACTION_PAUSE = "ACTION_PAUSE";
    public static final String ACTION_PLAY  = "ACTION_PLAY";
    public static final String ACTION_STOP  = "ACTION_STOP";

    public final int            BUF_SIZE = 10240;
    private long                MIDIDevice = 0;
    private volatile boolean    m_isPlaying = false;

    private volatile boolean    m_isInit = false;

    private String              m_lastErrorString = "";

    private String              m_lastFile = "";
    private boolean             m_useCustomBank = false;
    private String              m_lastBankPath = "";
    private int                 m_ADL_bank = 58;
    private int                 m_ADL_tremolo = -1;
    private int                 m_ADL_vibrato = -1;
    private int                 m_ADL_scalable = -1;
    private int                 m_ADL_adlibdrums = -1;

    private int                 m_adl_numChips = 2;
    private int                 m_ADL_num4opChannels = -1;
    private int                 m_ADL_volumeModel = 0;

    private double              m_gainingLevel = 2.0;

    //! Cache of previously sent seek position
    private int                 m_lastSeekPosition = -1;

    private class SeekSyncThread extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... params) {
            while(!isCancelled()) {
                try {
                    Thread.sleep(1000);
                    updateSeekBar(getPosition());
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }
            return null;
        }
    }

    private SeekSyncThread m_seekSyncThread = null;

    public PlayerService() {
        Log.d(LOG_TAG, "constrUKT");
    }

    // This is the object that receives interactions from clients.
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        PlayerService getService() {
            return PlayerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void updateSeekBar(int percentage) {
        if(m_lastSeekPosition == percentage)
            return; //Do nothing
        m_lastSeekPosition = percentage;

        Intent intent = new Intent("ADLMIDI_Broadcast");
        int seekPos = getPosition();
        intent.putExtra("INTENT_TYPE", "SEEKBAR_RESULT");
        intent.putExtra("PERCENTAGE", seekPos);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
        stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
        {
            String action = intent.getAction();

            switch (action)
            {
                case ACTION_START_FOREGROUND_SERVICE:
                    startForegroundService();
                    // Toast.makeText(getApplicationContext(), "Foreground service is started.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    playerStop();
                    stopForegroundService();
                    // Toast.makeText(getApplicationContext(), "Foreground service is stopped.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_PLAY:
                    playerStart();
                    // Toast.makeText(getApplicationContext(), "You click Play button.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP:
                    playerStop();
                    stopForegroundService();
                    break;
                case ACTION_PAUSE:
                    playerStop();
                    // Toast.makeText(getApplicationContext(), "You click Pause button.", Toast.LENGTH_LONG).show();
                    break;
            }
        }
        // Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return super.onStartCommand(intent,flags,startId);
    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        // Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");

        // Create notification default intent.
        Intent intent = new Intent();
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        // Create notification builder.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("ADLMIDI Playing");
        bigTextStyle.bigText("Playing " + m_lastFile);
        // Set big text style.
        builder.setStyle(bigTextStyle);

        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_stat_name);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        builder.setLargeIcon(largeIconBitmap);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        // Make head-up notification.
        builder.setFullScreenIntent(pendingIntent, true);

        Intent openUI = new Intent(getApplicationContext(), Player.class);
        openUI.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingOpenUiIntent = PendingIntent.getActivity(getApplicationContext(), 0, openUI, 0);
        builder.setContentIntent(pendingOpenUiIntent);

        // Add Play button intent in notification.
        Intent playIntent = new Intent(this, PlayerService.class);
        playIntent.setAction(ACTION_PLAY);
        PendingIntent pendingPlayIntent = PendingIntent.getService(this, 0, playIntent, 0);
        NotificationCompat.Action playAction = new NotificationCompat.Action(android.R.drawable.ic_media_play, "Play", pendingPlayIntent);
        builder.addAction(playAction);

        // Add Pause button intent in notification.
        Intent pauseIntent = new Intent(this, PlayerService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pendingPrevIntent = PendingIntent.getService(this, 0, pauseIntent, 0);
        NotificationCompat.Action prevAction = new NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", pendingPrevIntent);
        builder.addAction(prevAction);

        // Add Stop button intent in notification.
        Intent stopIntent = new Intent(this, PlayerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, 0);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent);
        builder.addAction(stopAction);

        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);
    }

    private void stopForegroundService()
    {
        Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.");

        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }

    public boolean isServiceReady()
    {
        return m_isInit;
    }

    public void loadSetup(SharedPreferences setup)
    {
        m_setup = setup;
        if(!m_isInit) {
            m_isInit = true;
            m_useCustomBank = setup.getBoolean("useCustomBank", m_useCustomBank);
            m_lastBankPath = setup.getString("lastBankPath", m_lastBankPath);
            m_ADL_bank = setup.getInt("adlBank", m_ADL_bank);
            m_ADL_tremolo = setup.getBoolean("flagTremolo", m_ADL_tremolo > 0) ? 1 : -1;
            m_ADL_vibrato = setup.getBoolean("flagVibrato", m_ADL_vibrato > 0) ? 1 : -1;
            m_ADL_scalable = setup.getBoolean("flagScalable", m_ADL_scalable > 0) ? 1 : -1;
            m_ADL_adlibdrums = setup.getBoolean("flagAdlibDrums", m_ADL_adlibdrums > 0) ? 1 : -1;
            m_adl_numChips = setup.getInt("numChips", m_adl_numChips);
            m_ADL_num4opChannels = setup.getInt("num4opChannels", m_ADL_num4opChannels);
            m_ADL_volumeModel = setup.getInt("volumeModel", m_ADL_volumeModel);

            m_gainingLevel = (double)setup.getFloat("gaining", (float)m_gainingLevel);
        }
    }

    public void unInitPlayer()
    {
        if(MIDIDevice > 0)
        {
            adl_close(MIDIDevice);
            MIDIDevice = 0;
        }
    }

    public boolean initPlayer()
    {
        if(MIDIDevice == 0) { //Create context when it wasn't created
            setGaining(m_gainingLevel);
            MIDIDevice = adl_init(44100);
        }
        if(MIDIDevice == 0) {
            m_lastErrorString = adl_errorString();
            return false;
        }

        if(m_lastBankPath.isEmpty() || !m_useCustomBank) {
            adl_setBank(MIDIDevice, m_ADL_bank);
        } else {
            if(adl_openBankFile(MIDIDevice, m_lastBankPath) < 0) {
                m_lastErrorString = adl_errorInfo(MIDIDevice);
                return false;
            }
        }

        applySetup();
        return true;
    }

    public void applySetup()
    {
        if(MIDIDevice == 0)
            return;
        adl_setNumChips(MIDIDevice, m_adl_numChips);
        adl_setRunAtPcmRate(MIDIDevice, 1); // Reduces CPU usage, BUT, also reduces sounding accuracy
        adl_setNumFourOpsChn(MIDIDevice, (m_ADL_num4opChannels >= 0) ? m_ADL_num4opChannels : -1); // -1 is "Auto"
        adl_setHTremolo(MIDIDevice, m_ADL_tremolo);
        adl_setHVibrato(MIDIDevice, m_ADL_vibrato);
        adl_setScaleModulators(MIDIDevice, m_ADL_scalable);
        adl_setPercMode(MIDIDevice, m_ADL_adlibdrums);
        adl_setLoopEnabled(MIDIDevice, 1);
        adl_setVolumeRangeModel(MIDIDevice, m_ADL_volumeModel);
    }

    public String getLastError()
    {
        return m_lastErrorString;
    }

    public void openBank(String bankFile)
    {
        m_lastBankPath = bankFile;
        m_setup.edit().putString("lastBankPath", m_lastBankPath).apply();
        if(MIDIDevice == 0) {
            return;
        }
        initPlayer();
    }
    public String getBankPath()
    {
        return m_lastBankPath;
    }

    public void setUseCustomBank(boolean use)
    {
        m_useCustomBank = use;
        m_setup.edit().putBoolean("useCustomBank", m_useCustomBank).apply();
        if(MIDIDevice == 0) {
            return;
        }
        initPlayer();
    }
    public boolean getUseCustomBank()
    {
        return m_useCustomBank;
    }

    public void setEmbeddedBank(int bankId)
    {
        m_ADL_bank = bankId;
        m_setup.edit().putInt("adlBank", m_ADL_bank).apply();
        if(MIDIDevice == 0) {
            return;
        }
        initPlayer();
    }
    public int getEmbeddedBank()
    {
        return m_ADL_bank;
    }


    public void setVolumeModel(int volumeModel)
    {
        m_ADL_volumeModel = volumeModel;
        m_setup.edit().putInt("volumeModel", m_ADL_volumeModel).apply();
        if(MIDIDevice == 0) {
            return;
        }
        adl_setVolumeRangeModel(MIDIDevice, volumeModel);
    }
    public int getVolumeModel()
    {
        return m_ADL_volumeModel;
    }

    public void setDeepTremolo(boolean flag)
    {
        m_ADL_tremolo = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagTremolo", flag).apply();
        if(MIDIDevice == 0) {
            return;
        }
        adl_setHTremolo(MIDIDevice, m_ADL_tremolo);
    }
    public boolean getDeepTremolo()
    {
        return m_ADL_tremolo > 0;
    }

    public void setDeepVibrato(boolean flag)
    {
        m_ADL_vibrato = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagVibrato", flag).apply();
        if(MIDIDevice == 0) {
            return;
        }
        adl_setHVibrato(MIDIDevice, m_ADL_vibrato);
    }
    public boolean getDeepVibrato()
    {
        return m_ADL_vibrato > 0;
    }

    public void setScalableModulators(boolean flag)
    {
        m_ADL_scalable = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagScalable", flag).apply();
        if(MIDIDevice == 0) {
            return;
        }
        adl_setScaleModulators(MIDIDevice, m_ADL_scalable);
    }
    public boolean getScalableModulation()
    {
        return m_ADL_scalable > 0;
    }

    public void setForceRhythmMode(boolean flag)
    {
        m_ADL_adlibdrums = flag ? 1 : -1;
        m_setup.edit().putBoolean("flagAdlibDrums", flag).apply();
        if(MIDIDevice == 0) {
            return;
        }
        adl_setPercMode(MIDIDevice, m_ADL_adlibdrums);
    }
    public boolean getForceRhythmMode()
    {
        return m_ADL_adlibdrums > 0;
    }

    public void setChipsCount(int chips)
    {
        m_adl_numChips = chips;
        m_setup.edit().putInt("numChips", m_adl_numChips).apply();
        if(m_ADL_num4opChannels > getFourOpMax()) {
            m_ADL_num4opChannels = getFourOpMax();
            m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
        }
    }
    public int getChipsCount()
    {
        return m_adl_numChips;
    }

    public int getFourOpMax()
    {
        return 6 * m_adl_numChips;
    }

    public void setFourOpChanCount(int fourOps)
    {
        m_ADL_num4opChannels = fourOps;
        if(m_ADL_num4opChannels > getFourOpMax()) {
            m_ADL_num4opChannels = getFourOpMax();
        }
        m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
    }
    public int getFourOpChanCount()
    {
        return m_ADL_num4opChannels;
    }

    List<String> getEmbeddedBanksList()
    {
        List<String> spinnerArray =  new ArrayList<String>();
        for(Integer i = 0; i < PlayerService.adl_getBanksCount(); i++)
        {
            spinnerArray.add(i.toString() + " - " + PlayerService.adl_getBankName(i));
        }
        return spinnerArray;
    }

    public int getSongLength()
    {
        if(MIDIDevice == 0) {
            return 0;
        }
        double time = adl_totalTimeLength(MIDIDevice);
        return (int)time;
    }

    public int getPosition()
    {
        if(MIDIDevice == 0) {
            return 0;
        }
        return (int)adl_positionTell(MIDIDevice);
    }

    public void setPosition(int pos)
    {
        if(MIDIDevice == 0) {
            return;
        }
        adl_positionSeek(MIDIDevice, (double)pos);
    }

    public void gainingSet(double gaining)
    {
        m_gainingLevel = gaining;
        setGaining(gaining);
        m_setup.edit().putFloat("gaining", (float)m_gainingLevel).apply();
    }
    public double gainingGet()
    {
        return m_gainingLevel;
    }

    public boolean openMusic(String musicFile) {
        if(MIDIDevice == 0) {
            m_lastErrorString = "Library is NOT initialized!";
            return false;
        }
        if(adl_openFile(MIDIDevice, musicFile) < 0) {
            m_lastErrorString = adl_errorInfo(MIDIDevice);
            return false;
        }
        m_lastFile = musicFile;
        return true;
    }

    public String getCurrentMusicPath()
    {
        return m_lastFile;
    }
    public boolean hasLoadedMusic()
    {
        return !m_lastFile.isEmpty();
    }

    public boolean isPlaying() {
        return m_isPlaying;
    }

    public boolean togglePlayPause() {
        if(!m_isPlaying)
        {
            return playerStart();
        } else {
            return playerStop();
        }
    }

    public boolean playerRestart() {
        if(m_isPlaying)
            playerStop();
        openMusic(m_lastFile);
        return playerStart();
    }

    public boolean playerStart() {
        if(MIDIDevice == 0) {
            return false;
        }
        if(!m_isPlaying) {
            m_isPlaying = true;
            startPlaying(MIDIDevice);
            m_seekSyncThread = new SeekSyncThread();
            m_seekSyncThread.execute(0);
        }
        return true;
    }

    public boolean playerStop() {
        if(MIDIDevice == 0) {
            return false;
        }
        if(m_isPlaying) {
            m_isPlaying = false;
            stopPlaying();
            if(m_seekSyncThread != null)
                m_seekSyncThread.cancel(true);
            m_seekSyncThread = null;
        }
        return true;
    }



//    @Override
//    protected void onHandleIntent(Intent i) {
//        if (i == null)
//            return;
//        long MIDIDevice = 0;
//        String filepath = i.getStringExtra("FilePath");
//        boolean isPlaying = true;
//
//        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
//                AudioFormat.CHANNEL_OUT_STEREO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                BUF_SIZE * 2, AudioTrack.MODE_STREAM);
//        short[] sample_buffer = new short[BUF_SIZE];
//        audioTrack.play();
//
//        Log.d(LOG_TAG, "onHandleIntent start " + filepath);
//
//        startForeground(FOREGROUND_ID, buildForegroundNotification(filepath));
//
////        MIDIDevice = Player.adl_init(44100);
////        Player.adl_openFile(MIDIDevice, filepath);
////
////        while(isPlaying)
////        {
////            int got = Player.adl_play(MIDIDevice, sample_buffer);
////            if(got <= 0) {
////                isPlaying = false;
////                break;
////            }
////            audioTrack.write(sample_buffer, 0, got);
////        }
////        Player.adl_close(MIDIDevice);
//
//
//        stopForeground(true);
//
//        Log.d(LOG_TAG, "onHandleIntent end " + filepath);
//    }

//
//    private Notification buildForegroundNotification(/*Context ctx,*/ String filename) {
//        //PendingIntent intent = PendingIntent.getActivity(ctx, 0, i, 0);
//        NotificationCompat.Builder b=new NotificationCompat.Builder(this);
//        b.setOngoing(true);
//        b.setContentTitle("Playing " + filename)
//                .setContentText("Playing music!")
//                .setSmallIcon(R.drawable.ic_stat_name)
//                //.setContentIntent(intent)
//                .setTicker("Playing " + filename);
//        return(b.build());
//    }




    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public static native String stringFromJNI();

    /**
     * Start OpenSLES player with fetching specified ADLMIDI device
     * @param device pointer to currently constructed ADLMIDI device
     */
    public static native void startPlaying(long device);

    /**
     * Stop OpenSLES player
     */
    public static native void stopPlaying();

    /**
     * Set sound volume gain level
     * @param gaining value of gaining
     */
    public static native void setGaining(double gaining);

    //    /* Sets number of emulated sound cards (from 1 to 100). Emulation of multiple sound cards exchanges polyphony limits*/
//    extern int adl_setNumChips(struct ADL_MIDIPlayer*device, int numCards);
    public static native int adl_setNumChips(long device, int numCards);
    //
///* Sets a number of the patches bank from 0 to N banks */
//    extern int adl_setBank(struct ADL_MIDIPlayer* device, int bank);
//
    public static native int adl_setBank(long device, int bank);

    ///* Returns total number of available banks */
//    extern int adl_getBanksCount();
    public static native int adl_getBanksCount();

    public static native String adl_getBankName(int bank);
    //
///*Sets number of 4-chan operators*/
//    extern int adl_setNumFourOpsChn(struct ADL_MIDIPlayer*device, int ops4);
    public static native int adl_setNumFourOpsChn(long device, int ops4);
    //
///*Enable or disable AdLib percussion mode*/
//    extern void adl_setPercMode(struct ADL_MIDIPlayer* device, int percmod);
    public static native void adl_setPercMode(long device, int percmod);
    //
///*Enable or disable deep vibrato*/
//    extern void adl_setHVibrato(struct ADL_MIDIPlayer* device, int hvibro);
//
    public static native void adl_setHVibrato(long device, int hvibrato);
    ///*Enable or disable deep tremolo*/
//    extern void adl_setHTremolo(struct ADL_MIDIPlayer* device, int htremo);
//
    public static native void adl_setHTremolo(long device, int htremo);
    ///*Enable or disable Enables scaling of modulator volumes*/
//    extern void adl_setScaleModulators(struct ADL_MIDIPlayer* device, int smod);
//
    public static native void adl_setScaleModulators(long device, int smod);
    ///*Enable or disable built-in loop (built-in loop supports 'loopStart' and 'loopEnd' tags to loop specific part)*/
//    extern void adl_setLoopEnabled(struct ADL_MIDIPlayer* device, int loopEn);
//
    public static native void adl_setLoopEnabled(long device, int loopEn);

    ///    /*Enable or disable Logariphmic volume changer */
//    extern void adl_setLogarithmicVolumes(struct ADL_MIDIPlayer* device, int logvol);
    public static native void adl_setLogarithmicVolumes(long device, int logvol);

    //    /*Set different volume range model */
//    extern void adl_setVolumeRangeModel(struct ADL_MIDIPlayer *device, int volumeModel);
    public static native void adl_setVolumeRangeModel(long device, int volumeModel);

    public static native int adl_setRunAtPcmRate(long device, int enabled);

    ///*Returns string which contains last error message*/
//    extern const char* adl_errorString();
    public static native String adl_errorString();

    ///*Returns string which contains last error message on specific device*/
//    extern const char *adl_errorInfo(ADL_MIDIPlayer *device);
    public static native String adl_errorInfo(long device);

    //
//    /*Initialize ADLMIDI Player device*/
//    extern struct ADL_MIDIPlayer* adl_init(long sample_rate);
    public static native long adl_init(long sampleRate);

    //
///*Load WOPL bank file from File System. Is recommended to call adl_reset() to apply changes to already-loaded file player or real-time.*/
//    extern int adl_openBankFile(struct ADL_MIDIPlayer *device, const char *filePath);
    public static native int adl_openBankFile(long device, String file);
    //
///*Load MIDI file from File System*/
//    extern int adl_openFile(struct ADL_MIDIPlayer* device, char *filePath);
    public static native int adl_openFile(long device, String file);
    //
///*Load MIDI file from memory data*/
//    extern int adl_openData(struct ADL_MIDIPlayer* device, void* mem, long size);
    public static native int adl_openData(long device, byte[] array);
    //
///*Resets MIDI player*/
//    extern void adl_reset(struct ADL_MIDIPlayer*device);
    public static native void adl_reset(long device);
    //
///*Close and delete ADLMIDI device*/
//    extern void adl_close(struct ADL_MIDIPlayer *device);
    public static native void adl_close(long device);
    //
///*Take a sample buffer*/
//    extern int  adl_play(struct ADL_MIDIPlayer*device, int sampleCount, short out[]);
    public static native int adl_play(long device, short[] buffer);

    /*Get total time length of current song*/
//extern double adl_totalTimeLength(struct ADL_MIDIPlayer *device);
    public static native double adl_totalTimeLength(long device);

    /*Jump to absolute time position in seconds*/
//extern void adl_positionSeek(struct ADL_MIDIPlayer *device, double seconds);
    public static native void adl_positionSeek(long device, double seconds);

    /*Get current time position in seconds*/
//extern double adl_positionTell(struct ADL_MIDIPlayer *device);
    public static native double adl_positionTell(long device);


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}