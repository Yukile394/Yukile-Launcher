// Kotlin, Android (Zalith/Pojav fork): post-mortem of latestlog.txt after a non-zero JVM exit
package com.movtery.zalithlauncher.feature.log

import com.movtery.zalithlauncher.feature.mod.parser.ModCompatReport
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.RandomAccessFile

object CrashDiagnoser {
    private const val TAIL_BYTES = 256 * 1024L

    private data class Rule(val regex: Regex, val explain: (MatchResult) -> String)

    private val rules = listOf(
        Rule(Regex("""UnsatisfiedLinkError:?\s*(.*)""")) {
            "Native library failed to load (${it.groupValues[1].take(160)}). Usually a mod bundles a desktop (x86/Windows) .so/.dll; Android needs an AArch64 build."
        },
        Rule(Regex("""UnsupportedClassVersionError:.*class file version (\d+)""")) {
            val j = it.groupValues[1].toIntOrNull()?.minus(44) ?: 0
            "A mod needs Java $j but the selected runtime is older. Pick a newer Java runtime for this version."
        },
        Rule(Regex("""NoClassDefFoundError:\s*([\w/.$]+)""")) {
            val c = it.groupValues[1]
            if (c.startsWith("java/awt") || c.startsWith("javax/swing") || c.startsWith("sun/awt"))
                "Mod uses desktop-only class $c, which the Android JRE does not ship."
            else "Missing class $c: a dependency mod/library is missing or the mod targets another MC/loader version."
        },
        Rule(Regex("""HeadlessException""")) { "Mod tried to open a desktop window (AWT/Swing); not possible on Android." },
        Rule(Regex("""Mixin apply failed|MixinApplyError|InvalidInjectionException""")) {
            "A mixin failed to apply: mod is built for a different Minecraft/Fabric Loader version, or conflicts with another mod."
        },
        Rule(Regex("""Incompatible mods found|requires (?:any )?version|depends on""")) {
            "Loader reported incompatible/missing mod dependencies (see log lines above)."
        },
        Rule(Regex("""OutOfMemoryError""")) { "Out of memory: lower RAM allocation or remove heavy mods." },
        Rule(Regex("""Fatal signal (\d+).*""")) { "Native crash (signal ${it.groupValues[1]}), typically from a native library or the renderer." }
    )

    private fun tail(): String? = runCatching {
        val f = File(PathManager.DIR_GAME_HOME, "latestlog.txt")
        if (!f.isFile) return null
        RandomAccessFile(f, "r").use { r ->
            val start = (r.length() - TAIL_BYTES).coerceAtLeast(0)
            r.seek(start)
            ByteArray((r.length() - start).toInt()).also { r.readFully(it) }.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    /** @return human-readable cause (with suspected mod jar names if the stack mentions them), or null. */
    @JvmStatic
    fun diagnose(): String? {
        val log = tail() ?: return null
        // last matching rule position wins (the real crash is near the end)
        var best: Pair<Int, String>? = null
        for (r in rules) {
            r.regex.findAll(log).lastOrNull()?.let { m ->
                if (best == null || m.range.first > best!!.first) best = m.range.first to r.explain(m)
            }
        }
        var text = best?.second ?: return null
        // cross-check scan results + names in the log
        val suspects = ModCompatReport.reports.filter { r ->
            r.fatal.isNotEmpty() || log.contains(r.file.nameWithoutExtension)
        }.map { it.file.name }.distinct().take(5)
        if (suspects.isNotEmpty()) text += "\nSuspected mod file(s): " + suspects.joinToString(", ")
        return text
    }
}
