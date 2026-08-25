package com.agentos.capability.core

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class CapabilityId(val value: String) {
    TIME("system.time.read"),
    DEVICE("system.device.read"),
    STORAGE("system.storage.read"),
    WIFI_SETTINGS("system.settings.wifi.open");

    companion object {
        fun fromWire(value: String): CapabilityId? = entries.firstOrNull { it.value == value }
    }
}

enum class CapabilityRisk {
    READ_ONLY,
    REQUIRES_CONFIRMATION,
    BLOCKED,
}

data class CapabilityDescriptor(
    val id: CapabilityId,
    val displayName: String,
    val risk: CapabilityRisk,
)

data class CapabilityResult(
    val capability: CapabilityId,
    val title: String,
    val facts: List<Pair<String, String>>,
)

class CapabilityRegistry(capabilities: List<SystemCapability>) {
    private val registered = capabilities.associateBy { it.descriptor.id }

    fun find(id: CapabilityId): SystemCapability? = registered[id]
}

interface SystemCapability {
    val descriptor: CapabilityDescriptor
    fun execute(): CapabilityResult
}

class TimeCapability : SystemCapability {
    override val descriptor = CapabilityDescriptor(
        CapabilityId.TIME,
        "读取当前时间",
        CapabilityRisk.READ_ONLY,
    )

    override fun execute() = CapabilityResult(
        capability = descriptor.id,
        title = "当前时间",
        facts = listOf(
            "本地时间" to ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            "时区" to ZonedDateTime.now().zone.id,
        ),
    )
}

class DeviceCapability : SystemCapability {
    override val descriptor = CapabilityDescriptor(
        CapabilityId.DEVICE,
        "读取设备状态",
        CapabilityRisk.READ_ONLY,
    )

    override fun execute() = CapabilityResult(
        capability = descriptor.id,
        title = "设备状态",
        facts = listOf(
            "设备" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to Build.VERSION.RELEASE,
            "API" to Build.VERSION.SDK_INT.toString(),
        ),
    )
}

class StorageCapability(context: Context) : SystemCapability {
    override val descriptor = CapabilityDescriptor(
        CapabilityId.STORAGE,
        "读取 AgentOS 私有存储状态",
        CapabilityRisk.READ_ONLY,
    )
    private val directory = context.filesDir

    override fun execute() = CapabilityResult(
        capability = descriptor.id,
        title = "能力服务存储",
        facts = listOf(
            "可用空间" to directory.usableSpace.toReadableSize(),
            "总空间" to directory.totalSpace.toReadableSize(),
            "访问范围" to "Capability Service 私有目录",
        ),
    )
}

class OpenWifiSettingsCapability(context: Context) : SystemCapability {
    override val descriptor = CapabilityDescriptor(
        CapabilityId.WIFI_SETTINGS,
        "打开 Wi-Fi 设置",
        CapabilityRisk.REQUIRES_CONFIRMATION,
    )
    private val applicationContext = context.applicationContext

    override fun execute(): CapabilityResult {
        applicationContext.startActivity(
            Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return CapabilityResult(
            capability = descriptor.id,
            title = "已打开 Wi-Fi 设置",
            facts = listOf("执行方式" to "用户确认后由独立 Capability Service 调用"),
        )
    }
}

private fun Long.toReadableSize(): String = when {
    this >= 1_073_741_824 -> "%.1f GB".format(this / 1_073_741_824.0)
    this >= 1_048_576 -> "%.1f MB".format(this / 1_048_576.0)
    else -> "$this B"
}
