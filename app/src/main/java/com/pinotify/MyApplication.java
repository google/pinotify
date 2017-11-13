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

import android.app.Application;
import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

public class MyApplication extends Application {
    private static final String TAG ="MyAppl";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("MyApplication", "onCreate!");
        FirebaseMessaging.getInstance().subscribeToTopic("news");

        // Send FCM token.
        String token = FirebaseInstanceId.getInstance().getToken();
        String msg = getString(R.string.msg_token_fmt, token);
        Log.d(TAG, msg);
        StateController.setFcmId(this, token);
    }
}
