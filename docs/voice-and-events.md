# Voice and message events

AgentOS treats speech as the primary task input and text as a fallback. The v0.4
path is:

```text
microphone permission -> platform SpeechRecognizer -> AgentRuntime
AgentRuntime -> Capability Broker -> generated result -> platform TextToSpeech
```

The Shell requests microphone permission only after the user presses **点击说话**.
It does not keep the microphone open in the background. `SpeechRecognizer` and
`TextToSpeech` are provider boundaries: a plain AOSP image may not contain a usable
Chinese engine, so missing providers fail visibly and text input remains available.

Message notifications follow a separate trusted path:

```text
Android NotificationManager -> AgentCapabilityService
-> local category/size/privacy filter -> one-way AIDL event -> AgentShell UI
```

Enable access from **消息事件 / 授权**. Only message-category notifications with a
non-empty sender and body are surfaced. Ongoing notifications and group summaries
are rejected. At most five recent events remain in Shell process memory.

No notification content is automatically sent to a remote model, persisted, spoken
on a locked device, or answered. Those behaviors require separate user-facing
controls and Broker capabilities in a later release.
