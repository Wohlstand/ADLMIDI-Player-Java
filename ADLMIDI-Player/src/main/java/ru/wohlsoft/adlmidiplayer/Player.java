package ru.wohlsoft.adlmidiplayer;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.app.ActivityCompat;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

public class Player extends AppCompatActivity
{
    final String LOG_TAG = "ADLMIDI";

    public static final int READ_PERMISSION_FOR_BANK = 1;
    public static final int READ_PERMISSION_FOR_MUSIC = 2;
    public static final int READ_PERMISSION_FOR_INTENT = 3;
    public static final int NOTIFICATIONS_PERMISSION_FOR_INTENT = 4;

    private PlayerService m_service = null;
    private volatile boolean m_bound = false;
    private volatile boolean m_banksListLoaded = false;
//    private volatile boolean m_fourOpsCountLoaded = false;
    private volatile boolean m_uiLoaded = false;

    private SharedPreferences   m_setup = null;

    private String              m_lastPath = "";
    private String              m_lastBankPath = "";
    private String              m_lastMusicPath = "";

    private int                 m_chipsCount = 2;
    private int                 m_fourOpsCount = -1;

    // DELAYED OPERATIONS
    private boolean             m_needBankReload = false;
    private boolean             m_needMusicReload = false;


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String intentType = intent.getStringExtra("INTENT_TYPE");
            assert intentType != null;
            if(intentType.equalsIgnoreCase("SEEKBAR_RESULT"))
            {
                int percentage = intent.getIntExtra("PERCENTAGE", -1);
                SeekBar musPos = findViewById(R.id.musPos);
                if(percentage >= 0)
                    musPos.setProgress(percentage);
            }
        }
    };

    public static double round(double value, int places)
    {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void seekerStart()
    {
        //Register Broadcast receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, new IntentFilter("ADLMIDI_Broadcast"));
    }

    public void seekerStop()
    {
        //Unregister Broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private final ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service)
        {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            m_service = binder.getService();
            m_bound = true;
            Log.d(LOG_TAG, "mConnection: Connected");
            initUiSetup(true);

            if(m_needBankReload)
            {
                m_service.openBank(m_lastBankPath);
                m_needBankReload = false;
            }

            if(m_needMusicReload)
            {
                processMusicFile(m_lastMusicPath, m_lastPath);
                m_needMusicReload = false;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0)
        {
            m_bound = false;
            Log.d(LOG_TAG, "mConnection: Disconnected");
        }
    };

    @SuppressLint("SetTextI18n")
    private void initUiSetup(boolean fromConnect)
    {
        boolean isPlaying = false;

        if(!fromConnect)
        {
            AppSettings.loadSetup(m_setup);
            if(reconnectPlayerService())
                return;
        }

        if (m_bound)
        {
            Log.d(LOG_TAG, "\"bound\" set to true");
            isPlaying = m_service.isPlaying();

            if(isPlaying)
            {
                Log.d(LOG_TAG, "Player works, make a toast");
                seekerStart();
                Toast toast = Toast.makeText(getApplicationContext(), "Already playing", Toast.LENGTH_SHORT);
                toast.show();
            }
            else
                Log.d(LOG_TAG, "Player doesn NOT works");
        }
        else
            Log.d(LOG_TAG, "\"bound\" set to false");

        if(m_uiLoaded)
        {
            Log.d(LOG_TAG, "UI already loaded, do nothing");
            return;
        }

        Log.d(LOG_TAG, "UI is not loaded, do load");

        /*
         * Music position seeker
         */
        SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
        if(m_bound)
        {
            musPos.setMax(m_service.getSongLength());
            musPos.setProgress(m_service.getPosition());
        }
        musPos.setProgress(0);
        musPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            //private double dstPos = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                if(fromUser && m_bound)
                    m_service.setPosition(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        /*
         * Filename title
         */
        // Example of a call to a native method
        TextView tv = (TextView) findViewById(R.id.currentFileName);
        tv.setText(PlayerService.stringFromJNI());
        if(isPlaying) {
            tv.setText(m_service.getCurrentMusicPath());
        }

        /*
         * Bank name title
         */
        TextView cbl = (TextView) findViewById(R.id.bankFileName);
        m_lastBankPath = AppSettings.getBankPath();
        if(!m_lastBankPath.isEmpty()) {
            File f = new File(m_lastBankPath);
            cbl.setText(f.getName());
        } else {
            cbl.setText(R.string.noCustomBankLabel);
        }


        /*
         * Embedded banks list combo-box
         */
        //Fill bank number box
        List<String> spinnerArray = PlayerService.getEmbeddedBanksList();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner sItems = (Spinner) findViewById(R.id.bankNo);
        sItems.setAdapter(adapter);
        sItems.setSelection(AppSettings.getEmbeddedBank());

        m_banksListLoaded = false;
        sItems.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId)
            {
                if(!m_banksListLoaded)
                {
                    m_banksListLoaded = true;
                    return;
                }

                AppSettings.setEmbeddedBank(selectedItemPosition);
                if(m_bound)
                    m_service.setEmbeddedBank(selectedItemPosition);

                Toast toast = Toast.makeText(getApplicationContext(),
                        "Bank changed to: " + selectedItemPosition, Toast.LENGTH_SHORT);
                toast.show();
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        /*
         * Use custom bank checkbox
         */
        CheckBox useCustomBank = (CheckBox)findViewById(R.id.useCustom);
        useCustomBank.setChecked(AppSettings.getUseCustomBank());
        useCustomBank.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setUseCustomBank(isChecked);
                if(m_bound)
                    m_service.setUseCustomBank(isChecked);
            }
        });


        /*
         * Emulator model combo-box
         */
        Spinner sEmulator = (Spinner) findViewById(R.id.emulatorType);
        final String[] emulatorItems =
        {
            "Nuked OPL3 v1.8 (very accurate)",
            "Nuked OPL3 v1.7 (very accurate)",
            "DosBox OPL3 (accurate and fast)",
            "Opal OPL3 (no rhythm-mode)",
            "Java OPL3 (broken rhythm-mode)",
            "ESFMu (Emulator of ESS's ESFM)",
            "MAME OPL2 (9 2op voices, broken rhythm-mode)",
            "YMFM OPL2 (9 2op voices)",
            "YMFM OPL3 (good accuracy)",
        };

        ArrayAdapter<String> adapterEMU = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, emulatorItems);
        adapterEMU.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sEmulator.setAdapter(adapterEMU);
        sEmulator.setSelection(AppSettings.getEmulator());

        sEmulator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId) {
                if(m_bound)
                    m_service.setEmulator(selectedItemPosition);
                // Unlike other options, this should be set after an engine-side update
                AppSettings.setEmulator(selectedItemPosition);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        /*
         * Volume model combo-box
         */
        Spinner sVolModel = (Spinner) findViewById(R.id.volumeRangesModel);
        final String[] volumeModelItems =
        {
            "[Auto]", "Generic", "CMF", "DMX", "Apogee",
            "9X (SB16)", "DMX (Fixed AM)", "Apogee (Fixed AM)", "Audio Interface Library",
            "9X (Generic FM)", "HMI Sound Operating System", "HMI Sound Operating System (Old)"
        };

        ArrayAdapter<String> adapterVM = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, volumeModelItems);
        adapterVM.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sVolModel.setAdapter(adapterVM);
        sVolModel.setSelection(AppSettings.getVolumeModel());

        sVolModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId)
            {
                AppSettings.setVolumeModel(selectedItemPosition);
                if(m_bound)
                    m_service.setVolumeModel(selectedItemPosition);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        /*
         * Channel allocation mode combo-box
         */
        Spinner sChanMode = (Spinner) findViewById(R.id.channelAllocationMode);
        final String[] chanAllocModeItems = {"[Auto]", "Releasing delay", "Released with same instrument", "Any released" };

        ArrayAdapter<String> adapterCA = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, chanAllocModeItems);
        adapterCA.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sChanMode.setAdapter(adapterCA);
        sChanMode.setSelection(AppSettings.getChanAlocMode() + 1);

        sChanMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,
                                       View itemSelected, int selectedItemPosition, long selectedId)
            {
                AppSettings.setChanAllocMode(selectedItemPosition - 1);
                if(m_bound)
                    m_service.setChanAllocMode(selectedItemPosition - 1);
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });


        /*
         * Deep Tremolo checkbox
         */
        CheckBox deepTremolo = (CheckBox)findViewById(R.id.deepTremolo);
        deepTremolo.setChecked(AppSettings.getDeepTremolo());
        deepTremolo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setDeepTremolo(isChecked);
                if(m_bound)
                    m_service.setDeepTremolo(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Deep tremolo toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Deep Vibrato checkbox
         */
        CheckBox deepVibrato = (CheckBox)findViewById(R.id.deepVibrato);
        deepVibrato.setChecked(AppSettings.getDeepVibrato());
        deepVibrato.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setDeepVibrato(isChecked);
                if(m_bound)
                    m_service.setDeepVibrato(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Deep vibrato toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Scalable Modulators checkbox
         */
        CheckBox scalableMod = (CheckBox)findViewById(R.id.scalableModulation);
        scalableMod.setChecked(AppSettings.getScalableModulation());
        scalableMod.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setScalableModulators(isChecked);
                if(m_bound)
                    m_service.setScalableModulators(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                          "Scalable modulation toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Run at PCM Rate checkbox
         */
        CheckBox runAtPcmRate = (CheckBox)findViewById(R.id.runAtPcmRate);
        runAtPcmRate.setChecked(AppSettings.getRunAtPcmRate());
        runAtPcmRate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setRunAtPcmRate(isChecked);
                if(m_bound)
                    m_service.setRunAtPcmRate(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Run at PCM Rate has toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Full-Panning Stereo checkbox
         */
        CheckBox fullPanningStereo = (CheckBox)findViewById(R.id.fullPanningStereo);
        fullPanningStereo.setChecked(AppSettings.getFullPanningStereo());
        fullPanningStereo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setFullPanningStereo(isChecked);
                if(m_bound)
                    m_service.setFullPanningStereo(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Full-Panning toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Automatic arpeggio
         */
        CheckBox autoArpeggio = (CheckBox)findViewById(R.id.autoArpeggio);
        autoArpeggio.setChecked(AppSettings.getAutoArpeggio());
        autoArpeggio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                AppSettings.setAutoArpeggio(isChecked);
                if(m_bound)
                    m_service.setAutoArpeggio(isChecked);
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Auto Arpeggio toggled!", Toast.LENGTH_SHORT);
                toast.show();
            }
        });

        /*
         * Chips count
         */
        Button numChipsMinus = (Button) findViewById(R.id.numChipsMinus);
        numChipsMinus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                int g = m_chipsCount;
                g--;
                if(g < 1) {
                    return;
                }
                onChipsCountUpdate(g, false);
            }
        });

        Button numChipsPlus = (Button) findViewById(R.id.numChipsPlus);
        numChipsPlus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                int g = m_chipsCount;
                g++;
                if(g > 100) {
                    return;
                }
                onChipsCountUpdate(g, false);
            }
        });

        onChipsCountUpdate(AppSettings.getChipsCount(), true);


        /*
         * Number of four-operator channels
         */
        Button num4opChansMinus = (Button) findViewById(R.id.num4opChansMinus);
        num4opChansMinus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                int g = m_fourOpsCount;
                g--;

                if(g < -1)
                    return;

                onFourOpsCountUpdate(g, false);
            }
        });

        Button num4opChansPlus = (Button) findViewById(R.id.num4opChansPlus);
        num4opChansPlus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                int curFourOpMax = m_chipsCount * 6;
                int g = m_fourOpsCount;
                g++;

                if(g > curFourOpMax)
                    return;

                onFourOpsCountUpdate(g, false);
            }
        });

        onFourOpsCountUpdate(AppSettings.getFourOpChanCount(), true);

        /*
         * Gain level
         */
        Button gainMinusMinus = (Button) findViewById(R.id.gainFactorMinusMinus);
        gainMinusMinus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(m_bound)
                {
                    double gain = AppSettings.getGaining();
                    gain -= 1.0;
                    if(gain < 0.1)
                        gain += 1.0;
                    onGainUpdate(gain, false);
                }
            }
        });

        Button gainMinus = (Button) findViewById(R.id.gainFactorMinus);
        gainMinus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(m_bound)
                {
                    double gain = AppSettings.getGaining();
                    gain -= 0.1;
                    if(gain < 0.1)
                        gain += 0.1;
                    onGainUpdate(gain, false);
                }
            }
        });

        Button gainPlus = (Button) findViewById(R.id.gainFactorPlus);
        gainPlus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(m_bound)
                {
                    double gain = AppSettings.getGaining();
                    gain += 0.1;
                    onGainUpdate(gain, false);
                }
            }
        });

        Button gainPlusPlus = (Button) findViewById(R.id.gainFactorPlusPlus);
        gainPlusPlus.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if(m_bound)
                {
                    double gain = AppSettings.getGaining();
                    gain += 1.0;
                    onGainUpdate(gain, false);
                }
            }
        });

        onGainUpdate(AppSettings.getGaining(), true);



        /* *******Everything UI related has been initialized!****** */
        m_uiLoaded = true;

        // Try to load external file if requested
        handleFileIntent();

        // TODO: Make the PROPER settings loading without service bootstrapping and remove this mess as fast as possible!!!!
        // WORKAROUND: stop the service
        if(!isPlaying)
            playerServiceStop();
    }

    private void playerServiceStart()
    {
        bindPlayerService();
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerService.ACTION_START_FOREGROUND_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void playerServiceStop()
    {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerService.ACTION_CLOSE_FOREGROUND_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // Workaround to keep content visible
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        {
            RelativeLayout lo = findViewById(R.id.activity_playerZ);
            lo.setPadding(lo.getPaddingLeft(), lo.getPaddingLeft() + 140, lo.getPaddingRight(), lo.getPaddingBottom() + 140);
        }

        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P)
            m_lastPath = Environment.getExternalStorageDirectory().getPath();
        else
            m_lastPath = "/storage/emulated/0";

        m_setup = getPreferences(Context.MODE_PRIVATE);

        m_lastPath = m_setup.getString("lastPath", m_lastPath);
        m_lastMusicPath = m_setup.getString("lastMusicPath", m_lastMusicPath);

        Button quitBut = (Button) findViewById(R.id.quitapp);
        quitBut.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Log.d(LOG_TAG, "Quit: Trying to stop seeker");
                seekerStop();
                if(m_bound) {
                    Log.d(LOG_TAG, "Quit: Stopping player");
                    m_service.playerStop();
                    Log.d(LOG_TAG, "Quit: De-Initializing player");
                    m_service.unInitPlayer();
                }
                Log.d(LOG_TAG, "Quit: Stopping player service");
                playerServiceStop();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    Log.d(LOG_TAG, "Quit: Finish Affinity");
                    Player.this.finishAffinity();
                } else {
                    Log.d(LOG_TAG, "Quit: Just finish");
                    Player.this.finish();
                }
                Log.d(LOG_TAG, "Quit: Collect garbage");
                System.gc();
            }
        });

        Button openfb = (Button) findViewById(R.id.openFile);
        openfb.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                OnOpenFileClick(view);
            }
        });

        Button playPause = (Button) findViewById(R.id.playPause);
        playPause.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                OnPlayClick(view);
            }
        });

        Button restartBtn = (Button) findViewById(R.id.restart);
        restartBtn.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                OnRestartClick(view);
            }
        });

        Button openBankFileButton = (Button) findViewById(R.id.customBank);
        openBankFileButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view) {
                OnOpenBankFileClick(view);
            }
        });

        checkNotificationsPermissions();
    }


    @Override
    protected void onStart()
    {
        super.onStart();
        initUiSetup(false);
    }

    private boolean isPlayerRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (PlayerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**!
     * Reconnect running player
     */
    private boolean reconnectPlayerService()
    {
        if(isPlayerRunning())
        {
            Log.d(LOG_TAG, "Player is running, reconnect");
            bindPlayerService();
            return true;
        }
        else
            Log.d(LOG_TAG, "Player is NOT running, do nothing");

        return false;
    }

    private void bindPlayerService()
    {
        if(!m_bound)
        {
            Log.d(LOG_TAG, "bind is not exist, making a bind");
            // Bind to LocalService
            Intent intent = new Intent(this, PlayerService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unbind from the service
        if (m_bound) {
            unbindService(mConnection);
            m_bound = false;
        }
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {

            /***
             * TODO: Rpleace this crap with properly made settings box
             * (this one can't receive changed value for "input" EditText field)
             */

            AlertDialog.Builder alert = new AlertDialog.Builder(this);

            alert.setTitle("Setup");
            alert.setMessage("Gaining level");

            // Set an EditText view to get user input
            final EditText input = new EditText(this);
            input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            alert.setView(input);

            if(m_bound) {
                input.setText(String.format(Locale.getDefault(), "%g", AppSettings.getGaining()));
            }

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String txtvalue = input.getText().toString();
                    onGainUpdate(Double.parseDouble(txtvalue), false);
                }
            });

            alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Canceled.
                }
            });

            alert.show();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    public void OnPlayClick(View view)
    {
        if(m_bound)
        {
            if(!m_service.hasLoadedMusic())
            {
                if(!m_service.isReady())
                    m_service.initPlayer();
                processMusicFileLoadMusic(false);
            }

            if(!m_service.hasLoadedMusic())
                return;

            if(!m_service.isPlaying()) {
                playerServiceStart();
                seekerStart();
            }

            m_service.togglePlayPause();
            if(!m_service.isPlaying()) {
                seekerStop();
                playerServiceStop();
            }
        }
    }

    public void OnRestartClick(View view)
    {
        if(m_bound) {
            if(!m_service.hasLoadedMusic())
                return;
            if(!m_service.isPlaying()) {
                playerServiceStart();
                seekerStart();
            }
            m_service.playerRestart();
        }
    }

    private boolean checkNotificationsPermissions()
    {
        if(Build.VERSION.SDK_INT < 33)
            return false; /* Has no effect, it's a brand-new permission type on Android 13 */

        final int grant = PackageManager.PERMISSION_GRANTED;
        final String postNotify = Manifest.permission.POST_NOTIFICATIONS;

        if(ContextCompat.checkSelfPermission(this, postNotify) == grant)
            return false;

        // Should we show an explanation?
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, postNotify))
        {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Permission denied");
            b.setMessage("Sorry, but permission is denied!\n"+
                    "Please, check the Post Notifications access permission to the application!");
            b.setNegativeButton(android.R.string.ok, null);
            b.show();
            return true;
        }
        else
            ActivityCompat.requestPermissions(this, new String[] { postNotify }, NOTIFICATIONS_PERMISSION_FOR_INTENT);

        return true;
    }

    private boolean checkFilePermissions(int requestCode)
    {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN)
            return false;

        final int grant = PackageManager.PERMISSION_GRANTED;
        final String exStorage = Manifest.permission.READ_EXTERNAL_STORAGE;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return false; /* Has no effect, the manage file storage permission is used instead of this */

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if(ContextCompat.checkSelfPermission(this, exStorage) == grant)
                Log.d(LOG_TAG, "File permission is granted");
            else
                Log.d(LOG_TAG, "File permission is revoked");
        }

        if(ContextCompat.checkSelfPermission(this, exStorage) == grant)
            return false;

        // Should we show an explanation?
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, exStorage))
        {
            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Permission denied");
            b.setMessage("Sorry, but permission is denied!\n"+
                    "Please, check the External Storage access permission to the application!");
            b.setNegativeButton(android.R.string.ok, null);
            b.show();

            return true;
        }
        else
        {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this, new String[] { exStorage }, requestCode);
            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(grantResults.length == 0)
            return;
        if(!permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE))
            return;
        if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            return;

        if(requestCode == READ_PERMISSION_FOR_BANK)
            openBankDialog();
        else if (requestCode == READ_PERMISSION_FOR_MUSIC)
            openMusicFileDialog();
        else if (requestCode == READ_PERMISSION_FOR_INTENT)
            handleFileIntent();
    }

    private static final int PICK_MUSIC_FILE = 1;
    private static final int PICK_BANK_FILE = 2;

    private void openBankFileNEW()
    {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R)
            return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("*/*");
        // Optionally, specify a URI for the file that should appear in the
        // system file picker when it loads.
        // intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        startActivityForResult(intent, PICK_BANK_FILE);
    }

    private void openMusicFileNEW()
    {
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R)
            return;

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
//        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setType("*/*");
        // Optionally, specify a URI for the file that should appear in the
        // system file picker when it loads.
        // intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);
        startActivityForResult(intent, PICK_MUSIC_FILE);
    }

    private String toTempFile(Uri inUri, String tempFileName)
    {
        String tempFilePath = this.getFilesDir().getAbsolutePath() + "/" + tempFileName;
        File tempFile = new File(this.getFilesDir().getAbsolutePath(), tempFileName);

        //Copy URI contents into temporary file.
        try
        {
            tempFile.createNewFile();

            FileOutputStream output = new FileOutputStream(tempFile);
            InputStream input = this.getContentResolver().openInputStream(inUri);

            byte[] buffer = new byte[1024];
            int n = 0;

            while(-1 != (n = input.read(buffer)))
            {
                output.write(buffer, 0, n);
            }

            try
            {
                input.close();
            } catch(IOException ioe)
            {
                //skip
            }

            try
            {
                output.close();
            }
            catch(IOException ioe)
            {
                //skip
            }
        }
        catch (IOException e)
        {
            //Log Error
        }

        return tempFilePath;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);

        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R)
            return;

        if(data == null)
            return;

        Uri uri = data.getData();

        if(uri == null)
            return;

        // getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

        if(requestCode == PICK_BANK_FILE)
        {
            String tempBank = toTempFile(uri, "current_bank.wopl");

            m_lastBankPath = tempBank;
            AppSettings.setBankPath(m_lastBankPath);

            TextView cbl = (TextView) findViewById(R.id.bankFileName);
            if(!m_lastBankPath.isEmpty())
            {
                File f = new File(m_lastBankPath);
                cbl.setText(f.getName());
            }
            else
                cbl.setText(R.string.noCustomBankLabel);

            if(m_bound)
                m_service.openBank(m_lastBankPath);
            else
                m_needBankReload = true;
        }
        else if(requestCode == PICK_MUSIC_FILE)
        {
            String tempMusik = toTempFile(uri, "current_music.bin");
            processMusicFile(tempMusik, m_lastPath);
        }
    }

    public void OnOpenBankFileClick(View view)
    {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
        {
            openBankFileNEW();
            return;
        }

        // Here, thisActivity is the current activity
        if(checkFilePermissions(READ_PERMISSION_FOR_BANK))
            return;
        openBankDialog();
    }

    public void openBankDialog()
    {
        File file = new File(m_lastBankPath);
        OpenFileDialog fileDialog = new OpenFileDialog(this)
                .setFilter(".*\\.wopl")
                .setCurrentDirectory(m_lastBankPath.isEmpty() ?
                        Environment.getExternalStorageDirectory().getPath() :
                        file.getParent())
                .setOpenDialogListener(new OpenFileDialog.OpenDialogListener()
                {
                    @Override
                    public void OnSelectedFile(Context ctx, String fileName, String lastPath)
                    {
                        m_lastBankPath = fileName;
                        AppSettings.setBankPath(m_lastBankPath);

                        TextView cbl = (TextView) findViewById(R.id.bankFileName);
                        if(!m_lastBankPath.isEmpty())
                        {
                            File f = new File(m_lastBankPath);
                            cbl.setText(f.getName());
                        }
                        else
                        {
                            cbl.setText(R.string.noCustomBankLabel);
                        }
                        if(m_bound)
                            m_service.openBank(m_lastBankPath);
                    }

                    @Override
                    public void OnSelectedDirectory(Context ctx, String lastPath) {}
                });
        fileDialog.show();
    }

    public void OnOpenFileClick(View view)
    {
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
        {
            openMusicFileNEW();
            return;
        }

        // Here, thisActivity is the current activity
        if(checkFilePermissions(READ_PERMISSION_FOR_MUSIC))
            return;
        openMusicFileDialog();
    }

    public void openMusicFileDialog()
    {
        OpenFileDialog fileDialog = new OpenFileDialog(this)
                .setFilter(".*\\.mid|.*\\.midi|.*\\.kar|.*\\.rmi|.*\\.imf|.*\\.cmf|.*\\.mus|.*\\.xmi")
                .setCurrentDirectory(m_lastPath)
                .setOpenDialogListener(new OpenFileDialog.OpenDialogListener()
                {
                    @Override
                    public void OnSelectedFile(Context ctx, String fileName, String lastPath) {
                        processMusicFile(fileName, lastPath);
                    }

                    @Override
                    public void OnSelectedDirectory(Context ctx, String lastPath) {}
                });
        fileDialog.show();
    }

    private void handleFileIntent()
    {
        Intent intent = getIntent();
        if(intent != null && intent.getScheme() != null)
        {
            String tempMusik = toTempFile(intent.getData(), "current_music.bin");
            processMusicFile(tempMusik, m_lastPath);
        }
    }

    private void processMusicFile(String fileName, String lastPath)
    {
        boolean wasPlay = false;
        Toast.makeText(getApplicationContext(), fileName, Toast.LENGTH_LONG).show();
        TextView tv = (TextView) findViewById(R.id.currentFileName);
        tv.setText(fileName);

        m_lastPath = lastPath;
        m_lastMusicPath = fileName;

        if(m_bound)
        {
            //Abort previously playing state
            wasPlay = m_service.isPlaying();
            if (m_service.isPlaying())
                m_service.playerStop();

            if (!m_service.isReady())
            {
                if (!m_service.initPlayer())
                {
                    m_service.playerStop();
                    m_service.unInitPlayer();
                    AlertDialog.Builder b = new AlertDialog.Builder(Player.this);
                    b.setTitle("Failed to initialize player");
                    b.setMessage("Can't initialize player because of " + m_service.getLastError());
                    b.setNegativeButton(android.R.string.ok, null);
                    b.show();
                    return;
                }
            }
        }
        else
        {
            m_needMusicReload = true;
        }

        m_setup.edit().putString("lastPath", m_lastPath).apply();
        m_setup.edit().putString("lastMusicPath", m_lastMusicPath).apply();
        processMusicFileLoadMusic(wasPlay);

        bindPlayerService();
    }

    private void processMusicFileLoadMusic(boolean wasPlay)
    {
        if(m_bound)
        {
            //Reload bank for a case if CMF file was passed that cleans custom bank
            m_service.reloadBank();
            if (!m_service.openMusic(m_lastMusicPath)) {
                AlertDialog.Builder b = new AlertDialog.Builder(Player.this);
                b.setTitle("Failed to open file");
                b.setMessage("Can't open music file because of " + m_service.getLastError());
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
            } else {
                SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
                musPos.setMax(m_service.getSongLength());
                musPos.setProgress(0);
                if (wasPlay)
                    m_service.playerStart();
            }
        }
    }

    void onChipsCountUpdate(int chipsCount, boolean silent)
    {
        int fourOpsMax = chipsCount * 6;

        if(chipsCount < 1) {
            chipsCount = 1;
        } else if(chipsCount > 100) {
            chipsCount = 100;
        }

        m_chipsCount = chipsCount;
        AppSettings.setChipsCount(m_chipsCount);
        if(m_bound && !silent) {
            m_service.applySetup();
            Log.d(LOG_TAG, String.format(Locale.getDefault(), "Chips: Written=%d", m_chipsCount));
        }

        if(m_fourOpsCount > fourOpsMax) {
            onFourOpsCountUpdate(m_fourOpsCount, silent);
        }

        TextView numChipsCounter = (TextView)findViewById(R.id.numChipsCount);
        numChipsCounter.setText(String.format(Locale.getDefault(), "%d", m_chipsCount));
    }

    @SuppressLint("SetTextI18n")
    void onFourOpsCountUpdate(int fourOpsCount, boolean silent)
    {
        int fourOpsMax = m_chipsCount * 6;

        if(fourOpsCount > fourOpsMax) {
            fourOpsCount = fourOpsMax;
        } else if(fourOpsCount < -1) {
            fourOpsCount = -1;
        }

        m_fourOpsCount = fourOpsCount;
        AppSettings.setFourOpChanCount(fourOpsCount);

        if(m_bound && !silent) {
            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: Written=%d", fourOpsCount));
            m_service.applySetup();
        }

        TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
        if(m_fourOpsCount >= 0)
            num4opCounter.setText(String.format(Locale.getDefault(), "%d", m_fourOpsCount));
        else
            num4opCounter.setText("<Auto>");
    }

    void onGainUpdate(double gainLevel, boolean silent)
    {
        gainLevel = round(gainLevel, 1);
        AppSettings.setGaining(gainLevel);
        if(m_bound && !silent) {
            m_service.gainingSet(gainLevel);
        }
        TextView gainFactor = (TextView)findViewById(R.id.gainFactor);
        gainFactor.setText(String.format(Locale.getDefault(), "%.1f", gainLevel));
    }
}

