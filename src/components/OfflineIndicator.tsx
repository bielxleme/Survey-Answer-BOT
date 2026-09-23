import React from 'react';
import { WifiOff } from 'lucide-react';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();

  if (isOnline) return null;

  return (
    <div className="fixed bottom-4 left-4 z-50 flex items-center gap-2 rounded-xl bg-amber-500/95 border border-amber-400 px-3.5 py-2 text-xs font-semibold text-slate-950 shadow-2xl backdrop-blur-sm animate-bounce">
      <WifiOff className="w-4 h-4 text-slate-950" />
      <span>Modo Offline — O agente continuará usando dados locais em cache.</span>
    </div>
  );
};
