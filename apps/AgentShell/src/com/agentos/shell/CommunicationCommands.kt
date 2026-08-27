package com.agentos.shell

import com.agentos.capability.api.CommunicationContract as C
import com.agentos.capability.api.CommunicationRequest

internal sealed interface CommunicationCommand {
    data object Open : CommunicationCommand
    data object Cancel : CommunicationCommand
    data class Draft(val request: CommunicationRequest) : CommunicationCommand
    data class Control(val action: Int) : CommunicationCommand
}

internal object CommunicationCommands {
    fun parse(raw: String): CommunicationCommand? {
        val text = raw.trim()
        if (text.length > 2_200) return null
        when (text) {
            "通信", "打开电话", "打开短信", "打开通信", "phone", "sms" -> return CommunicationCommand.Open
            "取消通信", "取消发送", "取消拨号" -> return CommunicationCommand.Cancel
            "接听", "接听电话" -> return CommunicationCommand.Control(C.ANSWER)
            "拒接", "拒接电话" -> return CommunicationCommand.Control(C.REJECT)
            "挂断", "挂断电话" -> return CommunicationCommand.Control(C.HANG_UP)
        }
        Regex("(?:给|拨打)(.{1,100}?)打电话").matchEntire(text)?.let {
            return CommunicationCommand.Draft(CommunicationRequest(C.CALL, it.groupValues[1].trim(), ""))
        }
        Regex("(?:call |拨打)([+0-9 ()-]{3,100})", RegexOption.IGNORE_CASE).matchEntire(text)?.let {
            return CommunicationCommand.Draft(CommunicationRequest(C.CALL, it.groupValues[1].trim(), ""))
        }
        Regex("给(.{1,100}?)发短信[，,:： ]+(?:说)?([\\s\\S]{1,2000})").matchEntire(text)?.let {
            return CommunicationCommand.Draft(CommunicationRequest(C.SMS, it.groupValues[1].trim(), it.groupValues[2].trim()))
        }
        return null
    }
}
