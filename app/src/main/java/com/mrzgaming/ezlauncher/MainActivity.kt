package com.mrzgaming.ezlauncher

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
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

    private val badgeColors = mapOf(
        "Ubuntu 24.04" to 0xFFE95420.toInt(),
        "Debian 12" to 0xFFA80030.toInt(),
        "Alpine" to 0xFF0D597F.toInt(),
        "EZOS" to 0xFF7C4DFF.toInt(),
        "Custom URL..." to 0xFF555566.toInt()
    )

    inner class DistroAdapter : BaseAdapter() {
        override fun getCount() = distros.size
        override fun getItem(position: Int) = distros[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.list_item_distro, parent, false)

            val distro = distros[position]
            val nameView = view.findViewById<TextView>(R.id.distroName)
            val statusView = view.findViewById<TextView>(R.id.distroStatus)
            val iconView = view.findViewById<TextView>(R.id.distroIcon)

            nameView.text = distro.name
            iconView.text = if (distro.name == "Custom URL...") "+" else distro.name.take(1)

            val bg = iconView.background
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(badgeColors[distro.name] ?: 0xFF555566.toInt())
            }

            if (distro.name == "Custom URL...") {
                statusView.text = "Masukkan link rootfs sendiri"
                statusView.setTextColor(0xFF888899.toInt())
            } else if (isInstalled(distro.name)) {
                statusView.text = "● Terinstall"
                statusView.setTextColor(0xFF4CAF50.toInt())
            } else {
                statusView.text = "○ Belum diinstall"
                statusView.setTextColor(0xFF888899.toInt())
            }

            return view
        }
    }

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
        listView.adapter = DistroAdapter()
    }

    private fun showDistroOptionsDialog(distro: Distro) {
        val options = arrayOf("Buka Terminal", "Install Ulang", "Hapus")
        AlertDialog.Builder(this)
            .setTitle(distro.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testProot(distro.name)
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
                when {
                    entry.isDirectory -> outFile.mkdirs()
                    entry.isSymbolicLink -> {
                        outFile.parentFile?.mkdirs()
                        outFile.delete()
                        try {
                            android.system.Os.symlink(entry.linkName, outFile.absolutePath)
                        } catch (e: Exception) {
                            // ignore broken symlink creation errors
                        }
                    }
                    else -> {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            tarInput.copyTo(out)
                        }
                    }
                }
                entry = tarInput.nextTarEntry
            }
        }
    }

    private fun testProot(distroName: String) {
        CoroutineScope(Dispatchers.Main).launch {
            statusText.text = "Testing proot untuk $distroName..."
            val result = withContext(Dispatchers.IO) {
                runProotCommand(distroName, listOf("cat", "/etc/os-release"))
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Hasil proot: $distroName")
                .setMessage(result)
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun runProotCommand(distroName: String, command: List<String>): String {
        return try {
            val prootBinary = File(applicationInfo.nativeLibraryDir, "libproot.so")
            val rootfsDir = File(filesDir, "distros/$distroName")

            val fullCommand = mutableListOf(
                prootBinary.absolutePath,
                "-0",
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-w", "/"
            )
            fullCommand.addAll(command)

            val pb = ProcessBuilder(fullCommand)
            pb.environment()["PROOT_TMP_DIR"] = cacheDir.absolutePath
            pb.environment()["PATH"] = "/bin:/usr/bin:/sbin:/usr/sbin"
            pb.environment()["HOME"] = "/root"
            pb.redirectErrorStream(true)

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            output.ifBlank { "(tidak ada output, exit code: ${process.exitValue()})" }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
