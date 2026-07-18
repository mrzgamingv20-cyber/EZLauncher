package com.mrzgaming.ezlauncher

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    data class Distro(val name: String, val rootfsUrl: String)

    private val distros = listOf(
        Distro("Ubuntu 24.04", "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"),
        Distro("Debian 12", "https://github.com/termux/proot-distro/releases/download/v4.18.0/debian-bookworm-aarch64-pd-v4.18.0.tar.xz"),
        Distro("Alpine", "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz"),
        Distro("EZOS", "https://github.com/mrzgamingv20-cyber/ezos-repo/releases/download/v1.0/ezos-rootfs.tar.gz"),
        Distro("Custom URL...", "")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val listView = ListView(this)
        val names = distros.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = distros[position]
            if (selected.name == "Custom URL...") {
                showCustomUrlDialog()
            } else {
                startInstall(selected.name, selected.rootfsUrl)
            }
        }

        setContentView(listView)
    }

    private fun showCustomUrlDialog() {
        val input = EditText(this)
        input.hint = "https://.../rootfs.tar.gz"
        AlertDialog.Builder(this)
            .setTitle("Masukkan URL rootfs")
            .setView(input)
            .setPositiveButton("Install") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    startInstall("Custom", url)
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun startInstall(name: String, url: String) {
        Toast.makeText(this, "Mulai install $name...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val distroDir = File(filesDir, "distros/$name")
                distroDir.mkdirs()

                val archiveFile = File(cacheDir, "rootfs_download")

                withContext(Dispatchers.IO) {
                    downloadFile(url, archiveFile)
                }

                Toast.makeText(this@MainActivity, "Download selesai, extracting...", Toast.LENGTH_SHORT).show()

                withContext(Dispatchers.IO) {
                    extractArchive(archiveFile, distroDir, url)
                }

                archiveFile.delete()

                Toast.makeText(this@MainActivity, "$name berhasil diinstall!", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun downloadFile(urlStr: String, outFile: File) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connect()
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractArchive(archiveFile: File, destDir: File, originalUrl: String) {
        val fileInput = archiveFile.inputStream()
        val decompressed = if (originalUrl.endsWith(".xz")) {
            XZInputStream(fileInput)
        } else {
            GzipCompressorInputStream(fileInput)
        }

        TarArchiveInputStream(decompressed).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out ->
                        tarInput.copyTo(out)
                    }
                }
                entry = tarInput.nextTarEntry
            }
        }
    }
}
