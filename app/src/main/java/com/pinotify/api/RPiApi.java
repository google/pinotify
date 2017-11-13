// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.pinotify.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.pinotify.RPiBluetoothConnection;
import com.pinotify.activities.ConfigActivity;

public class RPiApi {
    // These values can range 0 - 15. Both values cannot be zero.
    private static final byte TIME_ON_SEC = 2;
    private static final byte TIME_OFF_SEC = 2;
    private static final byte BLINK_MESSAGE = TIME_ON_SEC << 4 | TIME_OFF_SEC;
    private static final byte DISMISS_MSG = 0;

    public static interface Dismisser {
        void Dismiss(Context context);
    }

    public static void setDismisser(final Context context, final Dismisser dismisser) {
        RPiBluetoothConnection.setReceiver(new RPiBluetoothConnection.Receiver() {
            @Override
            public void receivedByte(byte b) {
                if (b == DISMISS_MSG) {
                    dismisser.Dismiss(context);
                }
            }
        });
    }

    public static void startBlinking(Context context) {
        SharedPreferences sharedPrefs = context.getSharedPreferences(ConfigActivity
                .PINOTIFY_PREFS, 0);
        if (!sharedPrefs.getBoolean(ConfigActivity.PREF_STARTED, false)) {
            return;
        }
        RPiBluetoothConnection.sendMessage(context, BLINK_MESSAGE);
    }
}
