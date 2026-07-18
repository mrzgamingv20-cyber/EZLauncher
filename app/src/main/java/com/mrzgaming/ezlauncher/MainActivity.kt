package com.mrzgaming.ezlauncher

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
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

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        listView = findViewById(R.id.listView)

        refreshList()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = distros[position]
            when {
                selected.name == "Custom URL..." -> showCustomUrlDialog()
                isInstalled(selected.name) -> showDistroOptionsDialog(selected)
                else -> startInstall(selected.name, selected.rootfsUrl)
            }
        }
    }

    private fun isInstalled(name: String): Boolean {
        val dir = File(filesDir, "distros/$name")
        return dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
    }

    private fun refreshList() {
        val names = distros.map { d ->
            if (d.name != "Custom URL..." && isInstalled(d.name)) "${d.name} (terinstall)" else d.name
        }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        listView.adapter = adapter
    }

    private fun showDistroOptionsDialog(distro: Distro) {
        val options = arrayOf("Buka Terminal", "Install Ulang", "Hapus")
        AlertDialog.Builder(this)
            .setTitle(distro.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Fitur terminal segera hadir", Toast.LENGTH_SHORT).show()
                    1 -> startInstall(distro.name, distro.rootfsUrl)
                    2 -> confirmDelete(distro)
                }
            }
            .show()
    }

    private fun confirmDelete(distro: Distro) {
        AlertDialog.Builder(this)
            .setTitle("Hapus ${distro.name}?")
            .setMessage("Semua data di dalam distro ini akan hilang.")
            .setPositiveButton("Hapus") { _, _ ->
                File(filesDir, "distros/${distro.name}").deleteRecursively()
                statusText.text = "${distro.name} dihapus."
                refreshList()
            }
            .setNegativeButton("Batal", null)
            .show()
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
        progressBar.visibility = View.VISIBLE
        statusText.text = "Menyiapkan $name..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val distroDir = File(filesDir, "distros/$name")
                distroDir.mkdirs()

                val archiveFile = File(cacheDir, "rootfs_${name}.cache")

                if (!archiveFile.exists()) {
                    statusText.text = "Mengunduh $name..."
                    withContext(Dispatchers.IO) {
                        downloadFile(url, archiveFile)
                    }
                } else {
                    statusText.text = "Pakai cache $name (skip download)..."
                }

                statusText.text = "Mengekstrak $name..."

                withContext(Dispatchers.IO) {
                    extractArchive(archiveFile, distroDir, url)
                }

                statusText.text = "$name berhasil diinstall!"
                refreshList()

            } catch (e: Exception) {
                statusText.text = "Gagal install $name: ${e.message}"
            } finally {
                progressBar.visibility = View.GONE
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
