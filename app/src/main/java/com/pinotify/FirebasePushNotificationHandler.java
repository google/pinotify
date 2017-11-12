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

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class FirebasePushNotificationHandler extends FirebaseMessagingService {
    private static final String TAG = "FirebasePushNotifica";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, remoteMessage.getFrom());

        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Received push data: " + remoteMessage.getData());
        }

        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message notification body " + remoteMessage.getNotification().getBody());
        }

        // TODO: id cause
        BackendService.makeDeviceRequest(this, null);

//        Handler h = new Handler(getApplicationContext().getMainLooper());
//
//        h.post(new Runnable() {
//            @Override
//            public void run() {
//                RPiBluetoothConnection.sendMessage(getApplicationContext(),
//                        "Got a push notification!");
//            }
//        });
    }
}