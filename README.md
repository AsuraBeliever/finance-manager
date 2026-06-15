<div align="center">

# 💰 Finanzas

**Personal finance in the cloud — wallets, transactions & investments.**

🌐 **[Open the app → finanzas.aseth.workers.dev](https://finanzas.aseth.workers.dev)**

[**English**](README.md) · [Español](README.es.md)

</div>

---

Finanzas is a personal finance manager: track your wallets, income and expenses,
budgets, savings goals, subscriptions and investments — multi‑currency, with MXN
as the base. It runs as a fast web app (PWA) you can install on any device, and as
a native desktop app for Windows and Linux.

## 📲 Install

### On the web (any device — recommended)

1. Open **[finanzas.aseth.workers.dev](https://finanzas.aseth.workers.dev)** in Chrome, Edge or another Chromium browser.
2. Click **Install app** (in the address bar, or inside the app under *Settings → Install app*).
3. On **iPhone/iPad** (Safari): tap **Share → Add to Home Screen**.

It then opens in its own window, works offline for the shell, and updates itself.

### Desktop app (Windows & Linux)

Download the latest installer from the **[Releases page →](https://github.com/AsuraBeliever/finance-manager/releases/latest)**:

| System | File |
| --- | --- |
| **Windows** | `.msi` (or the `.exe` setup) |
| **Linux — any distro** | `.AppImage` — `chmod +x` it and run |
| **Debian / Ubuntu / Mint** | `.deb` |
| **Fedora / openSUSE** | `.rpm` |

> The installers aren't code‑signed yet, so Windows SmartScreen or your distro may
> show an "unknown publisher" warning the first time — choose *Run anyway / Keep*.
> The desktop app is a native window around the web app, so it always stays up to date.

## ✨ Features

- Multi‑wallet, multi‑currency balances (MXN base) with live exchange rates
- Income / expense / transfer transactions with customizable categories
- Dashboard: net worth, monthly flow, spending breakdown and trends
- Budgets, savings goals and recurring subscriptions
- Investment tracking (Nu, CETES and more) with exchange‑rate‑aware valuations
- Installable PWA **and** native desktop app; your data syncs across every device

## 🛠️ Built with

Rust on **Cloudflare Workers** + **D1** (all money math lives in Rust), a **React 19**
PWA frontend, and a **Tauri 2** desktop shell. Money is stored as integer cents.
Architecture, data model and investment formulas live in [`docs/`](docs/PLAN.md).

## 📄 License

Source-available under the **[PolyForm Noncommercial License 1.0.0](LICENSE.md)**: you
may use, study, modify and share it for any **noncommercial** purpose, but commercial
use is not permitted. All commercial rights are reserved by the author.

---

<div align="center">
<sub>A personal project by <a href="https://github.com/AsuraBeliever">AsuraBeliever</a>.</sub>
</div>
