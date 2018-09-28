package ru.wohlsoft.adlmidiplayer;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.app.ActivityCompat;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Player extends AppCompatActivity {
    //private int                 MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    final String LOG_TAG = "PlayerService";
    private boolean             permission_readAllowed = false;

    private PlayerService m_service;
    private volatile boolean m_bound = false;
    private volatile boolean m_banksListLoaded = false;
    private volatile boolean m_fourOpsCountLoaded = false;
    private volatile boolean m_uiLoaded = false;

    private SharedPreferences   m_setup = null;

    private String              m_lastFile = "";
    private String              m_lastPath = Environment.getExternalStorageDirectory().getPath();
    private String              m_lastBankPath = "";

    private BroadcastReceiver mBroadcastReceiver= new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentType = intent.getStringExtra("INTENT_TYPE");
            if(intentType.equalsIgnoreCase("SEEKBAR_RESULT")){
                int percentage = intent.getIntExtra("PERCENTAGE", -1);
                SeekBar musPos = (SeekBar) findViewById(R.id.musPos);
                if(percentage >= 0)
                    musPos.setProgress(percentage);
            }
        }
    };

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

            /***
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

            /***
             * Filename title
             */
            // Example of a call to a native method
            TextView tv = (TextView) findViewById(R.id.sample_text);
            tv.setText(PlayerService.stringFromJNI());
            if(isPlaying) {
                tv.setText(m_service.getCurrentMusicPath());
            }

            /***
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


            /***
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

            /*****
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
                                                     }
            );


            /*****
             * Volume model combo-box
             */
            Spinner sVolModel = (Spinner) findViewById(R.id.volumeRangesModel);
            final String[] volumeModelItems = {"[Auto]", "Generic", "CMF", "DMX", "Apogee", "9X" };

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


            /*****
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
                                                   }
            );

            /*****
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
                                                   }
            );

            /*****
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
                                                                   "Scalable modulation toggled toggled!", Toast.LENGTH_SHORT);
                                                           toast.show();
                                                       }
                                                   }
            );

            /*****
             * Rhythm-Mode drums checkbox
             */
            CheckBox adlDrums = (CheckBox)findViewById(R.id.adlibDrumsMode);
            adlDrums.setChecked(m_service.getForceRhythmMode());
            adlDrums.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                                    @Override
                                                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                                        if(m_bound)
                                                            m_service.setForceRhythmMode(isChecked);
                                                        Toast toast = Toast.makeText(getApplicationContext(),
                                                                "AdLib percussion mode toggled!", Toast.LENGTH_SHORT);
                                                        toast.show();
                                                    }
                                                }
            );


            /*****
             * Chips count spinner
             */
            NumberPicker numChips = (NumberPicker)findViewById(R.id.numChips);
            numChips.setMinValue(1);
            numChips.setMaxValue(100);
            numChips.setValue(m_service.getChipsCount());
            numChips.setWrapSelectorWheel(false);
            TextView numChipsCounter = (TextView)findViewById(R.id.numChipsCount);
            numChipsCounter.setText(String.format(Locale.getDefault(), "%d", m_service.getChipsCount()));

            numChips.setOnValueChangedListener(new NumberPicker.OnValueChangeListener()
            {
                @Override
                public void onValueChange(NumberPicker picker, int oldVal, int newVal)
                {
                    int chipsCount = picker.getValue();
                    int old4opsCountVal = 0;
                    int new4opsCountVal = 0;
                    int fourOpsMax = 0;
                    if(m_bound) {
                        old4opsCountVal = m_service.getFourOpChanCount();
                        m_service.setChipsCount(chipsCount);
                        fourOpsMax = m_service.getFourOpMax();
                        new4opsCountVal = m_service.getFourOpChanCount();
                        m_service.applySetup();
                    }
                    if(chipsCount <=1) {
                        chipsCount = 1;
                        picker.setValue(1);
                    } else if(chipsCount >100) {
                        chipsCount = 100;
                        picker.setValue(100);
                    }
                    NumberPicker num4opChannels = (NumberPicker)findViewById(R.id.num4opChans);
                    if(old4opsCountVal > fourOpsMax) {
                        num4opChannels.setValue( new4opsCountVal + 1);
                        TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
                        if(new4opsCountVal >= 0)
                            num4opCounter.setText(String.format(Locale.getDefault(), "%d", new4opsCountVal));
                        else
                            num4opCounter.setText(String.format(Locale.getDefault(), "<Auto>"));
                    }
                    num4opChannels.setMaxValue(fourOpsMax + 1);

                    TextView numChipsCounter = (TextView)findViewById(R.id.numChipsCount);
                    numChipsCounter.setText(String.format(Locale.getDefault(), "%d", chipsCount));
                }
            });

            /*****
             * Number of four-operator channels spinner
             */
            NumberPicker num4opChannels = (NumberPicker)findViewById(R.id.num4opChans);
            num4opChannels.setFormatter(new NumberPicker.Formatter() {
                @Override
                public String format(int index) {
                    return Integer.toString(index - 1);
                }
            });
            num4opChannels.setMinValue(0);
            num4opChannels.setMaxValue(m_service.getFourOpMax() + 1);
            num4opChannels.setWrapSelectorWheel(false);
            TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
            int curFourOpsCount = m_service.getFourOpChanCount();
            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: Restored old=%d", curFourOpsCount));
            if(curFourOpsCount >= 0)
                num4opCounter.setText(String.format(Locale.getDefault(), "%d", curFourOpsCount));
            else
                num4opCounter.setText(String.format(Locale.getDefault(), "<Auto>"));

            num4opChannels.setValue(curFourOpsCount + 1);
            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: setValue=%d", curFourOpsCount + 1));

            num4opChannels.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                    if(!m_fourOpsCountLoaded) {
                        m_fourOpsCountLoaded = true;
                        return;
                    }
                    int count = ((int)picker.getValue() - 1);
                    Log.d(LOG_TAG, String.format(Locale.getDefault(),
                            "4ops: picker.value=%d, count %d, o=%d, n=%d", picker.getValue(), count, oldVal, newVal
                            )
                    );
                    int curFourOpMax = 0;
                    if(m_bound)
                        curFourOpMax = m_service.getFourOpMax();
                    if(count <= -1) {
                        count = -1;
                        picker.setValue(0);
                    } else if(count > curFourOpMax) {
                        count = curFourOpMax;
                        picker.setValue(count + 1);
                    }

                    if(m_bound) {
                        m_service.setFourOpChanCount(count);
                        Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: Written=%d", count));
                        m_service.applySetup();
                    }
                    TextView num4opCounter = (TextView)findViewById(R.id.num4opChansCount);
                    if(count >= 0)
                        num4opCounter.setText(String.format(Locale.getDefault(), "%d", count));
                    else
                        num4opCounter.setText(String.format(Locale.getDefault(), "<Auto>"));
                }
            });

