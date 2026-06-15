<div align="center">

# 💰 Finanzas

**Finanzas personales en la nube — carteras, transacciones e inversiones.**

🌐 **[Abre la app → finanzas.aseth.workers.dev](https://finanzas.aseth.workers.dev)**

[English](README.md) · [**Español**](README.es.md)

</div>

---

Finanzas es un gestor de finanzas personales: controla tus carteras, ingresos y
gastos, presupuestos, metas de ahorro, suscripciones e inversiones — multimoneda,
con MXN como base. Funciona como app web (PWA) que puedes instalar en cualquier
dispositivo, y como app de escritorio nativa para Windows y Linux.

## 📲 Instalación

### En la web (cualquier dispositivo — recomendado)

1. Abre **[finanzas.aseth.workers.dev](https://finanzas.aseth.workers.dev)** en Chrome, Edge u otro navegador Chromium.
2. Pulsa **Instalar app** (en la barra de direcciones, o dentro de la app en *Ajustes → Instalar app*).
3. En **iPhone/iPad** (Safari): toca **Compartir → Agregar a pantalla de inicio**.

Se abre en su propia ventana, funciona sin conexión (el esqueleto) y se actualiza sola.

### App de escritorio (Windows y Linux)

Descarga el instalador más reciente desde la **[página de versiones →](https://github.com/AsuraBeliever/finance-manager/releases/latest)**:

| Sistema | Archivo |
| --- | --- |
| **Windows** | `.msi` (o el instalador `.exe`) |
| **Linux — cualquier distro** | `.AppImage` — dale permiso (`chmod +x`) y ábrelo |
| **Debian / Ubuntu / Mint** | `.deb` |
| **Fedora / openSUSE** | `.rpm` |

> Los instaladores aún no están firmados, así que Windows SmartScreen o tu distro
> pueden mostrar un aviso de "editor desconocido" la primera vez — elige
> *Ejecutar de todos modos / Conservar*. La app de escritorio es una ventana nativa
> que envuelve la app web, así que siempre está actualizada.

## ✨ Características

- Carteras y saldos multimoneda (base MXN) con tipos de cambio automáticos
- Transacciones de ingreso / gasto / transferencia con categorías personalizables
- Panel: patrimonio, flujo mensual, desglose de gastos y tendencias
- Presupuestos, metas de ahorro y suscripciones recurrentes
- Inversiones (Nu, CETES y más) valuadas con tipos de cambio
- PWA instalable **y** app de escritorio nativa; tus datos se sincronizan en todos tus dispositivos

## 🛠️ Hecho con

Rust sobre **Cloudflare Workers** + **D1** (toda la aritmética de dinero vive en
Rust), un frontend PWA en **React 19** y un shell de escritorio en **Tauri 2**.
El dinero se guarda en centavos enteros. La arquitectura, el modelo de datos y las
fórmulas de inversión están en [`docs/`](docs/PLAN.md).

## 📄 Licencia

Código disponible bajo la **[PolyForm Noncommercial License 1.0.0](LICENSE.md)**: puedes
usarlo, estudiarlo, modificarlo y compartirlo para cualquier fin **no comercial**, pero
**no se permite el uso comercial**. El autor se reserva todos los derechos comerciales.

---

<div align="center">
<sub>Un proyecto personal de <a href="https://github.com/AsuraBeliever">AsuraBeliever</a>.</sub>
</div>
