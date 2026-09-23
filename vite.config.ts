import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig } from 'vite';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig(() => {
  return {
    plugins: [
      {
        name: 'suppress-vite-hmr-noise',
        transformIndexHtml: {
          order: 'pre',
          handler() {
            return [
              {
                tag: 'script',
                attrs: { type: 'text/javascript' },
                children: `/* Suppress Vite WebSocket noise in AI Studio preview */
(function(){
  var origErr=console.error;
  console.error=function(){
    var a=arguments[0];
    if(typeof a==='string'&&(a.indexOf('[vite]')!==-1||a.indexOf('websocket')!==-1))return;
    origErr.apply(console,arguments);
  };
  var origWarn=console.warn;
  console.warn=function(){
    var a=arguments[0];
    if(typeof a==='string'&&(a.indexOf('[vite]')!==-1||a.indexOf('websocket')!==-1))return;
    origWarn.apply(console,arguments);
  };
})();`,
                injectTo: 'head-prepend',
              },
            ];
          },
        },
      },
      react(),
      tailwindcss(),
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['icon.svg', 'apple-touch-icon.png', 'pwa-192x192.png', 'pwa-512x512.png'],
        manifest: {
          id: '/',
          name: 'Research Agent — Android Auto-Fill & Automation Studio',
          short_name: 'ResearchAgent',
          description: 'Agente autônomo inteligente para Android com bolha flutuante, Accessibility Service e preenchimento seguro.',
          theme_color: '#0f172a',
          background_color: '#020617',
          display: 'standalone',
          orientation: 'portrait-primary',
          start_url: '/',
          scope: '/',
          icons: [
            {
              src: '/pwa-192x192.png',
              sizes: '192x192',
              type: 'image/png',
              purpose: 'any',
            },
            {
              src: '/pwa-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'any',
            },
            {
              src: '/pwa-maskable-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2}'],
        },
        devOptions: {
          enabled: false,
        },
      }),
    ],
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname ?? '.', '.'),
      },
    },
    server: {
      hmr: false,
      watch: null,
    },
  };
});
