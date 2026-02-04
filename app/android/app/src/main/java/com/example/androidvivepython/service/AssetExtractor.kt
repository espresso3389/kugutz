package jp.espresso3389.kugutz.service

import android.content.Context
import android.util.Log
import java.io.File

class AssetExtractor(private val context: Context) {
    fun extractServerAssets(): File? {
        return try {
            val targetDir = File(context.filesDir, "server")
            targetDir.mkdirs()
            copyAssetDir("server", targetDir)
            targetDir
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to extract server assets", ex)
            null
        }
    }

    fun extractPythonRuntime(): File? {
        return try {
            val targetDir = File(context.filesDir, "pyenv")
            targetDir.mkdirs()
            copyAssetDir("pyenv", targetDir)
            targetDir
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to extract Python runtime", ex)
            null
        }
    }

    fun extractDropbearIfMissing(): File? {
        return try {
            val targetDir = File(context.filesDir, "bin")
            targetDir.mkdirs()
            val outFile = File(targetDir, "dropbear")
            if (outFile.exists()) {
                return outFile
            }
            val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: return null
            val assetPath = "bin/$abi/dropbear"
            if (!assetExists(assetPath)) {
                Log.w(TAG, "Dropbear binary not found in assets for ABI $abi")
                return null
            }
            copyAssetFile(assetPath, outFile)
            outFile.setExecutable(true, true)
            outFile
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to extract Dropbear binary", ex)
            null
        }
    }

    fun extractUiAssetsIfMissing(): File? {
        return try {
            val targetDir = File(context.filesDir, "www")
            if (targetDir.exists()) {
                val entries = targetDir.list()
                if (entries != null && entries.isNotEmpty()) {
                    val localVersion = File(targetDir, ".version").takeIf { it.exists() }?.readText()?.trim()
                    val assetVersion = readAssetText("www/.version")
                    if (assetVersion != null && assetVersion.isNotBlank() && assetVersion != localVersion) {
                        return resetUiAssets()
                    }
                    return targetDir
                }
            }
            targetDir.mkdirs()
            copyAssetDir("www", targetDir)
            targetDir
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to extract UI assets", ex)
            null
        }
    }

    fun resetUiAssets(): File? {
        return try {
            val root = context.filesDir
            val targetDir = File(root, "www")
            val tmpDir = File(root, "www.tmp")
            val backupDir = File(root, "www.bak")

            if (tmpDir.exists()) {
                deleteRecursive(tmpDir)
            }
            tmpDir.mkdirs()
            copyAssetDir("www", tmpDir)

            if (backupDir.exists()) {
                deleteRecursive(backupDir)
            }
            if (targetDir.exists()) {
                if (!targetDir.renameTo(backupDir)) {
                    deleteRecursive(targetDir)
                }
            }

            if (!tmpDir.renameTo(targetDir)) {
                // Fallback: try to restore previous version.
                if (backupDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                deleteRecursive(tmpDir)
                return null
            }

            if (backupDir.exists()) {
                deleteRecursive(backupDir)
            }
            targetDir
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to reset UI assets", ex)
            null
        }
    }

    private fun copyAssetDir(assetPath: String, outDir: File) {
        val assetManager = context.assets
        val entries = assetManager.list(assetPath) ?: return
        if (entries.isEmpty()) {
            val outFile = File(outDir, assetPath.substringAfterLast("/"))
            copyAssetFile(assetPath, outFile)
            return
        }
        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childEntries = assetManager.list(childAssetPath)
            if (childEntries == null || childEntries.isEmpty()) {
                copyAssetFile(childAssetPath, File(outDir, entry))
            } else {
                val childOut = File(outDir, entry)
                childOut.mkdirs()
                copyAssetDir(childAssetPath, childOut)
            }
        }
    }

    private fun copyAssetFile(assetPath: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readAssetText(assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        file.delete()
    }

    companion object {
        private const val TAG = "AssetExtractor"
    }
}
