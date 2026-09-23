import React from 'react';
import {
  Smartphone,
  UserCheck,
  BarChart3,
  ShieldCheck,
  Code2,
  Volume2,
  VolumeX,
  Download,
} from 'lucide-react';
import { AutomationMode } from '../types/agent';
import { PWAInstallButton } from './PWAInstallButton';

interface HeaderProps {
  activeTab: 'simulator' | 'profile' | 'dashboard' | 'privacy' | 'code';
  onTabChange: (tab: 'simulator' | 'profile' | 'dashboard' | 'privacy' | 'code') => void;
  mode: AutomationMode;
  onModeChange: (mode: AutomationMode) => void;
  isMuted: boolean;
  onToggleMute: () => void;
  onExportJson: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  activeTab,
  onTabChange,
  mode,
  onModeChange,
  isMuted,
  onToggleMute,
  onExportJson,
}) => {
  return (
    <header className="bg-slate-900 border-b border-slate-800 text-white sticky top-0 z-40 shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo and Title */}
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-emerald-500 via-teal-400 to-cyan-500 flex items-center justify-center shadow-lg shadow-emerald-500/20">
              <span className="text-xl font-black text-slate-950">●</span>
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="font-bold text-lg tracking-tight text-white">RESEARCH AGENT</span>
                <span className="text-xs bg-emerald-500/20 text-emerald-400 font-medium px-2 py-0.5 rounded border border-emerald-500/30">
                  Android Auto-Fill OS
                </span>
              </div>
              <p className="text-xs text-slate-400 hidden sm:block">
                Agente Inteligente de Pesquisas • Zero Alucinação • Accessibility Service
              </p>
            </div>
          </div>

          {/* Navigation Tabs */}
          <nav className="flex items-center space-x-1 sm:space-x-2 bg-slate-800/80 p-1 rounded-xl border border-slate-700/60">
            <button
              onClick={() => onTabChange('simulator')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'simulator'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm font-bold'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
              }`}
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span>Simulador Android</span>
            </button>

            <button
              onClick={() => onTabChange('profile')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'profile'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm font-bold'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
              }`}
            >
              <UserCheck className="w-3.5 h-3.5" />
              <span>Meus Dados</span>
            </button>

            <button
              onClick={() => onTabChange('dashboard')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'dashboard'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm font-bold'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
              }`}
            >
              <BarChart3 className="w-3.5 h-3.5" />
              <span>Dashboard & Logs</span>
            </button>

            <button
              onClick={() => onTabChange('privacy')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'privacy'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm font-bold'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
              }`}
            >
              <ShieldCheck className="w-3.5 h-3.5" />
              <span>Privacidade</span>
            </button>

            <button
              onClick={() => onTabChange('code')}
              className={`flex items-center space-x-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                activeTab === 'code'
                  ? 'bg-emerald-500 text-slate-950 shadow-sm font-bold'
                  : 'text-slate-300 hover:text-white hover:bg-slate-700/50'
              }`}
            >
              <Code2 className="w-3.5 h-3.5" />
              <span>Código Kotlin</span>
            </button>
          </nav>

          {/* Quick Controls: Install PWA, Mode Switcher, Audio, Export */}
          <div className="flex items-center space-x-2">
            {/* Direct PWA Android Install Button */}
            <PWAInstallButton />

            {/* Mode Selector */}
            <div className="hidden lg:flex items-center bg-slate-800 border border-slate-700 rounded-lg p-1 text-xs">
              <button
                onClick={() => onModeChange('MANUAL')}
                className={`px-2 py-1 rounded transition-colors ${
                  mode === 'MANUAL' ? 'bg-amber-500/20 text-amber-300 font-semibold' : 'text-slate-400 hover:text-white'
                }`}
                title="Manual: Sugere respostas e aguarda confirmação do usuário"
              >
                Manual
              </button>
              <button
                onClick={() => onModeChange('ASSISTIDO')}
                className={`px-2 py-1 rounded transition-colors ${
                  mode === 'ASSISTIDO' ? 'bg-blue-500/20 text-blue-300 font-semibold' : 'text-slate-400 hover:text-white'
                }`}
                title="Assistido: Preenche dados de alta confiança mas pede confirmação antes de avançar"
              >
                Assistido
              </button>
              <button
                onClick={() => onModeChange('AUTOMATICO')}
                className={`px-2 py-1 rounded transition-colors ${
                  mode === 'AUTOMATICO' ? 'bg-emerald-500/20 text-emerald-300 font-semibold' : 'text-slate-400 hover:text-white'
                }`}
                title="Automático: Preenche e avança autonomamente quando comprovado pelo perfil"
              >
                Automático
              </button>
            </div>

            {/* Audio chime toggle */}
            <button
              onClick={onToggleMute}
              className={`p-2 rounded-lg border transition-colors ${
                isMuted
                  ? 'bg-slate-800 border-slate-700 text-slate-400 hover:text-white'
                  : 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400 hover:bg-emerald-500/20'
              }`}
              title={isMuted ? 'Ativar avisos sonoros (🔔)' : 'Desativar avisos sonoros'}
            >
              {isMuted ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
            </button>

            {/* Export JSON quick button */}
            <button
              onClick={onExportJson}
              className="flex items-center space-x-1 text-xs bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 px-2.5 py-1.5 rounded-lg transition-colors"
              title="Exportar Perfil em formato JSON"
            >
              <Download className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Exportar JSON</span>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};
