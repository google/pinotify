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

import android.accounts.AccountManager;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.pinotify.api.BackendApi;
import com.pinotify.R;
import com.pinotify.receivers.HealthChecker;

public class ConfigActivity extends Activity {
    private TextView bluetoothName;
    private Button bluetoothBtn;
    private TextView userName;
    private Button loginBtn;
    private EditText backendTxt;
    private Button startStopBtn;
    private Button aboutBtn;

    public static final String PINOTIFY_PREFS = "PINOTIFY_PREFS";
    public static final String PREF_ACCOUNT_NAME = "prefAccount";
    public static final String PREF_BLUETOOTH_NAME = "prefBtName";
    public static final String PREF_BLUETOOTH_ADDRESS = "prefBtAddress";
    public static final String PREF_BACKEND_URL = "prefBackendUrl";
    public static final String PREF_STARTED = "prefStarted";

    private static final int REQUEST_BLUETOOTH_SELECT = 1;
    private static final int REQUEST_ACCOUNT_PICKER = 2;
    private static final int REQUEST_BLUETOOTH_ENABLE = 3;
    private GoogleAccountCredential credential;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config);

        bluetoothName = (TextView) findViewById(R.id.bluetoothName);
        bluetoothBtn = (Button) findViewById(R.id.bluetoothBtn);
        userName = (TextView) findViewById(R.id.userName);
        loginBtn = (Button) findViewById(R.id.loginBtn);
        backendTxt = (EditText) findViewById(R.id.backendTxt);
        startStopBtn = (Button) findViewById(R.id.startStopBtn);
        aboutBtn = (Button) findViewById(R.id.aboutBtn);

        bluetoothBtn.setOnClickListener(bluetoothBtnListener);
        loginBtn.setOnClickListener(loginBtnListener);
        startStopBtn.setOnClickListener(startStopBtnListener);
        aboutBtn.setOnClickListener(aboutBtnListener);
        backendTxt.addTextChangedListener(backendTxtTextChanged);

        SharedPreferences sharedPrefs = getSharedPrefs(this);
        bluetoothName.setText(sharedPrefs.getString(PREF_BLUETOOTH_NAME, "") + " " +
                sharedPrefs.getString(PREF_BLUETOOTH_ADDRESS, ""));
        userName.setText(sharedPrefs.getString(PREF_ACCOUNT_NAME, ""));
        backendTxt.setText(sharedPrefs.getString(PREF_BACKEND_URL, ""));
        setUIElemsEnabled(!sharedPrefs.getBoolean(PREF_STARTED, false));
    }

    private void setUIElemsEnabled(boolean enabled) {
        bluetoothBtn.setEnabled(enabled);
        loginBtn.setEnabled(enabled);
        backendTxt.setEnabled(enabled);
        startStopBtn.setText(enabled ? "Start" : "Stop");
    }

    private Button.OnClickListener bluetoothBtnListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), BluetoothSelectActivity.class);
            startActivityForResult(intent, REQUEST_BLUETOOTH_SELECT);
        }
    };

    private TextWatcher backendTxtTextChanged = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            SharedPreferences.Editor editor = getSharedPrefs(getApplicationContext()).edit();
            editor.putString(PREF_BACKEND_URL, backendTxt.getText().toString());
            editor.apply();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private Button.OnClickListener loginBtnListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            credential = GoogleAccountCredential.usingAudience(getApplicationContext(),
                    BackendApi.AUDIENCE);
            startActivityForResult(credential.newChooseAccountIntent(),
                    REQUEST_ACCOUNT_PICKER);

        }
    };

    private Button.OnClickListener startStopBtnListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            final Context context = getApplicationContext();
            final SharedPreferences sharedPrefs = getSharedPrefs(context);
            final boolean wasStarted = sharedPrefs.getBoolean(PREF_STARTED,
                    false);
            if (!wasStarted) {
                // First, let's make sure Bluetooth is on before doing anything else.
                if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    Intent enableBluetoothIntent = new Intent(BluetoothAdapter
                            .ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBluetoothIntent, REQUEST_BLUETOOTH_ENABLE);
                    return;
                }
            }
            setAppStarted(!wasStarted);
        }
    };

    /**
     * @param shouldBeStarted true if should be started, false if should be stopped.
     */
    private void setAppStarted(boolean shouldBeStarted) {
        final SharedPreferences sharedPrefs = getSharedPrefs(this);
        // We disable the config UI when we start.
        setUIElemsEnabled(!shouldBeStarted);
        sharedPrefs.edit().putBoolean(PREF_STARTED, shouldBeStarted).commit();
        if (shouldBeStarted) {
            HealthChecker.startAlarm(this);
            BackendApi.makeDeviceRequest(this, null);
        } else {
            HealthChecker.cancelAlarm(this);
        }
    }


    private Button.OnClickListener aboutBtnListener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getApplicationContext(), AboutActivity.class);
            startActivity(intent);
        }
    };

    private static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(PINOTIFY_PREFS, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_BLUETOOTH_SELECT:
                if (resultCode == Activity.RESULT_OK) {
                    final String deviceName =
                            data.getStringExtra(BluetoothSelectActivity.DEVICE_NAME);
                    final String deviceAddress =
                            data.getStringExtra(BluetoothSelectActivity.DEVICE_ADDRESS);
                    bluetoothName.setText(deviceName + " " + deviceAddress);
                    SharedPreferences.Editor editor = getSharedPrefs(this).edit();
                    editor.putString(PREF_BLUETOOTH_NAME, deviceName);
                    editor.putString(PREF_BLUETOOTH_ADDRESS, deviceAddress);
                    editor.commit();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (data != null && data.getExtras() != null) {
                    String accountName =
                            data.getExtras().getString(
                                    AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        setSelectedAccountName(accountName);
                        BackendApi.makeDeviceRequest(this, null);
                    }
                }
                break;
            case REQUEST_BLUETOOTH_ENABLE:
                if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    setAppStarted(true);
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled.", Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void setSelectedAccountName(String accountName) {
        SharedPreferences settings = getApplicationContext().getSharedPreferences(
                PINOTIFY_PREFS, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(PREF_ACCOUNT_NAME, accountName);
        editor.commit();
        credential.setSelectedAccountName(accountName);
        userName.setText(accountName);
    }
}
