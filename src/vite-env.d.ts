/// <reference types="vite/client" />
/// <reference types="vite-plugin-pwa/react" />
/// <reference types="vite-plugin-pwa/info" />

// Build id baked in at compile time (see vite.config.ts). Compared against the
// deployed /version.json to detect updates even where the service worker can't
// (the Tauri/WebKitGTK desktop shell).
declare const __BUILD_ID__: string;
