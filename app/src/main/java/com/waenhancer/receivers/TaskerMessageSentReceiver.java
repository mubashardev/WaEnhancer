package com.waenhancer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TaskerMessageSentReceiver extends BroadcastReceiver {
    private static final String TAG = "WAE_TaskerReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        ;
        if (intent == null) return;

        String number = intent.getStringExtra("number");
        String message = intent.getStringExtra("message");

        if (number == null || message == null) {
            ;
            return;
        }

        ;

        // Forward broadcast to WhatsApp packages for handling inside the hooked process
        forwardBroadcast(context, "com.whatsapp", number, message);
        forwardBroadcast(context, "com.whatsapp.w4b", number, message);
    }

    private void forwardBroadcast(Context context, String packageName, String number, String message) {
        try {
            Intent forwardIntent = new Intent("com.waenhancer.MESSAGE_SENT_INTERNAL");
            forwardIntent.putExtra("number", number);
            forwardIntent.putExtra("message", message);
            forwardIntent.setPackage(packageName);
            forwardIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(forwardIntent);
        } catch (Exception e) {
            Log.d(TAG, "Failed to forward broadcast to " + packageName + ": " + e.getMessage());
        }
    }
}

