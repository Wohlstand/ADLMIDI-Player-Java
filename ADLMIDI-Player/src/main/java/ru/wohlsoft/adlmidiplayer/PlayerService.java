package ru.wohlsoft.adlmidiplayer;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class PlayerService extends IntentService {
    private static int FOREGROUND_ID=4478;
    final String LOG_TAG = "PlayerService";

    public final int BUF_SIZE = 10240;

    public PlayerService() {
        super("PlayerService");
        Log.d(LOG_TAG, "constrUKT");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return super.onStartCommand(intent,flags,startId);
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
    protected void onHandleIntent(Intent i) {
        if (i == null)
            return;
        long MIDIDevice = 0;
        String filepath = i.getStringExtra("FilePath");
        boolean isPlaying = true;

        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUF_SIZE * 2, AudioTrack.MODE_STREAM);
        short[] sample_buffer = new short[BUF_SIZE];
        audioTrack.play();

        Log.d(LOG_TAG, "onHandleIntent start " + filepath);

        startForeground(FOREGROUND_ID, buildForegroundNotification(filepath));

        MIDIDevice = Player.adl_init(44100);
        Player.adl_openFile(MIDIDevice, filepath);

        while(isPlaying)
        {
            int got = Player.adl_play(MIDIDevice, sample_buffer);
            if(got <= 0) {
                isPlaying = false;
                break;
            }
            audioTrack.write(sample_buffer, 0, got);
        }
        Player.adl_close(MIDIDevice);

        stopForeground(true);

        Log.d(LOG_TAG, "onHandleIntent end " + filepath);
    }

    private Notification buildForegroundNotification(/*Context ctx,*/ String filename) {
        //PendingIntent intent = PendingIntent.getActivity(ctx, 0, i, 0);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this);
        b.setOngoing(true);
        b.setContentTitle("Playing " + filename)
                .setContentText("Playing music!")
                .setSmallIcon(R.drawable.ic_stat_name)
                //.setContentIntent(intent)
                .setTicker("Playing " + filename);
        return(b.build());
    }
}
