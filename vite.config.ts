import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";

// @ts-expect-error process is a nodejs global
const host = process.env.TAURI_DEV_HOST;

// https://vite.dev/config/
export default defineConfig(async () => ({
  plugins: [
    react(),
    tailwindcss(),
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
        background_color: "#0f1115",
        theme_color: "#0f1115",
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
