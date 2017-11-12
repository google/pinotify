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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Manages the a
 */
public class HealthChecker extends BroadcastReceiver{
    /**
     * (Re)starts the alarm, if it is not already running.
     *
     * @param context The Android context.
     */
    public static void startAlarm(Context context) {
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);


        alarmMgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HALF_HOUR / 30L / 12L,
                AlarmManager.INTERVAL_HALF_HOUR / 30L / 12L, getPendingIntent(context));
    }

    /**
     * Cancels the alarm, if it is running.
     *
     * @param context The Android context.
     */
    public static void cancelAlarm(Context context) {
        // Android considers two PendingIntents the same if they share the same request ID, and the
        // intents are the same by Intent.filterEquals() (same action, data, type, class, and
        // categories).
        //
        // Therefore, an existing alarm can be cancelled by simply re-creating the equivalent
        // PendingIntent and cancelling it.
        AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmMgr.cancel(getPendingIntent(context));
    }

    /**
     * @param context The Android context.
     * @return The Intent used by the health checker alarm. See the comment inside cancelAlarm()'s
     *         implementation for details about how Android reuses PendingIntents.
     */
    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, HealthChecker.class);
        intent.setAction("com.pinotify.HealthCheck");
        return PendingIntent.getBroadcast(context, 0, intent, 0);
    }

    /**
     * Called when the alarm has fired.
     *
     * @param context The Android context.
     * @param intent Intent for the alarm that fired.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "The alarm went off!", Toast.LENGTH_SHORT).show();
        BackendService.makeDeviceRequest(context, null);
    }
}
