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

package com.pinotify;

import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import none.pinotify_api.PinotifyApi;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // Intent extras for MainActivity.
    public static final String EXTRA_LOGIN = "LOGIN";
    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";
    public static final String EXTRA_DATE = "EXTRA_DATE";
    public static final String EXTRA_SENDER = "EXTRA_SENDER";

    private static final String PINOTIFY_PREFS = "PINOTIFY_PREFS";
    private static final String PREF_ACCOUNT_NAME = "prefAccount";
    private static final int REQUEST_ACCOUNT_PICKER = 2;
    TextView header;
    Button discoverDevicesBtn;
    Button sendMsgBtn;
    EditText sendTxt;
    private GoogleAccountCredential credential;
    private PinotifyApi service;
    private String accountName;
    private TextView messageView;
    private Button cancelBtn;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        //init layout parameters        
        header = (TextView) findViewById(R.id.header);
        sendMsgBtn = (Button) findViewById(R.id.sendbutton);
        cancelBtn = (Button) findViewById(R.id.cancelbutton);
        sendTxt = (EditText) findViewById(R.id.sendtext);
        sendMsgBtn.setOnClickListener(sendMsgListener);
        cancelBtn.setOnClickListener(cancelListener);
        messageView = (TextView) findViewById(R.id.messageContainer);
        //init bluetooth
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth state:" + btAdapter.getState() + " Ok!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Bluetooth state:" + btAdapter.getState() + " Not ok!", Toast.LENGTH_LONG).show();
        }

        showActiveMessage();

        if (!handleIntent(getIntent())) {
            // We're logged in, so let's setup FCM. If we weren't logged
            // in, we'll send it after the login completes.
            FirebaseMessaging.getInstance().subscribeToTopic("news");

            // Send FCM token.
            String token = FirebaseInstanceId.getInstance().getToken();
            String msg = getString(R.string.msg_token_fmt, token);
            Log.d(TAG, msg);
            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            StateController.setFcmId(this, token);

            // Start health checks.
            HealthChecker.startAlarm(getApplicationContext());
        }
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

        if (intent.getBooleanExtra(EXTRA_LOGIN, false)) {
            // We're not logged in. Let's fix that.
            credential = GoogleAccountCredential.usingAudience(this, BackendService.AUDIENCE);
            startActivityForResult(credential.newChooseAccountIntent(),
                    REQUEST_ACCOUNT_PICKER);
            return true;
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (data != null && data.getExtras() != null) {
                    String accountName =
                            data.getExtras().getString(
                                    AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        setSelectedAccountName(accountName);
                        BackendService.makeDeviceRequest(this, null);
                    }
                }
                break;
        }
    }

    private void setSelectedAccountName(String accountName) {
        SharedPreferences settings = getApplicationContext().getSharedPreferences(
                PINOTIFY_PREFS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_ACCOUNT_NAME, accountName);
        editor.commit();
        credential.setSelectedAccountName(accountName);
        this.accountName = accountName;
    }


    private Button.OnClickListener sendMsgListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            header.append(RPiBluetoothConnection.sendMessageGetStatus(getApplicationContext(),
                    sendTxt.getText().toString()));
        }
    };

    private Button.OnClickListener cancelListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            HealthChecker.cancelAlarm(getApplicationContext());
        }
    };
}
