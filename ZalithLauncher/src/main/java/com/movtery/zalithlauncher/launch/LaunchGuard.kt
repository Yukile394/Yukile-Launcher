// Kotlin, Android (Zalith/Pojav fork): pre-run sanity checks so a bad state fails with a message, not a crash
package com.movtery.zalithlauncher.launch

import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.mod.parser.ModCompatReport
import com.movtery.zalithlauncher.feature.mod.parser.ModNativeCompatScanner
import com.movtery.zalithlauncher.feature.version.Version
import net.kdt.pojavlaunch.multirt.MultiRTUtils
import org.json.JSONObject
import java.io.File

object LaunchGuard {
    /** @return null if OK, else a user-facing reason the game must not start. */
    @JvmStatic
    fun blockingProblem(version: Version): String? {
        if (AccountsManager.currentAccount == null) return "No account selected."
        val dir = version.getVersionPath()
        val name = version.getVersionName()
        val json = File(dir, "$name.json")
        if (!dir.isDirectory || !json.isFile || json.length() == 0L) return "Version files are missing ($name.json). Re-download the version."

        val root = runCatching { JSONObject(json.readText()) }.getOrNull()
            ?: return "Version JSON is corrupted ($name.json). Re-download the version."

        // inheritsFrom chain: a loader profile without its parent crashes in Tools.getVersionInfo
        val parent = root.optString("inheritsFrom", "")
        if (parent.isNotEmpty()) {
            val pj = File(dir.parentFile, "$parent/$parent.json")
            if (!pj.isFile) return "Parent version '$parent' is not installed (needed by $name)."
        } else {
            // "jar" key = profile reuses another version's client jar
            val jarName = root.optString("jar", name)
            val ok = File(dir, "$name.jar").isFile || File(dir.parentFile, "$jarName/$jarName.jar").isFile
            if (!ok) return "Client jar is missing ($jarName.jar). Connect to the internet and launch again to download it."
        }
        return null
    }

    /** Runtime floor required by mods in the mods folder (0 if unknown). */
    @JvmStatic
    fun javaNeededByMods(): Int =
        ModCompatReport.reports.maxOfOrNull { ModNativeCompatScanner.javaForMajor(it.maxClassMajor) } ?: 0

    /** Highest installed runtime name that satisfies [need], or null. */
    @JvmStatic
    fun runtimeFor(need: Int): String? = MultiRTUtils.getNearestJreName(need)
}
