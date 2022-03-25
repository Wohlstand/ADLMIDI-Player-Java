package ru.wohlsoft.adlmidiplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Copyright 2011 Matthew Gaunt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 * NOTE:
 * This code has been briefly tested on a Nexus One running Android
 * version 2.2.2 and a G1 running version 1.6
 *
 *
 * @author Matthew Gaunt - http://www.gauntface.co.uk/
 *
 */

public class HardButtonReceiver extends BroadcastReceiver
{
    private final static String TAG = "gauntface";

    private PlayerService mButtonListener = null;

    public HardButtonReceiver() {
        super();
    }

    public HardButtonReceiver(PlayerService buttonListener) {
        super();

        mButtonListener = buttonListener;
    }

    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.d(TAG, "HardButtonReceiver: Button press received");
        if(mButtonListener != null)
        {
            /*
             * We abort the broadcast to prevent the event being passed down
             * to other apps (i.e. the Music app)
             */
            abortBroadcast();

            // Pull out the KeyEvent from the intent
            KeyEvent key = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);

            // This is just some example logic, you may want to change this for different behaviour
            if(key != null && key.getAction() == KeyEvent.ACTION_UP)
            {
                int keycode = key.getKeyCode();

                // These are examples for detecting key presses on a Nexus One headset
                if(keycode == KeyEvent.KEYCODE_MEDIA_NEXT)
                {
                    mButtonListener.onNextButtonPress();
                }
                else if(keycode == KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                {
                    mButtonListener.onPrevButtonPress();
                }
                else if(keycode == KeyEvent.KEYCODE_HEADSETHOOK)
                {
                    mButtonListener.onPlayPauseButtonPress();
                }
            }
        }
    }

    public interface HardButtonListener {
        void onPrevButtonPress();
        void onNextButtonPress();
        void onPlayPauseButtonPress();
    }
}