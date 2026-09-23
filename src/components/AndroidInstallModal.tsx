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
  FileCode2,
  AlertTriangle,
  Layers,
  Sparkles,
  Github,
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
  const [activeTab, setActiveTab] = useState<'apk' | 'pwa' | 'why_google'>('apk');
  const [isZipping, setIsZipping] = useState(false);

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

  const handleDownloadProjectZip = async () => {
    setIsZipping(true);
    try {
      // Try backend endpoint first
      const link = document.createElement('a');
      link.href = '/api/download/android-project.zip';
      link.download = 'ResearchAgent_Android_Project.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (e) {
      // Client-side fallback
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
                APK & PWA
              </span>
            </h3>
            <p className="text-xs text-slate-400">
              Escolha como deseja instalar ou executar o aplicativo no seu dispositivo Android.
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
            <Package className="w-3.5 h-3.5" />
            <span>Gerar APK Nativo (.zip)</span>
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
            <span>Instalar via Web (PWA)</span>
          </button>

          <button
            onClick={() => setActiveTab('why_google')}
            className={`flex-1 flex items-center justify-center space-x-1.5 py-2 px-3 rounded-lg text-xs font-bold transition-all ${
              activeTab === 'why_google'
                ? 'bg-emerald-500 text-slate-950 shadow-sm'
                : 'text-slate-400 hover:text-white hover:bg-slate-800/50'
            }`}
          >
            <HelpCircle className="w-3.5 h-3.5" />
            <span>Por que abriu o Google?</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="overflow-y-auto pr-1 space-y-4 flex-1">
          {/* TAB 1: APK NATeval */}
          {activeTab === 'apk' && (
            <div className="space-y-4">
              {/* Main Download Card */}
              <div className="bg-gradient-to-br from-slate-950 to-slate-900 border border-emerald-500/30 rounded-2xl p-4 sm:p-5 relative overflow-hidden shadow-lg">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-white flex items-center gap-1.5">
                        <Sparkles className="w-4 h-4 text-emerald-400" />
                        Pacote Completo do Projeto Android
                      </span>
                      <span className="text-[10px] bg-emerald-500/10 text-emerald-400 font-semibold px-2 py-0.5 rounded border border-emerald-500/30">
                        47 Arquivos Prontos
                      </span>
                    </div>
                    <p className="text-xs text-slate-300 leading-relaxed">
                      Baixe o projeto pronto com <strong>Serviço de Acessibilidade</strong>, <strong>Bolha Flutuante sobre outros apps</strong>, <strong>Jetpack Compose</strong> e <strong>GitHub Actions</strong> para compilar o APK em 1 clique.
                    </p>
                  </div>

                  <button
                    onClick={handleDownloadProjectZip}
                    disabled={isZipping}
                    className="w-full sm:w-auto px-5 py-3 bg-gradient-to-r from-emerald-500 to-teal-400 hover:from-emerald-400 hover:to-teal-300 text-slate-950 font-black text-xs rounded-xl shadow-lg shadow-emerald-500/25 transition-all flex items-center justify-center gap-2 shrink-0 disabled:opacity-50"
                  >
                    <Download className="w-4 h-4" />
                    {isZipping ? 'Compactando...' : 'Baixar Projeto Android (.ZIP)'}
                  </button>
                </div>

                <div className="mt-4 pt-3 border-t border-slate-800 flex flex-wrap items-center gap-3 text-[11px] text-slate-400">
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> AndroidManifest configurado
                  </span>
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> Accessibility Service XML
                  </span>
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> Overlay Service Kotlin
                  </span>
                  <span className="flex items-center gap-1 text-emerald-400">
                    <Check className="w-3.5 h-3.5" /> Workflow de APK Automático
                  </span>
                </div>
              </div>

              {/* How to generate APK step by step */}
              <div className="space-y-3">
                <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider flex items-center gap-1.5">
                  <Layers className="w-4 h-4 text-emerald-400" />
                  Como transformar este projeto em um arquivo .APK:
                </h4>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {/* Option 1: GitHub Actions (No PC needed) */}
                  <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3.5 space-y-2">
                    <div className="flex items-center gap-2 text-white font-bold text-xs">
                      <div className="p-1 rounded bg-slate-800 text-purple-400">
                        <Github className="w-3.5 h-3.5" />
                      </div>
                      <span>Opção 1: Gerar Grátis pelo GitHub (Sem PC)</span>
                    </div>
                    <ol className="text-[11px] text-slate-300 space-y-1.5 list-decimal list-inside leading-relaxed">
                      <li>Crie um repositório no GitHub (grátis).</li>
                      <li>Envie os arquivos do ZIP baixado.</li>
                      <li>O GitHub detectará o workflow já incluído na pasta <code className="text-emerald-400">.github/workflows</code>.</li>
                      <li>Vá na aba <strong>Actions</strong> e o APK será compilado em 2 minutos!</li>
                      <li>Baixe o <code className="text-emerald-400">app-debug.apk</code> direto no celular.</li>
                    </ol>
                  </div>

                  {/* Option 2: Android Studio */}
                  <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3.5 space-y-2">
                    <div className="flex items-center gap-2 text-white font-bold text-xs">
                      <div className="p-1 rounded bg-slate-800 text-emerald-400">
                        <FileCode2 className="w-3.5 h-3.5" />
                      </div>
                      <span>Opção 2: Android Studio (Computador)</span>
                    </div>
                    <ol className="text-[11px] text-slate-300 space-y-1.5 list-decimal list-inside leading-relaxed">
                      <li>Abra o Android Studio e clique em <strong>Open</strong>.</li>
                      <li>Selecione a pasta descompactada do projeto.</li>
                      <li>Aguarde o Gradle sincronizar as dependências.</li>
                      <li>No menu: <strong>Build → Build Bundle(s) / APK(s) → Build APK(s)</strong>.</li>
                      <li>O arquivo <code className="text-emerald-400">.apk</code> é gerado para instalar no celular!</li>
                    </ol>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: PWA WEB INSTALL */}
          {activeTab === 'pwa' && (
            <div className="space-y-4">
              {/* Direct Action if prompt available */}
              {isInstallable && (
                <div className="p-4 rounded-2xl bg-emerald-950/40 border border-emerald-500/30 flex items-center justify-between">
                  <div>
                    <p className="text-xs font-semibold text-emerald-300">Instalação Direta Disponível!</p>
                    <p className="text-[11px] text-emerald-400/80">O navegador detectou o app como PWA instalável.</p>
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
                <div className="p-3 rounded-2xl bg-blue-950/40 border border-blue-500/30 flex items-center gap-2 text-xs text-blue-300">
                  <Check className="w-4 h-4 text-blue-400 shrink-0" />
                  <span>O aplicativo já está instalado neste dispositivo!</span>
                </div>
              )}

              {/* QR Code and Link to open on Android */}
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
                      Abra a câmera do seu celular Android apontando para este QR Code ou copie o link direto para o Google Chrome.
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
              <div className="space-y-2">
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
                    Confirme em <strong>"Instalar"</strong>. O ícone aparecerá junto aos seus outros aplicativos!
                  </li>
                </ol>
              </div>
            </div>
          )}

          {/* TAB 3: WHY GOOGLE PAGE OPENED */}
          {activeTab === 'why_google' && (
            <div className="space-y-3">
              <div className="bg-amber-950/30 border border-amber-500/30 rounded-2xl p-4 space-y-3 text-xs text-amber-200/90 leading-relaxed">
                <div className="flex items-center gap-2 text-amber-400 font-bold">
                  <AlertTriangle className="w-4 h-4" />
                  <span>Por que o atalho web abriu uma página do Google?</span>
                </div>
                <p>
                  O link fornecido anteriormente (<code className="bg-slate-900 px-1 py-0.5 rounded text-amber-300">ais-dev-*.run.app</code>) roda dentro do ambiente de nuvem do AI Studio protegido por autenticação do Google.
                </p>
                <p>
                  Quando você abriu o link no celular ou criou um atalho, o navegador móvel não encontrou os cookies de login da sua sessão do AI Studio e redirecionou para a página de verificação de login do Google (<code className="bg-slate-900 px-1 py-0.5 rounded text-amber-300">__cookie_check.html</code>).
                </p>
              </div>

              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-4 space-y-3">
                <h4 className="text-xs font-bold text-white flex items-center gap-2">
                  <Check className="w-4 h-4 text-emerald-400" />
                  Como resolver definitivamente:
                </h4>
                <ul className="text-xs text-slate-300 space-y-2 list-disc list-inside leading-relaxed">
                  <li>
                    <strong>Solução Recomendada (Nativo):</strong> Baixe o <strong>Projeto Android (.ZIP)</strong> na aba <strong>"Gerar APK Nativo"</strong>. Ele é 100% independente, não usa login do Google e possui suporte nativo à <strong>Bolha Flutuante</strong> sobre outros apps (Shopee, Toluna, Chrome, etc.) e <strong>Accessibility Service</strong>.
                  </li>
                  <li>
                    <strong>Para usar via Navegador:</strong> No celular, faça login no Google Chrome com a mesma conta de e-mail do AI Studio (<code className="text-emerald-400">bielxleme@gmail.com</code>) antes de abrir o link, para que o Google valide a sessão e permita adicionar à tela inicial sem erro.
                  </li>
                </ul>
              </div>
            </div>
          )}
        </div>

        {/* Footer buttons */}
        <div className="flex items-center justify-between gap-3 pt-3 border-t border-slate-800 shrink-0 mt-3">
          <button
            onClick={handleDownloadProjectZip}
            disabled={isZipping}
            className="flex items-center gap-1.5 text-xs text-emerald-400 hover:text-emerald-300 font-semibold transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Baixar ZIP do Código</span>
          </button>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition-colors"
            >
              Fechar
            </button>
            <a
              href="/api/download/android-project.zip"
              download="ResearchAgent_Android_Project.zip"
              className="px-4 py-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs transition-colors flex items-center gap-1.5"
            >
              <Download className="w-3.5 h-3.5" />
              Download Direto
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
