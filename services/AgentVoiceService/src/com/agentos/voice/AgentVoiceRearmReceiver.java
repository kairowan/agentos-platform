package com.agentos.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AgentVoiceRearmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (VoiceContract.ACTION_REARM.equals(intent.getAction())) {
            context.sendBroadcast(
                    new Intent(VoiceContract.ACTION_REARM).setPackage(context.getPackageName()));
        }
    }
}
