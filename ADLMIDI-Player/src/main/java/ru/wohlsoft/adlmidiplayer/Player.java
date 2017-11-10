package ru.wohlsoft.adlmidiplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.NumberPicker;
import android.widget.SeekBar;
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
import java.util.Locale;

public class Player extends AppCompatActivity {

    //private int                 MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    private boolean             permission_readAllowed = false;
    public final int            BUF_SIZE = 10240;
    private long                MIDIDevice = 0;
    private volatile boolean    isPlaying = false;

    private class SeekSyncThread extends AsyncTask<Integer, Void, Void> {
        @Override
        protected Void doInBackground(Integer... params) {
            while(!isCancelled()) {
                try {
                    Thread.sleep(1000);
                    if(isPlaying && (MIDIDevice != 0)) {
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run() {
                                SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
                                musPos.setProgress((int)adl_positionTell(MIDIDevice));
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }

            return null;
        }
    }

    private SeekSyncThread      seekSyncThread;

    private SharedPreferences   m_setup = null;

    private String              m_lastFile = "";
    private String              m_lastPath = Environment.getExternalStorageDirectory().getPath();
    private int                 m_ADL_bank = 62;
    private boolean             m_ADL_tremolo = false;
    private boolean             m_ADL_vibrato = false;
    private boolean             m_ADL_scalable = false;
    private boolean             m_ADL_adlibdrums = false;
    private boolean             m_ADL_logvolumes = false;
    private int                 m_adl_numChips = 2;
    private int                 m_ADL_num4opChannels = 7;
    private int                 m_ADL_volumeModel = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        permission_readAllowed = true;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            permission_readAllowed =
                    ContextCompat.checkSelfPermission(this,
                            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        m_setup = getPreferences(Context.MODE_PRIVATE);

        m_lastPath              = m_setup.getString("lastPath", m_lastPath);
        m_ADL_bank              = m_setup.getInt("adlBank", m_ADL_bank);
        m_ADL_tremolo           = m_setup.getBoolean("flagTremolo", m_ADL_tremolo);
        m_ADL_vibrato           = m_setup.getBoolean("flagVibrato", m_ADL_vibrato);
        m_ADL_scalable          = m_setup.getBoolean("flagScalable", m_ADL_scalable);
        m_ADL_adlibdrums        = m_setup.getBoolean("flagAdlibDrums", m_ADL_adlibdrums);
        m_ADL_logvolumes        = m_setup.getBoolean("flagLogVolumes", m_ADL_logvolumes);
        m_adl_numChips          = m_setup.getInt("numChips", m_adl_numChips);
        m_ADL_num4opChannels    = m_setup.getInt("num4opChannels", m_ADL_num4opChannels);
        m_ADL_volumeModel       = m_setup.getInt("volumeModel", m_ADL_volumeModel);

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

                m_setup.edit().putInt("adlBank", m_ADL_bank).apply();

                Toast toast = Toast.makeText(getApplicationContext(),
                        "Bank changed to: " + selectedItemPosition, Toast.LENGTH_SHORT);
                toast.show();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner sVolModel = (Spinner) findViewById(R.id.volumeRangesModel);
        final String[] volumeModelItems = {"[Auto]", "Generic", "CMF", "DMX", "Apogee", "9X" };

        ArrayAdapter<String> adapterVM = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, volumeModelItems);
        adapterVM.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sVolModel.setAdapter(adapterVM);
        sVolModel.setSelection(m_ADL_volumeModel);

        sVolModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                m_ADL_volumeModel = selectedItemPosition;
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

                m_setup.edit().putInt("volumeModel", m_ADL_volumeModel).apply();
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });



