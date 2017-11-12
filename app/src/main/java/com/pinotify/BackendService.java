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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import java.io.IOException;

import none.pinotify_api.PinotifyApi;
import none.pinotify_api.model.ApiDeviceRequest;
import none.pinotify_api.model.ApiDeviceResponse;


public class BackendService {
    private static final String TAG = "BackendService";

    // TODO: move these to the state controller
    public static final String PINOTIFY_PREFS = "PINOTIFY_PREFS";
    public static final String PREF_ACCOUNT_NAME = "prefAccount";

    // SETUP_TODO: You must add a valid client ID from the Google Cloud console.
    public static final String AUDIENCE = "server:client_id:valid-cilent-id-here";

    /**
     * Make a DeviceRequest to the backend. If a message is returned, this method will automatically
     * send an intent with the message contents to the MainActivity.
     *
     * If the user isn't logged in, a different intent will be sent to MainActivity to trigger a
     * login. Once the login copletes, this method should be called again.
     *
     * @param context The Android context.
     *
     * @param causedByPushId The ID of the push activity that triggered this API call, or null if
     *                       this request wasn't made due to a push notification.
     */
    public static void makeDeviceRequest(final Context context, Long causedByPushId) {
        // TODO: we might need a Wifi lock
        SharedPreferences settings = context.getSharedPreferences(PINOTIFY_PREFS, 0);
        String accountName = settings.getString(PREF_ACCOUNT_NAME, null);
        if (accountName == null) {
            // We're not logged in; send an intent to login.
            // TODO: get counter on this?
           Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_LOGIN, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }
        GoogleAccountCredential credential = GoogleAccountCredential
                .usingAudience(context,
                        AUDIENCE).setSelectedAccountName(accountName);
        PinotifyApi.Builder builder = new PinotifyApi.Builder(AndroidHttp.newCompatibleTransport(),
                new com.google.api.client.json.gson.GsonFactory(), credential);
        // TODO: change this to point to the backend. Perhaps we should load from config?
        // SETUP_TODO: Currently, this points at the local AppEngine dev server (replace the IP to
        // match yours). You can also replace this with the URL of your deployed backend.
        builder.setRootUrl("http://192.168.0.2:8080/_ah/api");
        final PinotifyApi service = builder.build();
        final ApiDeviceRequest request = new ApiDeviceRequest()
                .setFcmId(StateController.getFcmId(context))
                .setAckedUntil(StateController.getAckedUntilUtc(context))
                .setCausedByPushId(causedByPushId)
                .setDeviceId(Settings.Secure.getString(context.getApplicationContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID));
        (new AsyncTask<Void, Void, ApiDeviceResponse>() {
            @Override
            protected ApiDeviceResponse doInBackground(Void... voids) {
                try {
                    ApiDeviceResponse response = service.pynotifyApi().deviceRequest(request).execute();
                    return response;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(ApiDeviceResponse response) {
                if (response == null) {
                    Log.e(TAG, "Device response was null");
                    return;
                }
                if (response.getMessage() != null) {
                    if (response.getDate() == null || response.getSender() == null) {
                        Log.e(TAG, "Invalid message: date: " + response.getDate() + " sender: " +
                                response.getSender());
                        return;
                    }
                    // We got a new message! Let's show it.
                    StateController.ActiveMessage message = new StateController.ActiveMessage();
                    message.sender = response.getSender();
                    message.timeSentUtc = response.getDate();
                    message.message = response.getMessage();
                    StateController.setActiveMessage(context, message);
                    return;
                }
            }
        }).execute();
    }


}
