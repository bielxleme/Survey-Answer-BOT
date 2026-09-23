import React, { useState } from 'react';
import { Smartphone, Package, Download } from 'lucide-react';
import { AndroidInstallModal } from './AndroidInstallModal';

export const PWAInstallButton: React.FC = () => {
  const [modalOpen, setModalOpen] = useState(false);

  return (
    <>
      <button
        onClick={() => setModalOpen(true)}
        className="flex items-center space-x-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all shadow-md bg-gradient-to-r from-emerald-500 via-teal-400 to-cyan-500 hover:from-emerald-400 hover:to-teal-300 text-slate-950 shadow-emerald-500/20 hover:scale-[1.02] cursor-pointer"
        title="Instalar no Android (Baixar APK ou PWA)"
      >
        <Package className="w-3.5 h-3.5" />
        <span className="hidden sm:inline">Instalar no Android</span>
        <span className="sm:hidden">Instalar</span>
      </button>

      <AndroidInstallModal isOpen={modalOpen} onClose={() => setModalOpen(false)} />
    </>
  );
};
