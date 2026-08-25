package com.agentos.shell

import android.content.Context
import android.os.Build
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class CapabilityId(val value: String) {
    TIME("system.time.read"),
    DEVICE("system.device.read"),
    STORAGE("system.storage.read"),
}

data class CapabilityResult(
    val capability: CapabilityId,
    val title: String,
    val facts: List<Pair<String, String>>,
)

class CapabilityRegistry(
    capabilities: List<SystemCapability>,
) {
    private val registered = capabilities.associateBy(SystemCapability::id)

    fun execute(id: CapabilityId): CapabilityResult =
        requireNotNull(registered[id]) { "Capability is not registered: ${id.value}" }.execute()
}

interface SystemCapability {
    val id: CapabilityId
    fun execute(): CapabilityResult
}

class TimeCapability : SystemCapability {
    override val id = CapabilityId.TIME

    override fun execute() = CapabilityResult(
        capability = id,
        title = "当前时间",
        facts = listOf(
            "本地时间" to ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "时区" to ZonedDateTime.now().zone.id,
        ),
    )
}

class DeviceCapability : SystemCapability {
    override val id = CapabilityId.DEVICE

    override fun execute() = CapabilityResult(
        capability = id,
        title = "设备状态",
        facts = listOf(
            "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to Build.VERSION.RELEASE,
            "API" to Build.VERSION.SDK_INT.toString(),
        ),
    )
}

class StorageCapability(context: Context) : SystemCapability {
    override val id = CapabilityId.STORAGE
    private val directory = context.filesDir

    override fun execute() = CapabilityResult(
        capability = id,
        title = "应用存储",
        facts = listOf(
            "可用空间" to directory.usableSpace.toReadableSize(),
            "总空间" to directory.totalSpace.toReadableSize(),
            "访问范围" to "AgentOS 私有目录",
        ),
    )
}

private fun Long.toReadableSize(): String = when {
    this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
    else -> "$this B"
}
