package ru.wohlsoft.adlmidiplayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
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

    private PlayerService m_service;
    private volatile boolean m_bound = false;
    private volatile boolean m_banksListLoaded = false;
    private volatile boolean m_fourOpsCountLoaded = false;
    private volatile boolean m_uiLoaded = false;

    private SharedPreferences   m_setup = null;

    private String              m_lastPath = Environment.getExternalStorageDirectory().getPath();
    private String              m_lastBankPath = "";

    private int                 m_chipsCount = 2;
    private int                 m_fourOpsCount = -1;

    private BroadcastReceiver mBroadcastReceiver= new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentType = intent.getStringExtra("INTENT_TYPE");
            if(intentType.equalsIgnoreCase("SEEKBAR_RESULT")){
                int percentage = intent.getIntExtra("PERCENTAGE", -1);
                SeekBar musPos = findViewById(R.id.musPos);
                if(percentage >= 0)
                    musPos.setProgress(percentage);
            }
        }
    };

    public static double round(double value, int places) {
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
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            PlayerService.LocalBinder binder = (PlayerService.LocalBinder) service;
            m_service = binder.getService();
            m_bound = true;
            initUiSetup();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            m_bound = false;
        }
    };

    private void initUiSetup()
    {
        if (m_bound) {
            m_service.loadSetup(m_setup);
            boolean isPlaying = m_service.isPlaying();

            if(isPlaying) {
                seekerStart();
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Already playing", Toast.LENGTH_SHORT);
                toast.show();
            }

            if(m_uiLoaded)
                return;

            /*
             * Music position seeker
             */
            SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
            musPos.setMax(m_service.getSongLength());
            musPos.setProgress(m_service.getPosition());
            musPos.setProgress(0);
            musPos.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                //private double dstPos = 0;
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
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
            m_lastBankPath = m_service.getBankPath();
            if(!m_lastBankPath.isEmpty()) {
                File f = new File(m_lastBankPath);
                cbl.setText(f.getName());
            } else {
                cbl.setText("<No custom bank>");
            }


            /*
             * Embedded banks list combobox
             */
            //Fill bank number box
            List<String> spinnerArray = m_service.getEmbeddedBanksList();
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                    this, android.R.layout.simple_spinner_item, spinnerArray);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            Spinner sItems = (Spinner) findViewById(R.id.bankNo);
            sItems.setAdapter(adapter);
            sItems.setSelection(m_service.getEmbeddedBank());

            m_banksListLoaded = false;
            sItems.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                                           View itemSelected, int selectedItemPosition, long selectedId) {
                    if(!m_banksListLoaded)
                    {
                        m_banksListLoaded = true;
                        return;
                    }

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
            useCustomBank.setChecked(m_service.getUseCustomBank());
            useCustomBank.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
                "Java OPL3 (broken rhythm-mode)"
            };

            ArrayAdapter<String> adapterEMU = new ArrayAdapter<String>(
                    this, android.R.layout.simple_spinner_item, emulatorItems);
            adapterEMU.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sEmulator.setAdapter(adapterEMU);
            sEmulator.setSelection(m_service.getEmulator());

            sEmulator.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                                           View itemSelected, int selectedItemPosition, long selectedId) {
                    if(m_bound)
                        m_service.setEmulator(selectedItemPosition);
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
                "9X (SB16)", "DMX (Fixed AM)", "Apogee (Fixed AM)", "Audio Interfaces Library",
                "9X (Generic FM)"
            };

            ArrayAdapter<String> adapterVM = new ArrayAdapter<String>(
                    this, android.R.layout.simple_spinner_item, volumeModelItems);
            adapterVM.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sVolModel.setAdapter(adapterVM);
            sVolModel.setSelection(m_service.getVolumeModel());

            sVolModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                                           View itemSelected, int selectedItemPosition, long selectedId) {
                    if(m_bound)
                        m_service.setVolumeModel(selectedItemPosition);
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            });


            /*
             * Deep Tremolo checkbox
             */
            CheckBox deepTremolo = (CheckBox)findViewById(R.id.deepTremolo);
            deepTremolo.setChecked(m_service.getDeepTremolo());
            deepTremolo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            deepVibrato.setChecked(m_service.getDeepVibrato());
            deepVibrato.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            scalableMod.setChecked(m_service.getScalableModulation());
            scalableMod.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            runAtPcmRate.setChecked(m_service.getRunAtPcmRate());
            runAtPcmRate.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
            fullPanningStereo.setChecked(m_service.getFullPanningStereo());
            fullPanningStereo.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if(m_bound)
                        m_service.setFullPanningStereo(isChecked);
                    Toast toast = Toast.makeText(getApplicationContext(),
                            "Full-Panning toggled!", Toast.LENGTH_SHORT);
                    toast.show();
                }
            });

            /*
             * Chips count
             */
            Button numChipsMinus = (Button) findViewById(R.id.numChipsMinus);
            numChipsMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int g = m_chipsCount;
                    g--;
                    if(g < 1) {
                        return;
                    }
                    onChipsCountUpdate(g, false);
                }
            });

            Button numChipsPlus = (Button) findViewById(R.id.numChipsPlus);
            numChipsPlus.setOnClickListener(new View.OnClickListener() {
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

            onChipsCountUpdate(m_service.getChipsCount(), true);


            /*
             * Number of four-operator channels
             */
            Button num4opChansMinus = (Button) findViewById(R.id.num4opChansMinus);
            num4opChansMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int g = m_fourOpsCount;
                    g--;
                    if(g < -1) {
                        return;
                    }
                    onFourOpsCountUpdate(g, false);
                }
            });

            Button num4opChansPlus = (Button) findViewById(R.id.num4opChansPlus);
            num4opChansPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int curFourOpMax = m_chipsCount * 6;
                    int g = m_fourOpsCount;
                    g++;
                    if(g > curFourOpMax) {
                        return;
                    }
                    onFourOpsCountUpdate(g, false);
                }
            });

            onFourOpsCountUpdate(m_service.getFourOpChanCount(), true);

            /*
             * Gain level
             */
            Button gainMinusMinus = (Button) findViewById(R.id.gainFactorMinusMinus);
            gainMinusMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(m_bound) {
                        double gain = m_service.gainingGet();
                        gain -= 1.0;
                        if(gain < 0.1)
                            gain += 1.0;
                        onGainUpdate(gain, false);
                    }
                }
            });

            Button gainMinus = (Button) findViewById(R.id.gainFactorMinus);
            gainMinus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(m_bound) {
                        double gain = m_service.gainingGet();
                        gain -= 0.1;
                        if(gain < 0.1)
                            gain += 0.1;
                        onGainUpdate(gain, false);
                    }
                }
            });

            Button gainPlus = (Button) findViewById(R.id.gainFactorPlus);
            gainPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(m_bound) {
                        double gain = m_service.gainingGet();
                        gain += 0.1;
                        onGainUpdate(gain, false);
                    }
                }
            });

            Button gainPlusPlus = (Button) findViewById(R.id.gainFactorPlusPlus);
            gainPlusPlus.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(m_bound) {
                        double gain = m_service.gainingGet();
                        gain += 1.0;
                        onGainUpdate(gain, false);
                    }
                }
            });

            onGainUpdate(m_service.gainingGet(), true);



            /* *******Everything UI related has been initialized!****** */
            m_uiLoaded = true;

            // Try to load external file if requested
            handleFileIntent();
        }
    }

    private void playerServiceStart()
    {
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
        intent.setAction(PlayerService.ACTION_STOP_FOREGROUND_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        m_setup = getPreferences(Context.MODE_PRIVATE);

        m_lastPath              = m_setup.getString("lastPath", m_lastPath);

        Button quitb = (Button) findViewById(R.id.quitapp);
        quitb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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

        Button openBankFileButton = (Button) findViewById(R.id.customBank);
        openBankFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                OnOpenBankFileClick(view);
            }
        });
    }


    @Override
    protected void onStart() {
        super.onStart();
        // Bind to LocalService
        Intent intent = new Intent(this, PlayerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
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

            /* **
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
                input.setText(Double.toString(m_service.gainingGet()));
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
        if(m_bound) {
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

    private boolean checkFilePermissions(int requestCode)
    {
        if (Build.VERSION.SDK_INT >= 23)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.d(LOG_TAG, "File permission is granted");
            } else {
                Log.d(LOG_TAG, "File permission is revoked");
            }
        }

        if( (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) )
        {
            // Should we show an explanation?
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE))
            {
                // Show an expanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                AlertDialog.Builder b = new AlertDialog.Builder(this);
                b.setTitle("Permission denied");
                b.setMessage("Sorry, but permission is denied!\n"+
                             "Please, check the Read Extrnal Storage permission to application!");
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
                return true;
            }
            else
            {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE}, requestCode);
                //MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
            return true;
        }

        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 &&
            permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE) &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            if (requestCode == READ_PERMISSION_FOR_BANK) {
                openBankDialog();
            } else if (requestCode == READ_PERMISSION_FOR_MUSIC) {
                openMusicFileDialog();
            } else if (requestCode == READ_PERMISSION_FOR_INTENT) {
                handleFileIntent();
            }
        }
    }

    public void OnOpenBankFileClick(View view) {
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
                    public void OnSelectedFile(String fileName, String lastPath) {
                        m_lastBankPath = fileName;

                        TextView cbl = (TextView) findViewById(R.id.bankFileName);
                        if(!m_lastBankPath.isEmpty()) {
                            File f = new File(m_lastBankPath);
                            cbl.setText(f.getName());
                        } else {
                            cbl.setText("<No custom bank>");
                        }
                        if(m_bound)
                            m_service.openBank(m_lastBankPath);
                    }
                });
        fileDialog.show();
    }

    public void OnOpenFileClick(View view) {
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
                    public void OnSelectedFile(String fileName, String lastPath) {
                        processMusicFile(fileName, lastPath);
                    }
                });
        fileDialog.show();
    }

    private void handleFileIntent()
    {
        Intent intent = getIntent();
        String scheme = intent.getScheme();
        if(scheme != null)
        {
            if(checkFilePermissions(READ_PERMISSION_FOR_INTENT))
                return;
            if(scheme.equals(ContentResolver.SCHEME_FILE))
            {
                Uri url = intent.getData();
                if(url != null)
                {
                    Log.d(LOG_TAG, "Got a file: " + url + ";");
                    String fileName = url.getPath();
                    processMusicFile(fileName, m_lastPath);
                }
            }
            else if(scheme.equals(ContentResolver.SCHEME_CONTENT))
            {
                Uri url = intent.getData();
                if(url != null)
                {
                    Log.d(LOG_TAG, "Got a content: " + url + ";");
                    String fileName = url.getPath();
                    processMusicFile(fileName, m_lastPath);
                }
            }
        }
    }

    private void processMusicFile(String fileName, String lastPath)
    {
        Toast.makeText(getApplicationContext(), fileName, Toast.LENGTH_LONG).show();
        TextView tv = (TextView) findViewById(R.id.currentFileName);
        tv.setText(fileName);

        m_lastPath = lastPath;
        if(m_bound)
        {
            //Abort previously playing state
            boolean wasPlay = m_service.isPlaying();
            if(m_service.isPlaying())
                m_service.playerStop();
            String m_lastFile;
            if(!m_service.isReady())
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
                    m_lastFile = "";
                    return;
                }
            }
            m_lastFile = fileName;
            m_setup.edit().putString("lastPath", m_lastPath).apply();

            //Reload bank for a case if CMF file was passed that cleans custom bank
            m_service.reloadBank();
            if (!m_service.openMusic(m_lastFile)) {
                AlertDialog.Builder b = new AlertDialog.Builder(Player.this);
                b.setTitle("Failed to open file");
                b.setMessage("Can't open music file because of " + m_service.getLastError());
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
                m_lastFile = "";
            } else {
                SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
                musPos.setMax(m_service.getSongLength());
                musPos.setProgress(0);
                if (wasPlay)
                    m_service.playerStart();
            }
        } else {
            Log.d(LOG_TAG, "Woops, it's NOT BOUND!");
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
        if(m_bound && !silent) {
            m_service.setChipsCount(m_chipsCount);
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

        if(m_bound && !silent) {
            m_service.setFourOpChanCount(fourOpsCount);
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
        if(m_bound && !silent) {
            m_service.gainingSet(gainLevel);
        }
        TextView gainFactor = (TextView)findViewById(R.id.gainFactor);
        gainFactor.setText(String.format(Locale.getDefault(), "%.1f", gainLevel));
    }
}
