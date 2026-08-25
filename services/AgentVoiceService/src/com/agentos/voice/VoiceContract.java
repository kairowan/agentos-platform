package com.agentos.voice;

final class VoiceContract {
    static final String ACTION_REARM = "com.agentos.voice.action.REARM";
    static final String ACTION_DELIVER_COMMAND = "com.agentos.shell.action.DELIVER_VOICE_COMMAND";
    static final String ACTION_INTERRUPT_OUTPUT = "com.agentos.shell.action.INTERRUPT_VOICE_OUTPUT";
    static final String COMMAND_RECEIVER = "com.agentos.shell.VoiceCommandReceiver";
    static final String SHELL_PACKAGE = "com.agentos.shell";
    static final String EXTRA_COMMAND = "command";
    static final String KEYPHRASE = "Hey AgentOS";

    private VoiceContract() {}
}
