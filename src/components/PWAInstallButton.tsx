import React, { useState } from 'react';
import { Download, Smartphone, Check } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { AndroidInstallModal } from './AndroidInstallModal';

export const PWAInstallButton: React.FC = () => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [modalOpen, setModalOpen] = useState(false);

  const handleClick = async () => {
    if (isInstallable) {
      const installed = await install();
      if (!installed) {
        setModalOpen(true);
      }
    } else {
      setModalOpen(true);
    }
  };

  return (
    <>
      <button
        onClick={handleClick}
        className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all shadow-sm ${
          isInstalled
            ? 'bg-slate-800 text-emerald-400 border border-emerald-500/30'
            : 'bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-slate-950 shadow-emerald-500/20 hover:scale-[1.02]'
        }`}
        title="Baixar e instalar no Android via PWA ou Web App"
      >
        {isInstalled ? (
          <>
            <Check className="w-3.5 h-3.5 text-emerald-400" />
            <span className="hidden sm:inline">Instalado</span>
          </>
        ) : (
          <>
            <Download className="w-3.5 h-3.5" />
            <Smartphone className="w-3 h-3 -ml-1 text-slate-900" />
            <span>Baixar no Android</span>
          </>
        )}
      </button>

      <AndroidInstallModal isOpen={modalOpen} onClose={() => setModalOpen(false)} />
    </>
  );
};