        CheckBox deepTremolo = (CheckBox)findViewById(R.id.deepTremolo);
        deepTremolo.setChecked(m_ADL_tremolo);
        deepTremolo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_tremolo = isChecked;
                   if(MIDIDevice != 0)
                        adl_setHTremolo(MIDIDevice, m_ADL_tremolo ? 1 : 0);
                   m_setup.edit().putBoolean("flagTremolo", m_ADL_tremolo).apply();
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "Deep tremolo toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        CheckBox deepVibrato = (CheckBox)findViewById(R.id.deepVibrato);
        deepVibrato.setChecked(m_ADL_vibrato);
        deepVibrato.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_vibrato = isChecked;
                   if(MIDIDevice != 0)
                       adl_setHVibrato(MIDIDevice, m_ADL_vibrato ? 1 : 0);
                   m_setup.edit().putBoolean("flagVibrato", m_ADL_vibrato).apply();
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "Deep vibrato toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        CheckBox scalableMod = (CheckBox)findViewById(R.id.scalableModulation);
        scalableMod.setChecked(m_ADL_scalable);
        scalableMod.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                   @Override
                   public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                       m_ADL_scalable = isChecked;
                       if(MIDIDevice != 0)
                           adl_setScaleModulators(MIDIDevice, m_ADL_scalable ? 1 : 0);
                       m_setup.edit().putBoolean("flagScalable", m_ADL_scalable).apply();
                       Toast toast = Toast.makeText(getApplicationContext(),
                               "Scalabme modulation toggled toggled!", Toast.LENGTH_SHORT);
                       toast.show();
                   }
               }
        );

        CheckBox adlDrums = (CheckBox)findViewById(R.id.adlibDrumsMode);
        adlDrums.setChecked(m_ADL_adlibdrums);
        adlDrums.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
               @Override
               public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                   m_ADL_adlibdrums = isChecked;
                   if(MIDIDevice != 0)
                       adl_setPercMode(MIDIDevice, m_ADL_adlibdrums ? 1 : 0);
                   m_setup.edit().putBoolean("flagAdlibDrums", m_ADL_adlibdrums).apply();
                   Toast toast = Toast.makeText(getApplicationContext(),
                           "AdLib percussion mode toggled!", Toast.LENGTH_SHORT);
                   toast.show();
               }
           }
        );

        CheckBox logVolumes = (CheckBox)findViewById(R.id.logVols);
        logVolumes.setChecked(m_ADL_logvolumes);
        logVolumes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    m_ADL_logvolumes = isChecked;
                    if(MIDIDevice != 0)
                        adl_setLogarithmicVolumes(MIDIDevice, m_ADL_logvolumes ? 1 : 0);
                    m_setup.edit().putBoolean("flagLogVolumes", m_ADL_logvolumes).apply();
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Logariphmic volumes mode toggled!", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        );

        NumberPicker numChips = (NumberPicker)findViewById(R.id.numChips);
        numChips.setMinValue(1);
        numChips.setMaxValue(100);
        numChips.setValue(m_adl_numChips);
        numChips.setWrapSelectorWheel(false);
        TextView numChipsCounter = (TextView)findViewById(R.id.numChipsCount);
        numChipsCounter.setText(String.format(Locale.getDefault(), "%d", m_adl_numChips));

        numChips.setOnValueChangedListener(new NumberPicker.OnValueChangeListener()
        {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal)
            {
                m_adl_numChips = picker.getValue();
                if(m_adl_numChips <=1) {
                    m_adl_numChips = 1;
                    picker.setValue(1);
                } else if(m_adl_numChips >100) {
                    m_adl_numChips = 100;
                    picker.setValue(100);
                }
                NumberPicker num4opChannels = (NumberPicker)findViewById(R.id.num4opChans);
                if(m_ADL_num4opChannels > 6* m_adl_numChips) {
                    m_ADL_num4opChannels = 6* m_adl_numChips;
                    num4opChannels.setValue( m_ADL_num4opChannels );
                    m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
                }
                num4opChannels.setMaxValue(m_adl_numChips *6);

                TextView numChipsCounter = (TextView)findViewById(R.id.numChipsCount);
                numChipsCounter.setText(String.format(Locale.getDefault(), "%d", m_adl_numChips));

                m_setup.edit().putInt("numChips", m_adl_numChips).apply();
            }
        });

        NumberPicker num4opChannels = (NumberPicker)findViewById(R.id.num4opChans);
        num4opChannels.setMinValue(0);
        num4opChannels.setMaxValue(m_adl_numChips *6);
        num4opChannels.setValue(m_ADL_num4opChannels);
        num4opChannels.setWrapSelectorWheel(false);
        TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
        num4opCounter.setText(String.format(Locale.getDefault(), "%d", m_ADL_num4opChannels));

        num4opChannels.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                m_ADL_num4opChannels = picker.getValue();
                if(m_ADL_num4opChannels<=0) {
                    m_ADL_num4opChannels = 0;
                    picker.setValue(0);
                } else if(m_ADL_num4opChannels > 6* m_adl_numChips) {
                    m_ADL_num4opChannels = 6* m_adl_numChips;
                    picker.setValue(m_ADL_num4opChannels);
                }
                TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
                num4opCounter.setText(String.format(Locale.getDefault(), "%d", m_ADL_num4opChannels));
                m_setup.edit().putInt("num4opChannels", m_ADL_num4opChannels).apply();
            }
        });

        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.sample_text);
        tv.setText(stringFromJNI());

        Button quitb = (Button) findViewById(R.id.quitapp);
        quitb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                playerStop();
                uninitPlayer();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    finishAffinity();
                } else {
                    finish();
                }
                System.gc();
                Handler handler = new Handler();
                handler.postDelayed(new Runnable(){
                    @Override
                    public void run(){
                        System.exit(0);
                    }
                }, 1000);
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

        Button restartBtn = (Button) findViewById(R.id.restart);
        restartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnRestartClick(view);
            }
        });


        SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
        musPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            //private double dstPos = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(isPlaying && (MIDIDevice != 0) && fromUser)
                    adl_positionSeek(MIDIDevice, (double)progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
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

        Notification b = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            b = new Notification.Builder(ctx)
                    .setContentTitle("Playing " + m_lastFile)
                    .setContentText("Playing music!")
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(intent)
                    .build();
        } else {
            b = new Notification.Builder(ctx)
                    .setContentTitle("Playing " + m_lastFile)
                    .setContentText("Playing music!")
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentIntent(intent)
                    .getNotification();
        }
        b.flags |= Notification.FLAG_NO_CLEAR|Notification.FLAG_ONGOING_EVENT;
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, b);

        startPlaying(MIDIDevice);
        seekSyncThread = new SeekSyncThread();
        seekSyncThread.execute(0);
    }

    private void playerStop() {
        if(!isPlaying)
            return;

        isPlaying = false;
        seekSyncThread.cancel(true);

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
        adl_setNumChips(MIDIDevice, m_adl_numChips);
        adl_setNumFourOpsChn(MIDIDevice, m_ADL_num4opChannels);
        adl_setHTremolo(MIDIDevice, m_ADL_tremolo?1:0);
        adl_setHVibrato(MIDIDevice, m_ADL_vibrato?1:0);
        adl_setScaleModulators(MIDIDevice, m_ADL_scalable?1:0);
        adl_setPercMode(MIDIDevice, m_ADL_adlibdrums?1:0);
        adl_setLogarithmicVolumes(MIDIDevice, m_ADL_logvolumes?1:0);
        adl_setLoopEnabled(MIDIDevice, 1);
        adl_setVolumeRangeModel(MIDIDevice, m_ADL_volumeModel);
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

    public void OnRestartClick(View view)
    {
        if(isPlaying && (MIDIDevice>0))
        {
            playerStop();
            initPlayer();
            adl_openFile(MIDIDevice, m_lastFile);
            playerPlay();
        }
    }

    public void OnOpenFileClick(View view) {
        // Here, thisActivity is the current activity
        if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) &&
                (ContextCompat.checkSelfPermission(this,
                 Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) )
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ActivityCompat.requestPermissions(this,
                            new String[] { Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                }
                //MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
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
                    .setFilter(".*\\.mid|.*\\.midi|.*\\.kar|.*\\.rmi|.*\\.imf|.*\\.cmf|.*\\.mus|.*\\.xmi")
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
                            m_setup.edit().putString("lastPath", m_lastPath).apply();
                            if(adl_openFile(MIDIDevice, m_lastFile) < 0) {
                                AlertDialog.Builder b = new AlertDialog.Builder(Player.this);
                                b.setTitle("Failed to open file");
                                b.setMessage("Can't open music file because of " + adl_errorInfo(MIDIDevice));
                                b.setNegativeButton(android.R.string.ok, null);
                                b.show();
                                m_lastFile = "";
                            } else {
                                double time = adl_totalTimeLength(MIDIDevice);
                                SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
                                musPos.setMax((int)time);
                                musPos.setProgress(0);
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
//    extern int adl_setNumChips(struct ADL_MIDIPlayer*device, int numCards);
    public native int adl_setNumChips(long device, int numCards);
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

///    /*Enable or disable Logariphmic volume changer */
//    extern void adl_setLogarithmicVolumes(struct ADL_MIDIPlayer* device, int logvol);
    public native void adl_setLogarithmicVolumes(long device, int logvol);

//    /*Set different volume range model */
//    extern void adl_setVolumeRangeModel(struct ADL_MIDIPlayer *device, int volumeModel);
    public native void adl_setVolumeRangeModel(long device, int volumeModel);

///*Returns string which contains last error message*/
//    extern const char* adl_errorString();
    public native String adl_errorString();

///*Returns string which contains last error message on specific device*/
//    extern const char *adl_errorInfo(ADL_MIDIPlayer *device);
    public native String adl_errorInfo(long device);

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

/*Get total time length of current song*/
//extern double adl_totalTimeLength(struct ADL_MIDIPlayer *device);
    public native double adl_totalTimeLength(long device);

/*Jump to absolute time position in seconds*/
//extern void adl_positionSeek(struct ADL_MIDIPlayer *device, double seconds);
    public native void adl_positionSeek(long device, double seconds);

/*Get current time position in seconds*/
//extern double adl_positionTell(struct ADL_MIDIPlayer *device);
    public native double adl_positionTell(long device);


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }
}
