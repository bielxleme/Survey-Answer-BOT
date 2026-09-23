import React, { useState } from 'react';
import { X, Check, ArrowRight, ArrowLeft, Wand2, Sparkles } from 'lucide-react';
import { UserProfile } from '../../types/profile';

interface ProfileWizardProps {
  profile: UserProfile;
  onSave: (updated: UserProfile) => void;
  onClose: () => void;
}

export const ProfileWizard: React.FC<ProfileWizardProps> = ({ profile, onSave, onClose }) => {
  const [step, setStep] = useState(0);
  const [draft, setDraft] = useState<UserProfile>(JSON.parse(JSON.stringify(profile)));

  const questions = [
    {
      title: 'Você possui filhos?',
      category: 'familia',
      render: () => (
        <div className="grid grid-cols-2 gap-3 pt-3">
          <button
            onClick={() => {
              setDraft((prev) => ({
                ...prev,
                familia: { ...prev.familia, tem_filhos: true, quantidade_filhos: 1 },
              }));
              setStep((s) => s + 1);
            }}
            className={`py-3 px-4 rounded-xl border text-xs font-bold transition-all ${
              draft.familia.tem_filhos === true
                ? 'bg-emerald-600 text-white border-emerald-600'
                : 'bg-slate-800 border-slate-700 text-slate-200 hover:bg-slate-700'
            }`}
          >
            [ SIM, TENHO FILHOS ]
          </button>
          <button
            onClick={() => {
              setDraft((prev) => ({
                ...prev,
                familia: { ...prev.familia, tem_filhos: false, quantidade_filhos: null },
              }));
              setStep((s) => s + 1);
            }}
            className={`py-3 px-4 rounded-xl border text-xs font-bold transition-all ${
              draft.familia.tem_filhos === false
                ? 'bg-emerald-600 text-white border-emerald-600'
                : 'bg-slate-800 border-slate-700 text-slate-200 hover:bg-slate-700'
            }`}
          >
            [ NÃO POSSUO FILHOS ]
          </button>
        </div>
      ),
    },
    {
      title: 'Qual é a sua ocupação ou profissão principal?',
      category: 'trabalho',
      render: () => (
        <div className="space-y-3 pt-3">
          <input
            type="text"
            placeholder="Ex: Consultor de TI, Advogado, Designer..."
            value={draft.trabalho.profissao}
            onChange={(e) =>
              setDraft((prev) => ({
                ...prev,
                trabalho: { ...prev.trabalho, profissao: e.target.value },
              }))
            }
            className="w-full text-xs p-3 bg-slate-950 border border-slate-700 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
          <div className="flex justify-end">
            <button
              onClick={() => setStep((s) => s + 1)}
              className="py-2 px-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl flex items-center space-x-1"
            >
              <span>Continuar</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      ),
    },
    {
      title: 'Qual é a sua faixa de renda mensal?',
      category: 'financas',
      render: () => (
        <div className="space-y-2 pt-3">
          {[
            'Até R$ 3.000',
            'De R$ 3.001 a R$ 6.000',
            'De R$ 6.001 a R$ 10.000',
            'De R$ 10.001 a R$ 15.000',
            'Acima de R$ 15.000',
          ].map((faixa) => (
            <button
              key={faixa}
              onClick={() => {
                setDraft((prev) => ({
                  ...prev,
                  financas: { ...prev.financas, faixa_renda_pessoal: faixa },
                }));
                setStep((s) => s + 1);
              }}
              className={`w-full text-left p-3 rounded-xl border text-xs font-semibold transition-all ${
                draft.financas.faixa_renda_pessoal === faixa
                  ? 'bg-emerald-500/20 border-emerald-500 text-emerald-300'
                  : 'bg-slate-800/60 border-slate-700 text-slate-300 hover:bg-slate-800'
              }`}
            >
              {faixa}
            </button>
          ))}
        </div>
      ),
    },
    {
      title: 'Configuração Inicial Concluída!',
      category: 'conclusao',
      render: () => (
        <div className="space-y-4 py-4 text-center">
          <div className="w-12 h-12 rounded-full bg-emerald-500/20 text-emerald-400 mx-auto flex items-center justify-center">
            <Check className="w-6 h-6 stroke-[3]" />
          </div>
          <p className="text-xs text-slate-300 max-w-sm mx-auto">
            Os dados essenciais foram configurados. Você sempre poderá editar ou adicionar novos campos na aba{' '}
            <strong className="text-white">Meus Dados</strong>.
          </p>
          <button
            onClick={() => {
              onSave(draft);
              onClose();
            }}
            className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl shadow-lg shadow-emerald-600/20"
          >
            [ SALVAR E FINALIZAR ASSISTENTE ]
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-700 rounded-2xl max-w-lg w-full p-6 shadow-2xl text-white space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-3">
          <div className="flex items-center space-x-2">
            <Wand2 className="w-4 h-4 text-emerald-400" />
            <h3 className="font-bold text-sm text-white">Assistente: "Vamos configurar seu perfil"</h3>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-white p-1">
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Progress bar */}
        <div className="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
          <div
            className="bg-emerald-500 h-full transition-all duration-300"
            style={{ width: `${((step + 1) / questions.length) * 100}%` }}
          ></div>
        </div>

        {/* Step Question */}
        <div className="space-y-2">
          <span className="text-[11px] font-mono text-emerald-400">
            Etapa {step + 1} de {questions.length}
          </span>
          <h4 className="font-bold text-sm text-slate-100">{questions[step].title}</h4>
          {questions[step].render()}
        </div>

        {/* Navigation bottom */}
        {step > 0 && step < questions.length - 1 && (
          <div className="flex items-center justify-between pt-2 border-t border-slate-800">
            <button
              onClick={() => setStep((s) => s - 1)}
              className="text-xs text-slate-400 hover:text-white flex items-center space-x-1"
            >
              <ArrowLeft className="w-3.5 h-3.5" />
              <span>Voltar</span>
            </button>
            <button
              onClick={() => {
                onSave(draft);
                onClose();
              }}
              className="text-xs text-slate-400 hover:text-emerald-400"
            >
              Salvar e continuar depois
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
