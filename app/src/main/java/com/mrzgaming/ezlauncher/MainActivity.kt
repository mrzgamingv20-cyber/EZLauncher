package com.mrzgaming.ezlauncher

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
            Toast.makeText(this, "Dipilih: ${selected.name}", Toast.LENGTH_SHORT).show()
            // TODO: lanjut ke proses download & install rootfs
        }

        setContentView(listView)
    }
}
