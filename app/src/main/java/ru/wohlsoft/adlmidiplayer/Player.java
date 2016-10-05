package ru.wohlsoft.adlmidiplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class Player extends AppCompatActivity {

    private int                 MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private boolean             permission_readAllowed = false;
    public final int            BUF_SIZE = 10240;
    private long                MIDIDevice = 0;
    private volatile boolean    isPlaying = false;
    private String              m_lastFile = "";
    private String              m_lastPath = Environment.getExternalStorageDirectory().getPath();;

    private int                 m_ADL_bank = 62;
    private boolean             m_ADL_tremolo = false;
    private boolean             m_ADL_vibrato = false;
    private boolean             m_ADL_scalable = false;
    private boolean             m_ADL_adlibdrums = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        permission_readAllowed =
                ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        //Fill bank number box
        List<String> spinnerArray =  new ArrayList<String>();
        for(Integer i=0; i<adl_getBanksCount(); i++)
        {
            spinnerArray.add(i.toString() + " - " + adl_getBankName(i));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.bankNo);
        sItems.setAdapter(adapter);
        sItems.setSelection(m_ADL_bank);

        sItems.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                m_ADL_bank = selectedItemPosition;
                if(!m_lastFile.isEmpty() && (MIDIDevice!=0)) {
                    if (isPlaying) {
                        playerStop();
                        initPlayer();
                        adl_openFile(MIDIDevice, m_lastFile);
                        playerPlay();
                    }
                    else {
                        initPlayer();
                        adl_openFile(MIDIDevice, m_lastFile);
                    }
                }
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Bank changed to: " + selectedItemPosition, Toast.LENGTH_SHORT);
                toast.show();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        CheckBox deepTremolo = (CheckBox)findViewById(R.id.deepTremolo);
        deepTremolo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_tremolo = isChecked;
                   if(MIDIDevice != 0)
                        adl_setHTremolo(MIDIDevice, m_ADL_tremolo ? 1 : 0);
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "Deep tremolo toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        CheckBox deepVibrato = (CheckBox)findViewById(R.id.deepVibrato);
        deepVibrato.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_vibrato = isChecked;
                   if(MIDIDevice != 0)
                       adl_setHVibrato(MIDIDevice, m_ADL_vibrato ? 1 : 0);
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "Deep vibrato toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        CheckBox scalableMod = (CheckBox)findViewById(R.id.scalableModulation);
        scalableMod.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                   @Override
                   public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                       m_ADL_scalable = isChecked;
                       if(MIDIDevice != 0)
                           adl_setScaleModulators(MIDIDevice, m_ADL_scalable ? 1 : 0);
                       Toast toast = Toast.makeText(getApplicationContext(),
                               "Scalabme modulation toggled toggled!", Toast.LENGTH_SHORT);
                       toast.show();
                   }
               }
        );

        CheckBox adlDrums = (CheckBox)findViewById(R.id.adlibDrumsMode);
        adlDrums.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_adlibdrums = isChecked;
                   if(MIDIDevice != 0)
                       adl_setPercMode(MIDIDevice, m_ADL_adlibdrums ? 1 : 0);
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "AdLib percussion mode toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        Button quitb = (Button) findViewById(R.id.quitapp);
        quitb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerStop();
                uninitPlayer();
                System.gc();
                finish();
            }
        });

        Button openfb = (Button) findViewById(R.id.openFile);
        openfb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnOpenFileClick(view);
            }
        });

        Button playPause = (Button) findViewById(R.id.playPause);
        playPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnPlayClick(view);
            }
        });
    }

    private void playerPlay()
    {
        if(isPlaying)
            return;

        if(MIDIDevice==0)
            return;

        isPlaying = true;
        Context ctx = getApplicationContext();
        Intent notificationIntent = new Intent(ctx, Player.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent intent = PendingIntent.getActivity(ctx, 0, notificationIntent, 0);

        Notification b = new Notification.Builder(ctx)
                .setContentTitle("Playing " + m_lastFile)
                .setContentText("Playing music!")
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentIntent(intent)
                .build();
        b.flags |= Notification.FLAG_NO_CLEAR|Notification.FLAG_ONGOING_EVENT;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, b);

        startPlaying(MIDIDevice);
    }

    private void playerStop()
    {
        if(!isPlaying)
            return;
        isPlaying = false;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        stopPlaying();
    }

    private void uninitPlayer()
    {
        if(MIDIDevice>0)
        {
            adl_close(MIDIDevice);
            MIDIDevice = 0;
        }
    }
    private void initPlayer()
    {
        uninitPlayer();
        MIDIDevice = adl_init(44100);
        adl_setBank(MIDIDevice, m_ADL_bank);
        adl_setHTremolo(MIDIDevice, m_ADL_tremolo?1:0);
        adl_setHVibrato(MIDIDevice, m_ADL_vibrato?1:0);
        adl_setScaleModulators(MIDIDevice, m_ADL_scalable?1:0);
        adl_setPercMode(MIDIDevice, m_ADL_adlibdrums?1:0);
    }

    public void OnPlayClick(View view)
    {
        if(!isPlaying)
        {
            playerPlay();
        } else {
            playerStop();
        }
    }

    public void OnOpenFileClick(View view) {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
            // Should we show an explanation?
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE))
            {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Permission denied");
                b.setMessage("Sorry, but permission is denied!\n"+
                             "Please, check permissions to application!");
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[] { Manifest.permission.READ_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            if(!permission_readAllowed)
            {
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Failed permissions");
                b.setMessage("Can't open file dialog because permission denied. Restart application to take effects!");
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
                return;
            }
            OpenFileDialog fileDialog = new OpenFileDialog(this)
                    .setFilter(".*\\.mid|.*\\.MID|.*\\.kar|.*\\.KAR|.*\\.rmi|.*\\.RMI|.*\\.imf|.*\\.IMF")
                    .setCurrentDirectory(m_lastPath)
                    .setOpenDialogListener(new OpenFileDialog.OpenDialogListener()
                    {
                        @Override
                        public void OnSelectedFile(String fileName, String lastPath) {
                            Toast.makeText(getApplicationContext(), fileName, Toast.LENGTH_LONG).show();
                            TextView tv = (TextView) findViewById(R.id.sample_text);
                            tv.setText(fileName);

                            //Abort previos playing state
                            boolean wasPlay = isPlaying;
                            if(isPlaying)
                                playerStop();
                            initPlayer();
                            m_lastFile = fileName;
                            m_lastPath = lastPath;
                            if(adl_openFile(MIDIDevice, m_lastFile) < 0)
                            {
                                AlertDialog.Builder b = new AlertDialog.Builder(getParent());
                                b.setTitle("Failed to open file");
                                b.setMessage("Can't open music file because " + adl_errorString());
                                b.setNegativeButton(android.R.string.ok, null);
                                b.show();
                                m_lastFile = "";
                            } else {
                                if (wasPlay)
                                    playerPlay();
                            }
                        }
                    });
            fileDialog.show();
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    /**
     * Start OpenSLES player with fetching specified ADLMIDI device
     * @param device pointer to currently constructed ADLMIDI device
     */
    public native void startPlaying(long device);

    /**
     * Stop OpenSLES player
     */
    public native void stopPlaying();

//    /* Sets number of emulated sound cards (from 1 to 100). Emulation of multiple sound cards exchanges polyphony limits*/
//    extern int adl_setNumCards(struct ADL_MIDIPlayer*device, int numCards);
    public native int adl_setNumCards(long device, int numCards);
//
///* Sets a number of the patches bank from 0 to N banks */
//    extern int adl_setBank(struct ADL_MIDIPlayer* device, int bank);
//
    public native int adl_setBank(long device, int bank);
///* Returns total number of available banks */
//    extern int adl_getBanksCount();
    public native int adl_getBanksCount();

    public native String adl_getBankName(int bank);
//
///*Sets number of 4-chan operators*/
//    extern int adl_setNumFourOpsChn(struct ADL_MIDIPlayer*device, int ops4);
    public native int adl_setNumFourOpsChn(long device, int ops4);
//
///*Enable or disable AdLib percussion mode*/
//    extern void adl_setPercMode(struct ADL_MIDIPlayer* device, int percmod);
    public native void adl_setPercMode(long device, int percmod);
//
///*Enable or disable deep vibrato*/
//    extern void adl_setHVibrato(struct ADL_MIDIPlayer* device, int hvibro);
//
    public native void adl_setHVibrato(long device, int hvibrato);
///*Enable or disable deep tremolo*/
//    extern void adl_setHTremolo(struct ADL_MIDIPlayer* device, int htremo);
//
    public native void adl_setHTremolo(long device, int htremo);
///*Enable or disable Enables scaling of modulator volumes*/
//    extern void adl_setScaleModulators(struct ADL_MIDIPlayer* device, int smod);
//
    public native void adl_setScaleModulators(long device, int smod);
///*Enable or disable built-in loop (built-in loop supports 'loopStart' and 'loopEnd' tags to loop specific part)*/
//    extern void adl_setLoopEnabled(struct ADL_MIDIPlayer* device, int loopEn);
//
    public native void adl_setLoopEnabled(long device, int loopEn);
///*Returns string which contains last error message*/
//    extern const char* adl_errorString();
    public native String adl_errorString();
//
//    /*Initialize ADLMIDI Player device*/
//    extern struct ADL_MIDIPlayer* adl_init(long sample_rate);
    public native long adl_init(long sampleRate);
//
///*Load MIDI file from File System*/
//    extern int adl_openFile(struct ADL_MIDIPlayer* device, char *filePath);
    public native int adl_openFile(long device, String file);
//
///*Load MIDI file from memory data*/
//    extern int adl_openData(struct ADL_MIDIPlayer* device, void* mem, long size);
    public native int adl_openData(long device, byte[] array);
//
///*Resets MIDI player*/
//    extern void adl_reset(struct ADL_MIDIPlayer*device);
    public native void adl_reset(long device);
//
///*Close and delete ADLMIDI device*/
//    extern void adl_close(struct ADL_MIDIPlayer *device);
    public native void adl_close(long device);
//
///*Take a sample buffer*/
//    extern int  adl_play(struct ADL_MIDIPlayer*device, int sampleCount, short out[]);
    public native int adl_play(long device, short[] buffer);


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
