import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import { version as appVersion } from "./package.json";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;

// A fresh id per build. Baked into the bundle (__BUILD_ID__) and also written
// to dist/version.json, so a running client can compare the two and know when
// a newer build has been deployed — the update path that works in the Tauri/
// WebKitGTK desktop shell, where service workers don't fire update events.
const buildId = String(Date.now());

// Emit dist/version.json holding the current build id.
const emitVersion: Plugin = {
  name: "emit-version",
  generateBundle() {
    this.emitFile({
      type: "asset",
      fileName: "version.json",
      source: JSON.stringify({ buildId }),
    });
  },
};

// https://vite.dev/config/
export default defineConfig(async () => ({
  define: {
    __BUILD_ID__: JSON.stringify(buildId),
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  plugins: [
    react(),
    tailwindcss(),
    emitVersion,
    VitePWA({
      // "prompt": when a new version is deployed the app shows an "Actualizar"
      // banner instead of silently swapping — see src/features/update/.
      registerType: "prompt",
      includeAssets: ["apple-touch-icon.png", "favicon.ico", "favicon-32.png"],
      manifest: {
        name: "Finanzas",
        short_name: "Finanzas",
        description: "Finanzas personales",
        lang: "es-MX",
        display: "standalone",
        start_url: "/",
        background_color: "#0b0a16",
        theme_color: "#0b0a16",
        icons: [
          { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
          {
            src: "/icon-512-maskable.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        // Precache the app shell; NEVER cache financial data — /api/* always
        // hits the network (default: no runtime caching for unlisted routes,
        // and navigateFallback serves the shell offline).
        navigateFallback: "/index.html",
        navigateFallbackDenylist: [/^\/api\//],
        // version.json must always come from the network (it's the update
        // signal); precaching it would freeze the build id the client sees.
        globIgnores: ["**/version.json"],
      },
    }),
  ],

  // Vite options tailored for Tauri development and only applied in `tauri dev` or `tauri build`
  //
  // 1. prevent Vite from obscuring rust errors
  clearScreen: false,
  // 2. tauri expects a fixed port, fail if that port is not available
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    // `vite dev` + `wrangler dev` side by side: API calls go to the worker
    proxy: {
      "/api": "http://localhost:8787",
    },
    hmr: host
      ? {
          protocol: "ws",
          host,
          port: 1421,
        }
      : undefined,
    watch: {
      // 3. tell Vite to ignore watching `src-tauri`
      ignored: ["**/src-tauri/**"],
    },
  },
}));
