// Kotlin, Android (Zalith/Pojav fork), static pre-launch jar scan
package com.movtery.zalithlauncher.feature.mod.parser

import com.mio.util.AndroidUtil
import net.kdt.pojavlaunch.Architecture
import java.io.File
import java.util.zip.ZipFile

/** Result of scanning one mod jar. Static analysis only: nothing is loaded or executed. */
data class JarCompatReport(
    val file: File,
    /** Will certainly fail on this device (wrong-arch/desktop-OS native). */
    val fatal: List<String>,
    /** Desktop-only API references; crash only if that code path runs. */
    val risky: List<String>,
    /** Highest class-file major version (52=Java 8, 61=17, 65=21). 0 if none. */
    val maxClassMajor: Int
)

object ModNativeCompatScanner {
    // internal-name markers searched in class constant pools -> why it is a problem on Android
    private val RISKY = mapOf(
        "java/awt/Robot" to "java.awt.Robot (no screen/input injection in the Android JRE)",
        "javax/swing/" to "Swing UI (no desktop windowing; JFrame/JOptionPane throw HeadlessException)",
        "java/awt/Frame" to "AWT Frame/Window (headless)",
        "sun/awt/" to "sun.awt internals (absent in Android JRE build)",
        "com/sun/jna/Native" to "JNA (needs a libjnidispatch for this ABI)",
        "java/awt/SystemTray" to "AWT SystemTray (unsupported)",
        "java/awt/Desktop" to "java.awt.Desktop (browse/open unsupported)"
    )
    private const val MAX_CLASS_BYTES = 4 * 1024 * 1024

    private fun archName(i: Int) = when (i) {
        Architecture.ARCH_ARM64 -> "AArch64"
        Architecture.ARCH_ARM -> "ARM"
        Architecture.ARCH_X86_64 -> "x86_64"
        Architecture.ARCH_X86 -> "x86"
        else -> "?"
    }

    fun scan(jar: File, deviceArch: Int = Architecture.getDeviceArchitecture()): JarCompatReport {
        val fatal = linkedSetOf<String>()
        val risky = linkedSetOf<String>()
        var maxMajor = 0
        runCatching {
            ZipFile(jar).use { zip ->
                for (e in zip.entries()) {
                    if (e.isDirectory) continue
                    val n = e.name
                    val lower = n.lowercase()
                    when {
                        lower.endsWith(".dll") || lower.endsWith(".dylib") || lower.endsWith(".exe") ->
                            fatal += "$n: Windows/macOS binary, cannot load on Android"
                        lower.endsWith(".so") -> {
                            val arch = zip.getInputStream(e).use { AndroidUtil.getElfArchFromStream(it) }
                            val dev = archName(deviceArch)
                            // JVM-side check: libs in jars are extracted and dlopen'd, so ABI must match.
                            if (arch.isNotEmpty() && arch != dev && !(dev == "AArch64" && arch == "ARM64"))
                                fatal += "$n: built for $arch, device is $dev"
                        }
                        lower.endsWith(".class") && !lower.endsWith("module-info.class") &&
                                !lower.startsWith("meta-inf/versions/") && e.size in 8..MAX_CLASS_BYTES -> {
                            val b = zip.getInputStream(e).use { it.readBytes() }
                            if (b.size > 8 && (b[0].toInt() and 0xFF) == 0xCA && (b[1].toInt() and 0xFF) == 0xFE) {
                                val major = ((b[6].toInt() and 0xFF) shl 8) or (b[7].toInt() and 0xFF)
                                if (major > maxMajor) maxMajor = major
                                val text = String(b, Charsets.ISO_8859_1)
                                for ((k, v) in RISKY) if (text.contains(k)) risky += "${n.substringAfterLast('/')}: $v"
                            }
                        }
                    }
                }
            }
        }
        return JarCompatReport(jar, fatal.toList(), risky.toList().take(8), maxMajor)
    }

    fun javaForMajor(major: Int) = if (major >= 49) major - 44 else 0
}

/** Last scan results, read by LaunchGame (runtime known there) and the crash diagnoser. */
object ModCompatReport {
    @Volatile var reports: List<JarCompatReport> = emptyList()
}