//            num4opChannels.setValue(curFourOpsCount + 1);
//            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: setValue=%d", curFourOpsCount + 1));
//            num4opChannels.setValue(curFourOpsCount + 2);
//            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: setValue=%d", curFourOpsCount + 2));
//            num4opChannels.setValue(curFourOpsCount + 1);
//            Log.d(LOG_TAG, String.format(Locale.getDefault(), "4ops: setValue=%d", curFourOpsCount + 1));

            /* ========================================================================
             * Workaround for a drawing of first element of NumberPicker bug:
             * https://stackoverflow.com/questions/17708325/android-numberpicker-with-formatter-doesnt-format-on-first-rendering
             */
            try {
                Method method = num4opChannels.getClass().getDeclaredMethod("changeValueByOne", boolean.class);
                method.setAccessible(true);
                method.invoke(num4opChannels, true);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            /* ======================================================================== */

            m_uiLoaded = true;
        }
    }

    private void playerServiceStart()
    {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerService.ACTION_START_FOREGROUND_SERVICE);
        startService(intent);
    }

    private void playerServiceStop()
    {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerService.ACTION_STOP_FOREGROUND_SERVICE);
        startService(intent);
    }

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

                Log.d(LOG_TAG, "Quit: New Handler");
                Handler handler = new Handler();
                Log.d(LOG_TAG, "Quit: Wait until call Exit");
                handler.postDelayed(new Runnable(){
                    @Override
                    public void run(){
                        Log.d(LOG_TAG, "Quit: Do Exit NOW!");
                        System.exit(0);
                    }
                }, 3000);
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
                input.setText(Double.toString(m_service.gainingGet()));
            }

            alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String txtvalue = input.getText().toString();
                    if(m_bound) {
                        double value = Double.parseDouble(txtvalue);
                        m_service.setGaining(value);
                    }
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

    private boolean checkFilePermissions()
    {
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
                        "Please, check permissions to application!");
                b.setNegativeButton(android.R.string.ok, null);
                b.show();
                return false;
            }
            else
            {
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
            return false;
        }

        if(!permission_readAllowed)
        {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Failed permissions");
            b.setMessage("Can't open file dialog because permission denied. Restart application to take effects!");
            b.setNegativeButton(android.R.string.ok, null);
            b.show();
            return false;
        }

        return true;
    }

    public void OnOpenBankFileClick(View view) {
        // Here, thisActivity is the current activity
        if(!checkFilePermissions())
            return;

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
        if(!checkFilePermissions())
            return;

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

                        m_lastPath = lastPath;
                        if(m_bound) {
                            //Abort previously playing state
                            boolean wasPlay = m_service.isPlaying();
                            if (m_service.isPlaying())
                                m_service.playerStop();
                            if (!m_service.initPlayer()) {
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
                            m_lastFile = fileName;
                            m_setup.edit().putString("lastPath", m_lastPath).apply();

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
                        }
                    }
                });
        fileDialog.show();
    }
}
