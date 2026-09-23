import React, { useState } from 'react';
import {
  Download,
  Smartphone,
  QrCode,
  Check,
  Copy,
  ExternalLink,
  HelpCircle,
  X,
  Package,
  Layers,
  Sparkles,
  ShieldCheck,
  FileCode2,
} from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';
import { generateAndroidProjectZip } from '../utils/androidProjectZip';

interface AndroidInstallModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const AndroidInstallModal: React.FC<AndroidInstallModalProps> = ({ isOpen, onClose }) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [copied, setCopied] = useState(false);
  const [activeTab, setActiveTab] = useState<'apk' | 'source' | 'pwa'>('apk');
  const [isZipping, setIsZipping] = useState(false);
  const [isDownloadingApk, setIsDownloadingApk] = useState(false);

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

  const handleDownloadApk = () => {
    setIsDownloadingApk(true);
    const link = document.createElement('a');
    link.href = '/api/download/ResearchAgent.apk';
    link.download = 'ResearchAgent.apk';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => setIsDownloadingApk(false), 2500);
  };

  const handleDownloadProjectZip = async () => {
    setIsZipping(true);
    try {
      const link = document.createElement('a');
      link.href = '/api/download/android-project.zip';
      link.download = 'ResearchAgent_Android_Project.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch {
      try {
        const blob = await generateAndroidProjectZip();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'ResearchAgent_Android_Project.zip';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
      } catch (err) {
        console.error('Erro ao baixar ZIP:', err);
      }
    } finally {
      setTimeout(() => setIsZipping(false), 1000);
    }
  };

  const qrCodeUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(
    currentUrl
  )}&bgcolor=0f172a&color=10b981&margin=1`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-slate-950/85 backdrop-blur-md animate-fade-in overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-3xl max-w-2xl w-full p-5 sm:p-6 shadow-2xl relative overflow-hidden text-slate-100 my-auto max-h-[92vh] flex flex-col">
        {/* Glow accent */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-emerald-500/10 rounded-full blur-3xl pointer-events-none" />

        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-full bg-slate-800/60 hover:bg-slate-800 transition-colors z-10"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Header */}
        <div className="flex items-center space-x-3 mb-4 shrink-0">
          <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-emerald-500 via-teal-400 to-cyan-500 flex items-center justify-center text-slate-950 font-black shadow-lg shadow-emerald-500/20 shrink-0">
            <Smartphone className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-white flex items-center gap-2">
              Instalação do Research Agent no Android
              <span className="text-[10px] bg-emerald-500/20 text-emerald-300 font-semibold px-2 py-0.5 rounded-full border border-emerald-500/30">
                APK Oficial
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              Baixe o arquivo APK diretamente ou escolha outro método de instalação.
            </p>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="flex space-x-1 bg-slate-950/80 p-1 rounded-xl border border-slate-800 mb-4 shrink-0">
          <button
            onClick={() => setActiveTab('apk')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg text-xs font-bold transition-all ${
              activeTab === 'apk'
                ? 'bg-emerald-500 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            <Download className="w-3.5 h-3.5" />
            <span>Baixar APK Direto (.apk)</span>
          </button>

          <button
            onClick={() => setActiveTab('source')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg text-xs font-bold transition-all ${
              activeTab === 'source'
                ? 'bg-emerald-500 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            <Package className="w-3.5 h-3.5" />
            <span>Código-Fonte (.zip)</span>
          </button>

          <button
            onClick={() => setActiveTab('pwa')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg text-xs font-bold transition-all ${
              activeTab === 'pwa'
                ? 'bg-emerald-500 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Instalar via Navegador (PWA)</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="overflow-y-auto pr-1 space-y-4 flex-1">
          {/* TAB 1: APK DIRETO (.APK) */}
          {activeTab === 'apk' && (
            <div className="space-y-4">
              {/* Main Download Card */}
              <div className="bg-gradient-to-br from-slate-950 via-slate-900 to-emerald-950/40 border border-emerald-500/40 rounded-2xl p-5 relative overflow-hidden shadow-xl">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="space-y-1.5">
                    <div className="flex items-center gap-2">
                      <span className="text-base font-bold text-white flex items-center gap-1.5">
                        <Sparkles className="w-4 h-4 text-emerald-400" />
                        ResearchAgent.apk
                      </span>
                      <span className="text-[11px] bg-emerald-500/20 text-emerald-300 font-semibold px-2.5 py-0.5 rounded-full border border-emerald-500/40">
                        1.45 MB • Pronto para Instalar
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      Arquivo de instalação nativo no formato <strong>.APK</strong>. Assinado e preparado para funcionar diretamente no seu celular Android sem precisar da Play Store e sem depender de login de navegador.
                    </p>
                  </div>

                  <button
                    onClick={handleDownloadApk}
                    disabled={isDownloadingApk}
                    className="w-full sm:w-auto px-6 py-3.5 bg-gradient-to-r from-emerald-500 via-teal-400 to-cyan-400 hover:from-emerald-400 hover:to-cyan-300 text-slate-950 font-black text-sm rounded-xl shadow-lg shadow-emerald-500/30 hover:scale-[1.02] active:scale-[0.98] transition-all flex items-center justify-center gap-2 shrink-0 cursor-pointer disabled:opacity-50"
                  >
                    <Download className="w-5 h-5 text-slate-950" />
                    <span>{isDownloadingApk ? 'Baixando APK...' : 'Baixar Arquivo APK'}</span>
                  </button>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-800 flex flex-wrap items-center gap-3 text-[11px] text-slate-400">
                  <span className="flex items-center gap-1 text-emerald-400">
                    <ShieldCheck className="w-3.5 h-3.5" /> Assinado (v1 + v2 + v3)
                  </span>
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> Compatível com Android 5.0 até Android 15
                  </span>
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> 100% Funcional Offline
                  </span>
                </div>
              </div>

              {/* 3 Simple Steps */}
              <div className="bg-slate-950/80 border border-slate-800 rounded-2xl p-4 space-y-3">
                <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider flex items-center gap-1.5">
                  <HelpCircle className="w-4 h-4 text-emerald-400" />
                  Passo a passo simples para instalar no celular:
                </h4>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="bg-slate-900/90 border border-slate-800/80 p-3 rounded-xl space-y-1">
                    <div className="w-6 h-6 rounded-full bg-emerald-500/20 text-emerald-400 font-bold text-xs flex items-center justify-center">
                      1
                    </div>
                    <p className="text-xs font-bold text-white">Baixar o APK</p>
                    <p className="text-[11px] text-slate-400 leading-snug">
                      Toque no botão verde acima. O download do arquivo <code className="text-emerald-400">ResearchAgent.apk</code> começará.
                    </p>
                  </div>

                  <div className="bg-slate-900/90 border border-slate-800/80 p-3 rounded-xl space-y-1">
                    <div className="w-6 h-6 rounded-full bg-emerald-500/20 text-emerald-400 font-bold text-xs flex items-center justify-center">
                      2
                    </div>
                    <p className="text-xs font-bold text-white">Tocar no arquivo</p>
                    <p className="text-[11px] text-slate-400 leading-snug">
                      Puxe a barra de notificações do celular ou abra a pasta <strong>Downloads</strong> e toque no arquivo baixado.
                    </p>
                  </div>

                  <div className="bg-slate-900/90 border border-slate-800/80 p-3 rounded-xl space-y-1">
                    <div className="w-6 h-6 rounded-full bg-emerald-500/20 text-emerald-400 font-bold text-xs flex items-center justify-center">
                      3
                    </div>
                    <p className="text-xs font-bold text-white">Confirmar Instalação</p>
                    <p className="text-[11px] text-slate-400 leading-snug">
                      Se o Android pedir permissão para instalar fontes desconhecidas, toque em <strong>Permitir</strong> e confirme <strong>Instalar</strong>.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: CÓDIGO-FONTE ANDROID (.ZIP) */}
          {activeTab === 'source' && (
            <div className="space-y-4">
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 sm:p-5 space-y-4">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white flex items-center gap-1.5">
                        <FileCode2 className="w-4 h-4 text-emerald-400" />
                        Projeto Android Studio Completo (.ZIP)
                      </span>
                      <span className="text-[10px] bg-slate-800 text-slate-300 font-semibold px-2 py-0.5 rounded">
                        47 Arquivos Fonte
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      Contém o código-fonte em Kotlin nativo, Jetpack Compose, <code className="text-emerald-400">SurveyAccessibilityService.kt</code>, serviço de bolha flutuante e o arquivo de build para compilar no Android Studio ou GitHub Actions.
                    </p>
                  </div>

                  <button
                    onClick={handleDownloadProjectZip}
                    disabled={isZipping}
                    className="w-full sm:w-auto px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-emerald-400 border border-emerald-500/30 font-bold text-xs rounded-xl shadow-md transition-all flex items-center justify-center gap-2 shrink-0 disabled:opacity-50"
                  >
                    <Download className="w-4 h-4" />
                    {isZipping ? 'Compactando...' : 'Baixar Código (.ZIP)'}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: PWA WEB */}
          {activeTab === 'pwa' && (
            <div className="space-y-4">
              {isInstallable && (
                <div className="p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/30 flex items-center justify-between">
                  <div>
                    <p className="text-xs font-semibold text-emerald-300">Instalação Direta via Chrome!</p>
                    <p className="text-[11px] text-emerald-400/80">O navegador detectou o app como PWA instalável.</p>
                  </div>
                  <button
                    onClick={handleNativeInstall}
                    className="px-4 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-emerald-500/25 transition-all flex items-center gap-1.5"
                  >
                    <Download className="w-4 h-4" />
                    Instalar PWA
                  </button>
                </div>
              )}

              {isInstalled && (
                <div className="p-3 rounded-2xl bg-blue-950/40 border border-blue-500/30 flex items-center gap-2 text-xs text-blue-300">
                  <Check className="w-4 h-4 text-blue-400 shrink-0" />
                  <span>O aplicativo já está instalado no navegador deste dispositivo!</span>
                </div>
              )}

              {/* QR Code and Link */}
              <div className="bg-slate-950 p-4 rounded-2xl border border-slate-800 space-y-4">
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
                      Abra a câmera do celular para escanear ou copie o link direto para o Google Chrome.
                    </p>
                  </div>
                </div>

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
            </div>
          )}
        </div>

        {/* Footer buttons */}
        <div className="flex items-center justify-between gap-3 pt-3 border-t border-slate-800 shrink-0 mt-3">
          <div className="text-[11px] text-slate-400 flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
            <span>APK Assinado • Instalação Rápida</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
            >
              Fechar
            </button>
            <a
              href="/api/download/ResearchAgent.apk"
              download="ResearchAgent.apk"
              className="px-4 py-2 rounded-xl bg-gradient-to-r from-emerald-500 to-teal-400 hover:from-emerald-400 hover:to-teal-300 text-slate-950 font-bold text-xs transition-colors flex items-center gap-1.5"
            >
              <Download className="w-3.5 h-3.5" />
              Baixar APK (.apk)
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
