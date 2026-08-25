package com.agentos.shell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VoiceCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (activityAction, token) = when (intent.action) {
            ACTION_DELIVER_COMMAND -> {
                val command = VoiceCommandPolicy.sanitize(intent.getStringExtra(EXTRA_COMMAND)) ?: return
                ACTION_RUN_COMMAND to VoiceCommandInbox.offer(command)
            }
            ACTION_INTERRUPT_OUTPUT -> ACTION_INTERRUPT to VoiceInterruptInbox.offer()
            else -> return
        }
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(activityAction)
                .putExtra(EXTRA_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
    }

    companion object {
        const val ACTION_DELIVER_COMMAND = "com.agentos.shell.action.DELIVER_VOICE_COMMAND"
        const val ACTION_INTERRUPT_OUTPUT = "com.agentos.shell.action.INTERRUPT_VOICE_OUTPUT"
        const val ACTION_RUN_COMMAND = "com.agentos.shell.action.RUN_VOICE_COMMAND"
        const val ACTION_INTERRUPT = "com.agentos.shell.action.INTERRUPT_VOICE_TURN"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_TOKEN = "voice_command_token"
    }
}
