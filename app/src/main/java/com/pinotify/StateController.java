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
import android.support.annotation.Nullable;

/**
 * Manages message persistent state held locally.
 */
public class StateController {
    public static final String PINOTIFY_PREFS = "PINOTIFY_PREFS";

    private static final String ACTIVE_MESSAGE_SENDER = "ACTIVE_MESSAGE_SENDER";
    private static final String ACTIVE_MESSAGE_MESSAGE = "ACTIVE_MESSAGE_MESSAGE";
    private static final String ACKED_UNTIL_UTC = "ACKED_UNTIL_UTC";
    private static final String FCM_ID = "FCM_ID";
    private static final String ACTIVE_MESSAGE_TIME_SENT_UTC = "ACTIVE_MESSAGE_TIME_SENT_UTC";

    private static SharedPreferences getSharedPrefs(Context context) {
        return context.getSharedPreferences(PINOTIFY_PREFS, 0);
    }

    private static SharedPreferences.Editor getEditor(Context context) {
        return getSharedPrefs(context).edit();
    }

    // TODO: These shouldn't do work on the UI thread.

    public static void setFcmId(Context context, String fcmId) {
        SharedPreferences.Editor editor = getEditor(context);
        editor.putString(FCM_ID, fcmId);
        editor.commit();
        BackendService.makeDeviceRequest(context, null);
    }

    public static String getFcmId(Context context) {
        SharedPreferences settings = getSharedPrefs(context);
        return settings.getString(FCM_ID, null);
    }

    public static void ackCurrentMessage(Context context) {
        ActiveMessage message = getActiveMessage(context);
        if (message == null) {
            return;
        }
        SharedPreferences.Editor editor = getEditor(context);
        editor.putLong(ACKED_UNTIL_UTC, message.timeSentUtc);
        editor.commit();
        BackendService.makeDeviceRequest(context, null);

        // Clear the currently displayed message.
        setActiveMessage(context, null);
    }

    public static long getAckedUntilUtc(Context context) {
        SharedPreferences settings = getSharedPrefs(context);
        return settings.getLong(ACKED_UNTIL_UTC, -1L);
    }

    public static class ActiveMessage {
        String sender;
        String message;
        long timeSentUtc;

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof ActiveMessage)) {
                return false;
            }
            ActiveMessage other = (ActiveMessage) obj;
            return sender.equals(other.sender) && message.equals(other.message) &&
                    timeSentUtc == other.timeSentUtc;
        }
    }

    public static synchronized void setActiveMessage(final Context context, ActiveMessage message) {
        SharedPreferences.Editor editor = getEditor(context);
        ActiveMessage old_message = getActiveMessage(context);
        if (message == null) {
            editor.putString(ACTIVE_MESSAGE_SENDER, null);
            editor.putString(ACTIVE_MESSAGE_MESSAGE, null);
            editor.putString(ACTIVE_MESSAGE_TIME_SENT_UTC, null);
        } else {
            editor.putString(ACTIVE_MESSAGE_SENDER, message.sender);
            editor.putString(ACTIVE_MESSAGE_MESSAGE, message.message);
            editor.putLong(ACTIVE_MESSAGE_TIME_SENT_UTC, message.timeSentUtc);
            RPiService.setDismisser(context, new RPiService.Dismisser() {
                @Override
                public void Dismiss(Context context) {
                    ackCurrentMessage(context);
                }
            });
            RPiService.startBlinking(context);
        }
        editor.commit();

        if ((message == null && old_message != null) ||
                (message != null && (!message.equals(old_message)))) {
            // If the message changed, update the message display activity. This method is
            // synchronized to prevent races in this conditional.
            Intent intent = new Intent(context, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_MESSAGE, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    @Nullable
    public static ActiveMessage getActiveMessage(Context context) {
        SharedPreferences settings = getSharedPrefs(context);
        ActiveMessage message = new ActiveMessage();
        message.sender = settings.getString(ACTIVE_MESSAGE_SENDER, null);
        message.message = settings.getString(ACTIVE_MESSAGE_MESSAGE, null);
        message.timeSentUtc = settings.getLong(ACTIVE_MESSAGE_TIME_SENT_UTC, -1L);
        if (message.sender == null || message.message == null || message.timeSentUtc == -1) {
            return null;
        }
        return message;
    }
}
