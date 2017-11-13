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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.pinotify.R;

import java.util.Set;

public class BluetoothSelectActivity extends Activity {
    private static final String TAG = "BlueSelAct";

    /**
     * The name of the string field containing the device address in the returned intent.
     */
    public static final String DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String DEVICE_NAME = "DEVICE_NAME";

    private static final String SEPARATOR_CHAR = "\n";

    private ArrayAdapter<String> devicesArrayAdapter;
    private ListView bluetoothListView;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_select);

        bluetoothListView = (ListView) findViewById(R.id.bluetoothListView);
        devicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.bluetooth_item);
        bluetoothListView.setAdapter(devicesArrayAdapter);
        bluetoothListView.setOnItemClickListener(bluetoothListViewListener);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            devicesArrayAdapter.add(device.getName() + SEPARATOR_CHAR + device.getAddress());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private AdapterView.OnItemClickListener bluetoothListViewListener =
            new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    bluetoothAdapter.cancelDiscovery();
                    final String selectedDeviceString = ((TextView) view).getText().toString();
                    Log.d(TAG, "Selected device info " + selectedDeviceString);

                    final String[] parts = selectedDeviceString.split(SEPARATOR_CHAR);
                    final String bluetoothDeviceName = parts[0];
                    final String bluetoothAddress = parts[1];
                    Intent backIntent = new Intent();
                    backIntent.putExtra(DEVICE_NAME, bluetoothDeviceName);
                    backIntent.putExtra(DEVICE_ADDRESS, bluetoothAddress);
                    setResult(Activity.RESULT_OK, backIntent);
                    finish();
                }
            };
}
