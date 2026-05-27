package org.searchmob.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the locked dependency/permission decisions: the storage layer MUST NOT pull in the deprecated
 * `androidx.security:security-crypto` / `EncryptedSharedPreferences` or the deprecated
 * `android-database-sqlcipher`, and MUST NOT add any new permission. Runs against the project's build
 * scripts and manifest so a regression is caught at unit-test time, not just on device.
 */
class NoForbiddenDependenciesTest {
    // Gradle runs unit tests with the module dir as the working directory; fall back to ./app if a
    // runner uses the repo root instead.
    private val moduleDir: File =
        File("").absoluteFile.let { cwd ->
            when {
                File(cwd, "build.gradle.kts").exists() && File(cwd, "src/main").exists() -> cwd
                File(cwd, "app/build.gradle.kts").exists() -> File(cwd, "app")
                else -> cwd
            }
        }

    @Test
    fun buildScriptsDoNotReferenceForbiddenArtifacts() {
        val catalog = File(moduleDir.parentFile, "gradle/libs.versions.toml").readText()
        val buildScript = File(moduleDir, "build.gradle.kts").readText()
        val combined = catalog + buildScript

        assertFalse("must not use deprecated security-crypto", combined.contains("security-crypto"))
        assertFalse(
            "must not use deprecated android-database-sqlcipher",
            combined.contains("android-database-sqlcipher"),
        )
        // Confirms the supported artifacts ARE present.
        assertTrue("must use net.zetetic:sqlcipher-android", combined.contains("sqlcipher-android"))
        assertTrue("must use Argon2id (argon2kt)", combined.contains("argon2kt"))
        assertTrue("must use DataStore", combined.contains("datastore"))
    }

    @Test
    fun noSourceImportsDeprecatedSecurityCrypto() {
        // Catch actual usage (an import of the deprecated library), not doc-comment mentions of why we
        // avoid it.
        val srcRoot = File(moduleDir, "src/main/java/org/searchmob")
        val offenders =
            srcRoot
                .walkTopDown()
                .filter { it.extension == "kt" }
                .filter { f ->
                    f
                        .readLines()
                        .any { line ->
                            val trimmed = line.trim()
                            trimmed.startsWith("import androidx.security.crypto") ||
                                trimmed.startsWith("import androidx.security")
                        }
                }.toList()
        assertTrue("no source may import androidx.security crypto: $offenders", offenders.isEmpty())
    }

    @Test
    fun manifestAddsNoStorageOrNewPermission() {
        val manifest = File(moduleDir, "src/main/AndroidManifest.xml").readText()
        // No storage permissions are introduced by the storage layer (all files are app-internal).
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("READ_EXTERNAL_STORAGE"))
        assertFalse(manifest.contains("MANAGE_EXTERNAL_STORAGE"))
    }
}
