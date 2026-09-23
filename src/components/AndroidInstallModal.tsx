import React, { useState } from 'react';
import { Download, Smartphone, QrCode, Check, Copy, ExternalLink, HelpCircle, X } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface AndroidInstallModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidInstallModal: React.FC<AndroidInstallModalProps> = ({ isOpen, onClose }) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const currentUrl = typeof window !== 'undefined' ? window.location.href : 'https://...';

  const handleCopyLink = () => {
    navigator.clipboard.writeText(currentUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleNativeInstall = async () => {
    const success = await install();
    if (success) {
      onClose();
    }
  };

  // Generate QR code URL via reliable public SVG API (qrserver)
  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(
    currentUrl
  )}&bgcolor=0f172a&color=10b981&margin=1`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fade-in">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-lg w-full p-6 shadow-2xl relative overflow-hidden text-slate-100">
        {/* Glow accent */}
        <div className="absolute top-0 right-0 w-48 h-48 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-full bg-slate-800/60 hover:bg-slate-800 transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Header */}
        <div className="flex items-center space-x-3 mb-5">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-emerald-500 to-teal-400 flex items-center justify-center text-slate-950 font-black shadow-lg shadow-emerald-500/20">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              Baixar / Instalar no Android
              <span className="text-[10px] bg-emerald-500/20 text-emerald-300 font-semibold px-2 py-0.5 rounded-full border border-emerald-500/30">
                PWA Nativo
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              Instale o Research Agent diretamente no seu celular Android sem precisar da Play Store.
            </p>
          </div>
        </div>

        {/* Direct Action if prompt available */}
        {isInstallable && (
          <div className="mb-5 p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/30 flex items-center justify-between">
            <div>
              <p className="text-xs font-semibold text-emerald-300">Instalação Direta Disponível!</p>
              <p className="text-[11px] text-emerald-400/80">O navegador detectou suporte nativo.</p>
            </div>
            <button
              onClick={handleNativeInstall}
              className="px-4 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-emerald-500/25 transition-all flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" />
              Instalar Agora
            </button>
          </div>
        )}

        {isInstalled && (
          <div className="mb-5 p-3 rounded-2xl bg-blue-950/40 border border-blue-500/30 flex items-center gap-2 text-xs text-blue-300">
            <Check className="w-4 h-4 text-blue-400 shrink-0" />
            <span>O aplicativo já está instalado neste dispositivo!</span>
          </div>
        )}

        {/* QR Code and Link to open on Android */}
        <div className="bg-slate-950 p-4 rounded-2xl border border-slate-800 space-y-4 mb-5">
          <div className="flex flex-col sm:flex-row items-center gap-4">
            <div className="bg-slate-900 p-2 rounded-xl border border-slate-800 shrink-0">
              <img
                src={qrCodeUrl}
                alt="QR Code para abrir no celular"
                className="w-28 h-28 rounded-lg"
              />
            </div>
            <div className="flex-1 space-y-1.5 text-center sm:text-left">
              <span className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center justify-center sm:justify-start gap-1">
                <QrCode className="w-3.5 h-3.5 text-emerald-400" />
                Escanear pelo celular
              </span>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                Abra a câmera do seu Android para ler o QR Code ou use o link direto abaixo no Google Chrome.
              </p>
            </div>
          </div>

          {/* URL Copy Bar */}
          <div className="flex items-center gap-2 bg-slate-900 p-2 rounded-xl border border-slate-800">
            <input
              type="text"
              readOnly
              value={currentUrl}
              className="bg-transparent text-xs text-slate-300 flex-1 px-2 font-mono truncate focus:outline-none"
            />
            <button
              onClick={handleCopyLink}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-semibold rounded-lg text-emerald-400 border border-slate-700 transition-colors shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copiado!' : 'Copiar Link'}
            </button>
          </div>
        </div>

        {/* Step by step Android instructions */}
        <div className="space-y-2 mb-6">
          <h4 className="text-xs font-bold text-slate-300 uppercase tracking-wider flex items-center gap-1.5">
            <HelpCircle className="w-3.5 h-3.5 text-emerald-400" />
            Passo a passo no Google Chrome do Android:
          </h4>
          <ol className="text-xs text-slate-300 space-y-2 list-decimal list-inside pl-1 bg-slate-950/60 p-3.5 rounded-2xl border border-slate-800/80 leading-relaxed">
            <li>
              Abra o link no navegador <strong>Google Chrome</strong> do seu Android.
            </li>
            <li>
              Toque no menu de três pontos <strong>(⋮)</strong> no canto superior direito do Chrome.
            </li>
            <li>
              Selecione <strong>"Adicionar à tela inicial"</strong> ou <strong>"Instalar aplicativo"</strong>.
            </li>
            <li>
              Confirme em <strong>"Instalar"</strong>. O ícone aparecerá junto aos seus outros apps!
            </li>
          </ol>
        </div>

        {/* Footer buttons */}
        <div className="flex items-center justify-end gap-3 pt-2 border-t border-slate-800/80">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
          >
            Fechar
          </button>
          <a
            href={currentUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition-colors flex items-center gap-1.5"
          >
            <ExternalLink className="w-3.5 h-3.5" />
            Abrir URL Externa
          </a>
        </div>
      </div>
    </div>
  );
};
