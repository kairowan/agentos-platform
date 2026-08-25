package com.agentos.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VoiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELIVER_COMMAND) return
        val command = VoiceCommandPolicy.sanitize(intent.getStringExtra(EXTRA_COMMAND)) ?: return
        val token = VoiceCommandInbox.offer(command)
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_RUN_COMMAND)
                .putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        const val ACTION_DELIVER_COMMAND = "com.agentos.shell.action.DELIVER_VOICE_COMMAND"
        const val ACTION_RUN_COMMAND = "com.agentos.shell.action.RUN_VOICE_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_TOKEN = "voice_command_token"
    }
}
