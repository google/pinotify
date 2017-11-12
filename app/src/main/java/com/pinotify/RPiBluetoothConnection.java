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

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.UUID;

public class RPiBluetoothConnection {
    private static final int NUM_RETRIES = 3;
    private static BluetoothSocket btSocket;
    // TODO: make UI to get btAddress from paired devices.
    // SETUP_TODO: Hard code the Bluetooth address of your Raspberry Pi.
    private static String btAdress = "enter-valid-device-address";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static OutputStream out;
    private static BluetoothDevice device;
    private static StringBuilder statusMsg = new StringBuilder();
    private static RPiBluetoothListener listener = new RPiBluetoothListener();

    private static void discoverDevice(Context context) {
        BluetoothSocket sock;
        if (btSocket == null) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            device = btAdapter.getRemoteDevice(btAdress);
            statusMsg.append("\nRemote device: " + device.getName());
            try {
                sock = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (Exception e) {
                Toast.makeText(context, "Couldn't create socket", Toast.LENGTH_LONG);
                statusMsg.append("Failed to connect");
                return;
            }
            statusMsg.append("\n createRfcommsockettoservice! ");
            btAdapter.cancelDiscovery();
            try {
                sock.connect();
                statusMsg.append("\n btSocket Created!");
            } catch (IOException e) {
                Toast.makeText(context, "Could not connect to socket", Toast.LENGTH_LONG);
                try {
                    sock.close();
                } catch (Exception b) {}
            }
            btSocket = sock;
        }
    }

    @NonNull
    public static String sendMessageGetStatus(Context context, String sendTxt) {
        statusMsg = new StringBuilder();
        sendMessage(context, sendTxt.getBytes()[0]);
        return statusMsg.toString();
    }

    public static interface Receiver {
        // Called when a byte is received on the socket.
        void receivedByte(byte b);
    }

    // Sets
    public static void setReceiver(Receiver receiver) {
        listener.setReceiver(receiver);
    }

    public static void sendMessage(Context context, byte msg) {
        if (btSocket == null) {
            discoverDevice(context);
            if (btSocket == null) {
                return;
            }
        }
        for (int i = 0; i < NUM_RETRIES; i++) {
            try {
                out = btSocket.getOutputStream();
                out.write(msg);
                Toast.makeText(context, "Message sent", Toast.LENGTH_LONG).show();
                listener.start(btSocket);
                break;
            } catch (Exception a) {
                Toast.makeText(context, "Could not send msg", Toast.LENGTH_LONG).show();
                try {
                    btSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                btSocket = null;
                discoverDevice(context);
                if (btSocket == null) {
                    return;
                }
            }
        }

    }
}

/**
 * Listens for responses from the remote.
 */
class RPiBluetoothListener {
    private static String TAG = "RPiBluetoothListener";
    private BluetoothSocket _btSocket;
    private RPiBluetoothConnection.Receiver receiver;

    public void start(final BluetoothSocket btSocket) {
        if (_btSocket == btSocket) {
            Log.d(TAG, "Already listening on socket " + btSocket);
            return;
        }
        _btSocket = btSocket;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Starting new listener.");
                try {
                    byte[] buf = new byte[1024];
                    InputStream is = btSocket.getInputStream();
                    while (true) {
                        int len = is.read(buf);
                        if (len == -1) {
                            Log.e(TAG, "Unexpected EOF from remote.");
                            btSocket.close();
                            return;
                        }
                        if (len != 1) {
                            Log.e(TAG, "Unexpectedly got input of size " + len + ": " +
                                    Arrays.copyOfRange(buf, 0, len));
                            btSocket.close();
                            return;
                        }
                        if (receiver != null) {
                            receiver.receivedByte(buf[0]);
                        }
                        Log.i(TAG, "Got a byte: " + buf[0]);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "Exited listener");
            }
        }).start();
    }

    public void setReceiver(RPiBluetoothConnection.Receiver receiver) {
        this.receiver = receiver;
    }
}