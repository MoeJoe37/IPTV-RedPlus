package com.redplus.iptv.vpn

import android.content.Context
import android.content.Intent
import java.io.StringReader

object RedPlusOpenVpnBridge {
    fun connect(context: Context, title: String, openVpnConfig: String, remoteIpOverride: String? = null) {
        val cleanConfig = hardenVpnGateConfig(openVpnConfig, remoteIpOverride)
        require(cleanConfig.contains(Regex("(?m)^remote\\s+\\S+\\s+\\d+"))) { "Selected server has no usable OpenVPN remote line." }

        val appContext = context.applicationContext
        val configParserClass = Class.forName("de.blinkt.openvpn.core.ConfigParser")
        val profileClass = Class.forName("de.blinkt.openvpn.VpnProfile")
        val profileManagerClass = Class.forName("de.blinkt.openvpn.core.ProfileManager")
        val launchHelperClass = Class.forName("de.blinkt.openvpn.core.VPNLaunchHelper")

        val parser = configParserClass.getDeclaredConstructor().newInstance()
        invokeOpenVpn { configParserClass.method("parseConfig", 1).invoke(parser, StringReader(cleanConfig)) }.getOrThrow()
        val profile = invokeOpenVpn { configParserClass.method("convertProfile", 0).invoke(parser) }.getOrThrow()
            ?: error("Could not convert the OpenVPN profile.")

        profileClass.setFieldIfPresent(profile, "mName", title.ifBlank { "RedPlus VPN" })
        profileClass.setFieldIfPresent(profile, "mProfileCreator", appContext.packageName)
        profileClass.setFieldIfPresent(profile, "mUsername", "")
        profileClass.setFieldIfPresent(profile, "mPassword", "")
        checkProfileIfPossible(appContext, profileClass, profile)
        setTemporaryProfile(profileManagerClass, profileClass, appContext, profile)

        invokeOpenVpn {
            launchHelperClass.method("startOpenVpn", 2).invoke(null, profile, appContext)
        }.getOrElse {
            startInternalLaunchActivity(appContext, profileClass, profile)
        }
    }

    fun stop() {
        runCatching {
            val serviceClass = Class.forName("de.blinkt.openvpn.core.OpenVPNService")
            val management = serviceClass.declaredFields.firstOrNull { it.name == "mManagement" }?.let { field ->
                field.isAccessible = true
                field.get(null)
            }
            if (management != null) {
                val stop = management.javaClass.methods.firstOrNull { it.name == "stopVPN" }
                    ?: management.javaClass.declaredMethods.firstOrNull { it.name == "stopVPN" }?.also { it.isAccessible = true }
                if (stop != null) {
                    when (stop.parameterTypes.size) {
                        0 -> stop.invoke(management)
                        1 -> stop.invoke(management, false)
                    }
                }
                return
            }
        }
    }

    private fun setTemporaryProfile(profileManagerClass: Class<*>, profileClass: Class<*>, context: Context, profile: Any) {
        val oneArg = (profileManagerClass.methods + profileManagerClass.declaredMethods)
            .firstOrNull { it.name == "setTemporaryProfile" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(profileClass) }
        if (oneArg != null) {
            oneArg.isAccessible = true
            invokeOpenVpn { oneArg.invoke(null, profile) }.getOrThrow()
            return
        }

        val twoArg = (profileManagerClass.methods + profileManagerClass.declaredMethods)
            .firstOrNull { it.name == "setTemporaryProfile" && it.parameterTypes.size == 2 }
            ?: error("OpenVPN engine method missing: ProfileManager.setTemporaryProfile")
        twoArg.isAccessible = true
        val args = if (twoArg.parameterTypes.firstOrNull()?.isAssignableFrom(Context::class.java) == true) arrayOf(context, profile) else arrayOf(profile, context)
        invokeOpenVpn { twoArg.invoke(null, *args) }.getOrThrow()
    }

    private fun checkProfileIfPossible(context: Context, profileClass: Class<*>, profile: Any) {
        val check = (profileClass.methods + profileClass.declaredMethods).firstOrNull { it.name == "checkProfile" && it.parameterTypes.size == 1 } ?: return
        check.isAccessible = true
        val result = invokeOpenVpn { check.invoke(profile, context) }.getOrNull() as? Int ?: return
        val noError = runCatching {
            val rClass = Class.forName("de.blinkt.openvpn.R\$string")
            rClass.getField("no_error_found").getInt(null)
        }.getOrNull()
        if (noError != null && result != noError) {
            val message = runCatching { context.getString(result) }.getOrDefault("OpenVPN profile validation failed: $result")
            error(message)
        }
    }

    private fun startInternalLaunchActivity(context: Context, profileClass: Class<*>, profile: Any) {
        val launchClass = Class.forName("de.blinkt.openvpn.LaunchVPN")
        val uuid = profileClass.method("getUUIDString", 0).invoke(profile) as? String
            ?: error("OpenVPN profile UUID missing.")
        val extraKey = runCatching { launchClass.getField("EXTRA_KEY").get(null) as String }.getOrDefault("de.blinkt.openvpn.shortcutProfileUUID")
        val extraHideLog = runCatching { launchClass.getField("EXTRA_HIDELOG").get(null) as String }.getOrDefault("de.blinkt.openvpn.showNoLogWindow")
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClass(context, launchClass)
            putExtra(extraKey, uuid)
            putExtra(extraHideLog, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun hardenVpnGateConfig(config: String, remoteIpOverride: String?): String {
        val normalized = config.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return normalized
        val lines = normalized.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toMutableList()
        val remoteIp = remoteIpOverride?.trim().orEmpty()
        if (remoteIp.isNotBlank()) {
            for (i in lines.indices) {
                val match = Regex("^remote\\s+\\S+\\s+(\\d+)(.*)$", RegexOption.IGNORE_CASE).find(lines[i])
                if (match != null) {
                    lines[i] = "remote $remoteIp ${match.groupValues[1]}${match.groupValues[2]}"
                }
            }
        }
        if (lines.none { it == "pull" }) lines += "pull"
        if (lines.none { it.startsWith("redirect-gateway") }) lines += "redirect-gateway def1"
        if (lines.none { it.startsWith("dhcp-option DNS") }) {
            lines += "dhcp-option DNS 1.1.1.1"
            lines += "dhcp-option DNS 8.8.8.8"
        }
        if (lines.none { it == "resolv-retry infinite" }) lines += "resolv-retry infinite"
        if (lines.none { it == "nobind" }) lines += "nobind"
        return lines.joinToString("\n") + "\n"
    }

    private fun <T> invokeOpenVpn(block: () -> T): Result<T> = runCatching(block).fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable -> Result.failure(throwable.cause ?: throwable) }
    )

    private fun Class<*>.method(name: String, parameterCount: Int) = methods.firstOrNull { it.name == name && it.parameterTypes.size == parameterCount }
        ?: declaredMethods.firstOrNull { it.name == name && it.parameterTypes.size == parameterCount }?.also { it.isAccessible = true }
        ?: error("OpenVPN engine method missing: $name/$parameterCount")

    private fun Class<*>.setFieldIfPresent(target: Any, fieldName: String, value: Any) {
        val field = runCatching { getField(fieldName) }.getOrNull() ?: runCatching { getDeclaredField(fieldName) }.getOrNull() ?: return
        field.isAccessible = true
        field.set(target, value)
    }
}
