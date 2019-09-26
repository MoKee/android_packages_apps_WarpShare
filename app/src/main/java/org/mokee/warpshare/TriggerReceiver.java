package org.mokee.warpshare;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TriggerReceiver extends BroadcastReceiver {

    public static final String TAG = "TriggerReceiver";

    public static final String EXTRA_CALLBACK_INTENT = "callback";

    static PendingIntent getTriggerIntent(Context context) {
        final PendingIntent callbackIntent = ReceiverService.getTriggerIntent(context);

        final Intent intent = new Intent(context, TriggerReceiver.class);
        intent.putExtra(TriggerReceiver.EXTRA_CALLBACK_INTENT, callbackIntent);

        return PendingIntent.getBroadcast(context, callbackIntent.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive");
        final PendingIntent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        try {
            callbackIntent.send(context, 0, intent);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed sending callback", e);
        }
    }

}
