package com.agentos.capability.service

import com.agentos.capability.api.CommunicationContract
import com.agentos.capability.api.CommunicationRequest

internal object CommunicationPolicy {
    const val MAX_MESSAGE = 2_000
    fun validate(request: CommunicationRequest) {
        require(request.operation in setOf(CommunicationContract.CALL, CommunicationContract.SMS)) { "不支持的通信操作" }
        require(request.recipient.isNotBlank() && request.recipient.length <= 100 &&
            request.recipient.none { it.isISOControl() }) { "请输入有效号码或联系人" }
        require(request.subscriptionId >= -1) { "SIM 参数无效" }
        if (request.operation == CommunicationContract.SMS) {
            require(request.body.isNotBlank() && request.body.length <= MAX_MESSAGE && '\u0000' !in request.body) { "短信须为 1–2000 字" }
        } else require(request.body.isEmpty()) { "拨号请求不能携带短信内容" }
    }

    // ponytail: This preview accepts normal numeric destinations only. USSD, MMI,
    // post-dial digits and SIP need separate reviewed capabilities, not relaxed parsing.
    fun number(raw: String): String? {
        if (raw.length > 100 || raw.any { !it.isDigit() && it !in "+ -()" }) return null
        val value = raw.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
        return value.takeIf { it.matches(Regex("\\+?[0-9]{3,20}")) }
    }

    fun canControl(state: Int, action: Int): Boolean = when (action) {
        CommunicationContract.ANSWER, CommunicationContract.REJECT -> state == 2 // Call.STATE_RINGING
        CommunicationContract.HANG_UP -> state in setOf(0, 1, 2, 3, 4, 8, 9, 10)
        else -> false
    }
}
