import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import App from './App.tsx';
import './index.css';
import { registerSW } from 'virtual:pwa-register';

// Register Service Worker in production builds for Android PWA installability
if (typeof window !== 'undefined' && 'serviceWorker' in navigator && import.meta.env.PROD) {
  try {
    registerSW({
      immediate: true,
      onNeedRefresh() {
        console.log('[PWA] Nova versão disponível');
      },
      onOfflineReady() {
        console.log('[PWA] Aplicativo pronto para uso offline');
      },
    });
  } catch (err) {
    console.debug('[PWA] Service worker registration skipped:', err);
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

