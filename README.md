# 📱 Telegram Bridge (Paper & Multiplatform Fork)

<div align="center">

[![Minecraft 1.21.11](https://img.shields.io/badge/Minecraft-1.21.11-brightgreen.svg)](https://minecraft.net/)
[![Platform Paper](https://img.shields.io/badge/Platform-Paper%20%7C%20Fabric%20%7C%20NeoForge-blue.svg)](https://papermc.io/)
[![Client Companion](https://img.shields.io/badge/Companion_Mod-tgbridge--client-55aaff.svg?logo=fabric)](https://github.com/Durhol/tgbridge-client)
[![License MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![GitHub Releases](https://img.shields.io/badge/Release-v0.9.14-informational.svg)](https://github.com/Durhol/tgbridge/releases)

<p align="center">
  <img src="icon.png" width="128" height="128" alt="Telegram Bridge Icon">
</p>

**Telegram Bridge** is a feature-rich, high-performance bridge synchronizing Minecraft and Telegram chats.

This is an enhanced fork of [vanutp/tgbridge](https://github.com/vanutp/tgbridge) maintained by **Durhol**, updated for Minecraft 1.21.11 and featuring rich bidirectional media networking with the companion [**tgbridge-client (Fabric)**](https://github.com/Durhol/tgbridge-client) mod.

</div>

---

## 🚀 What's New in this Fork?

While preserving 100% backward compatibility for vanilla players (who see clickable fallback indicators like `[Photo]` and `[Voice Message]`), this fork enables rich multimedia communication when paired with the companion [**tgbridge-client**](https://github.com/Durhol/tgbridge-client) mod:

* 🖼️ **Zero-Latency In-Chat Thumbnails:** The server generates lightweight 32x20 image thumbnails embedded directly into network packets. Modded clients render photo previews instantly without external HTTP delays or exposing Telegram bot tokens.
* 📦 **Safe Chunked Media Streaming:** Both photos and voice messages are transferred between client and server in 12 KB chunks, completely bypassing Minecraft's strict 32 KB `CustomPacketPayload` disconnect limits.
* 🎙️ **Voice Messages in Game:** Incoming Telegram voice notes are downloaded, cached, and streamed on-demand to players who click `[Voice Message]`.
* 📋 **In-Game Photo Uploads:** Modded clients can paste screenshots (`Ctrl + V`) directly into chat and upload them to Telegram topics.
* 💬 **Reply & Mention Sync:** Two-way message quoting and replies.

---

## 📸 Core Features

- **Multiplatform:** Supports Paper, Fabric, Forge, and NeoForge.
- **Telegram Topics:** Full support for dedicated forum topics in Telegram supergroups.
- **Bi-directional Sync:** Minecraft chat $\leftrightarrow$ Telegram messages.
- **Event Forwarding:** Player join/leave messages, deaths, and advancements.
- **Highly Configurable:** Granular toggles for message formatting, filters, webhook/long-polling, and permissions.

---

## 🛠️ Server Installation

1. Download the latest `tgbridge-0.9.14-paper.jar` from [Releases](https://github.com/Durhol/tgbridge/releases).
2. Place the JAR into your server's `plugins/` directory.
3. Start the server once to generate `plugins/tgbridge/config.yml`.
4. Insert your Telegram Bot token and target chat/topic ID into `config.yml`.
5. Restart the server.
6. *(Recommended)* Tell your players to install the [**tgbridge-client**](https://github.com/Durhol/tgbridge-client) mod for inline photo previews, fullscreen viewer, and voice notes!

---

## 🔨 Building from Source

```bash
git clone https://github.com/Durhol/tgbridge.git
cd tgbridge
./gradlew :paper:assemble      # On Linux/macOS
.\gradlew.bat :paper:assemble  # On Windows
```

The compiled plugin will be at `modules/paper/build/libs/tgbridge-paper-0.9.14.jar` (and in `build/release/`).

---

## 👤 Author & Contacts

* **Fork Author & Enhancements:** Durhol
* **Telegram:** [@tyxap](https://t.me/tyxap) or [@Durhol](https://t.me/Durhol)
* **GitHub:** [https://github.com/Durhol](https://github.com/Durhol)
* **Original Plugin Author:** [@vanutp](https://github.com/vanutp) ([vanutp/tgbridge](https://github.com/vanutp/tgbridge))

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Original code copyright (c) 2021-2024 vanutp. Enhancements copyright (c) 2026 Durhol.
