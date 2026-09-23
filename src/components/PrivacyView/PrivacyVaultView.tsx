import React, { useState } from 'react';
import { ShieldCheck, Lock, EyeOff, KeyRound, Sparkles, Database, FileText } from 'lucide-react';
import { UserProfile } from '../../types/profile';
import { ProfileService } from '../../services/profileService';

interface PrivacyVaultViewProps {
  profile: UserProfile;
}

export const PrivacyVaultView: React.FC<PrivacyVaultViewProps> = ({ profile }) => {
  const [testQuestion, setTestQuestion] = useState('Qual é a sua profissão e tempo de experiência?');

  const filteredSubset = ProfileService.getPrivacyFilteredSubset([testQuestion], profile);

  return (
    <div className="max-w-6xl mx-auto py-6 px-4 space-y-6">
      {/* Header Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
        <div className="flex items-center space-x-3 mb-2">
          <div className="w-10 h-10 rounded-xl bg-cyan-500/20 text-cyan-400 flex items-center justify-center">
            <Lock className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-white uppercase tracking-wider">
              Cofre de Privacidade & Isolamento de Dados (Seção 21)
            </h2>
            <p className="text-xs text-slate-400">
              Arquitetura local com criptografia em repouso e envio estritamente seletivo de atributos para o LLM.
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mt-4 pt-4 border-t border-slate-800 text-xs">
          <div className="flex items-start space-x-2.5 p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <KeyRound className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <strong className="text-white block font-semibold">Android Keystore (AES-256)</strong>
              <span className="text-slate-400 text-[11px]">
                Chaves criptográficas isoladas em hardware com EncryptedSharedPreferences.
              </span>
            </div>
          </div>

          <div className="flex items-start space-x-2.5 p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <EyeOff className="w-4 h-4 text-cyan-400 shrink-0 mt-0.5" />
            <div>
              <strong className="text-white block font-semibold">Filtragem Seletiva (Zero-Leak)</strong>
              <span className="text-slate-400 text-[11px]">
                O perfil inteiro nunca é enviado para APIs de IA. Apenas a chave mínima relevante.
              </span>
            </div>
          </div>

          <div className="flex items-start space-x-2.5 p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <Database className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <div>
              <strong className="text-white block font-semibold">Sem Credenciais Financeiras</strong>
              <span className="text-slate-400 text-[11px]">
                Proibição estrita de armazenamento de senhas, PINs, códigos 2FA ou chaves privadas.
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Interactive Selective Sandbox Visualizer (Section 21 Flow) */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-5">
        <div>
          <div className="flex items-center space-x-2">
            <Sparkles className="w-4 h-4 text-emerald-400" />
            <h3 className="font-bold text-sm text-white">Simulador de Transmissão Seletiva</h3>
          </div>
          <p className="text-xs text-slate-400 mt-1">
            Digite qualquer pergunta de formulário para verificar visualmente como o pipeline isola apenas o dado
            necessário e bloqueia os outros 95% do perfil.
          </p>
        </div>

        {/* Input Sandbox */}
        <div className="space-y-2">
          <label className="text-xs font-semibold text-slate-300">Pergunta da Pesquisa:</label>
          <div className="flex gap-2">
            <input
              type="text"
              value={testQuestion}
              onChange={(e) => setTestQuestion(e.target.value)}
              className="flex-1 text-xs p-3 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
              placeholder="Ex: Você possui filhos? / Qual é sua renda? / Qual sua idade?"
            />
            <button
              onClick={() => setTestQuestion('Você possui animais de estimação?')}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl transition-colors font-semibold shrink-0"
            >
              Exemplo Pet
            </button>
            <button
              onClick={() => setTestQuestion('Qual o modelo do seu celular?')}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs rounded-xl transition-colors font-semibold shrink-0"
            >
              Exemplo Celular
            </button>
          </div>
        </div>

        {/* Comparison: Full Profile (Protected) vs Filtered Subset (Dispatched) */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
          {/* Dispatched Payload */}
          <div className="bg-slate-950 border border-emerald-500/30 rounded-xl p-4 space-y-2">
            <div className="flex items-center justify-between text-xs pb-2 border-b border-slate-800">
              <span className="font-bold text-emerald-400">Payload Enviado ao Motor Semântico:</span>
              <span className="text-[10px] bg-emerald-500/10 text-emerald-300 px-2 py-0.5 rounded font-mono">
                {Object.keys(filteredSubset).length} Categorias Filtradas
              </span>
            </div>
            <pre className="text-xs font-mono text-emerald-300 overflow-x-auto max-h-56 p-2 bg-slate-900/60 rounded-lg">
              {JSON.stringify(filteredSubset, null, 2)}
            </pre>
            <p className="text-[11px] text-slate-400">
              ✓ Somente os atributos estritamente essenciais para responder à pergunta foram expostos.
            </p>
          </div>

          {/* Shielded Profile Attributes */}
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-2">
            <div className="flex items-center justify-between text-xs pb-2 border-b border-slate-800">
              <span className="font-bold text-slate-300">Dados do Usuário Retidos no Dispositivo:</span>
              <span className="text-[10px] bg-cyan-500/10 text-cyan-300 px-2 py-0.5 rounded font-mono">
                100% Protegido em Repouso
              </span>
            </div>
            <div className="text-xs text-slate-400 space-y-2 max-h-56 overflow-y-auto pr-2">
              <div className="p-2 rounded bg-slate-900 border border-slate-800">
                <span className="text-slate-300 font-semibold">Identidade & Documentos:</span>
                <p className="text-[11px] text-slate-500 mt-0.5">Nome completo, CEP, endereço residencial bloqueados.</p>
              </div>
              <div className="p-2 rounded bg-slate-900 border border-slate-800">
                <span className="text-slate-300 font-semibold">Finanças Pessoais:</span>
                <p className="text-[11px] text-slate-500 mt-0.5">Bancos, investimentos e cartões retidos no Keystore.</p>
              </div>
              <div className="p-2 rounded bg-slate-900 border border-slate-800">
                <span className="text-slate-300 font-semibold">Família & Filhos:</span>
                <p className="text-[11px] text-slate-500 mt-0.5">Idade de dependentes protegidos localmente.</p>
              </div>
            </div>
            <p className="text-[11px] text-slate-400">
              🛡️ Criptografado com MasterKey AES-256 no armazenamento do dispositivo Android.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};
