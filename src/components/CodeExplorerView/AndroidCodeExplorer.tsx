import React, { useState } from 'react';
import {
  Code2,
  FileCode,
  FileText,
  Copy,
  Check,
  Download,
  Terminal,
  ExternalLink,
  FolderOpen,
  Search,
  CheckCircle2,
} from 'lucide-react';
import { ANDROID_CODEBASE, AndroidCodeFile } from '../../data/androidCodebase';
import { AndroidInstallModal } from '../AndroidInstallModal';
import { Smartphone } from 'lucide-react';

export const AndroidCodeExplorer: React.FC = () => {
  const [selectedFileIndex, setSelectedFileIndex] = useState(0);
  const [copied, setCopied] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [showInstallModal, setShowInstallModal] = useState(false);

  const selectedFile: AndroidCodeFile = ANDROID_CODEBASE[selectedFileIndex] || ANDROID_CODEBASE[0];

  const filteredFiles = ANDROID_CODEBASE.filter((f) =>
    f.path.toLowerCase().includes(searchTerm.toLowerCase()) ||
    f.description.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const handleCopyCode = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownloadFile = () => {
    const blob = new Blob([selectedFile.content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const fileName = selectedFile.path.split('/').pop() || 'file.txt';
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="max-w-6xl mx-auto py-6 px-4 space-y-6">
      {/* Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <Code2 className="w-5 h-5 text-emerald-400" />
            <h2 className="text-base font-bold text-white uppercase tracking-wider">
              Código-Fonte Nativo Android (Kotlin & Jetpack Compose)
            </h2>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Projeto Android modular completo, incluindo Accessibility Service, WindowManager Overlay, Máquina de Estados e
            Testes Unitários (Seção 45).
          </p>
        </div>

        <div className="flex items-center space-x-2">
          <button
            onClick={() => setShowInstallModal(true)}
            className="flex items-center space-x-1.5 px-3 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs rounded-xl shadow-lg shadow-emerald-500/20 transition-all"
          >
            <Smartphone className="w-3.5 h-3.5" />
            <span>Instalar no Celular</span>
          </button>

          <button
            onClick={handleDownloadFile}
            className="flex items-center space-x-1.5 px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 font-semibold text-xs rounded-xl transition-colors"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Baixar Arquivo Atual</span>
          </button>
        </div>
      </div>

      {/* Code Explorer Layout: File Tree (Left) + Code Viewer (Right) */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Left: File Tree */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-3 shadow-xl space-y-3">
          <div className="relative">
            <Search className="w-3.5 h-3.5 text-slate-500 absolute left-3 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              placeholder="Filtrar arquivos..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full text-xs pl-8 pr-3 py-2 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-1 focus:ring-emerald-500"
            />
          </div>

          <div className="space-y-1 max-h-[580px] overflow-y-auto pr-1">
            {filteredFiles.map((file, idx) => {
              const originalIndex = ANDROID_CODEBASE.findIndex((f) => f.path === file.path);
              const isSelected = originalIndex === selectedFileIndex;

              return (
                <button
                  key={file.path}
                  onClick={() => setSelectedFileIndex(originalIndex)}
                  className={`w-full text-left p-2.5 rounded-xl border transition-all text-xs ${
                    isSelected
                      ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-300 font-bold shadow-sm'
                      : 'bg-slate-950/60 border-slate-800/80 text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                  }`}
                >
                  <div className="flex items-center space-x-2">
                    {file.language === 'kotlin' ? (
                      <FileCode className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                    ) : file.language === 'xml' ? (
                      <FileText className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                    ) : (
                      <FileText className="w-3.5 h-3.5 text-cyan-400 shrink-0" />
                    )}
                    <span className="font-mono text-[11px] truncate flex-1">
                      {file.path.split('/').pop()}
                    </span>
                  </div>
                  <p className="text-[10px] text-slate-500 line-clamp-1 mt-1 pl-5.5">
                    {file.description}
                  </p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Right: Code Viewer */}
        <div className="md:col-span-2 bg-slate-950 border border-slate-800 rounded-2xl shadow-xl flex flex-col overflow-hidden">
          {/* File Header */}
          <div className="bg-slate-900 px-4 py-3 border-b border-slate-800 flex items-center justify-between">
            <div className="flex items-center space-x-2 min-w-0">
              <span className="text-[10px] uppercase font-bold px-2 py-0.5 rounded bg-slate-800 text-emerald-400 border border-slate-700">
                {selectedFile.language}
              </span>
              <span className="font-mono text-xs text-slate-200 truncate">{selectedFile.path}</span>
            </div>

            <button
              onClick={handleCopyCode}
              className="flex items-center space-x-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-300 text-xs font-semibold rounded-lg transition-colors shrink-0"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copiado!' : 'Copiar'}</span>
            </button>
          </div>

          {/* Description line */}
          <div className="bg-slate-900/60 px-4 py-2 border-b border-slate-800/80 text-[11px] text-slate-400">
            {selectedFile.description}
          </div>

          {/* Code Text with Line Numbers */}
          <div className="p-4 font-mono text-xs overflow-x-auto max-h-[580px] overflow-y-auto text-slate-200 leading-relaxed">
            <pre className="text-emerald-300">
              <code>{selectedFile.content}</code>
            </pre>
          </div>
        </div>
      </div>

      <AndroidInstallModal
        isOpen={showInstallModal}
        onClose={() => setShowInstallModal(false)}
      />
    </div>
  );
};
