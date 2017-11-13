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

package com.pinotify.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.pinotify.R;
import com.pinotify.StateController;

public class MessageActivity extends Activity {
    private static final String TAG = "MessageActivity";

    // Intent extras for MessageActivity.
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";

    private TextView messageView;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } catch (Exception e) {
            // Hiding the decor view is best-effort.
            e.printStackTrace();
        }

        //init layout parameters
        messageView = (TextView) findViewById(R.id.messageContainer);
        showActiveMessage();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /**
     * @param intent The intent to handle.
     * @return true If we had to start the login flow, false otherwise.
     */
    private boolean handleIntent(Intent intent) {
        if (intent == null) {
            return false;
        }

        if (intent.getBooleanExtra(EXTRA_MESSAGE, false)) {
            showActiveMessage();
            return true;
        }
        return false;
    }

    private void showActiveMessage() {
        final int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
        final Window win = getWindow();
        // We got a new message! Let's display it.
        // TODO: display more details.
        StateController.ActiveMessage message = StateController.getActiveMessage(this);
        if (message == null) {
            win.clearFlags(flags);
            messageView.setText("");
        } else {
            win.addFlags(flags);
            messageView.setText(message.message);
        }
    }
}
