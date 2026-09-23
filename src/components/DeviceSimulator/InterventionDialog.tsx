import React, { useState } from 'react';
import { AlertTriangle, ShieldAlert, BookmarkPlus, Check, X, Sparkles } from 'lucide-react';
import { InterventionRequest } from '../../types/agent';

interface InterventionDialogProps {
  request: InterventionRequest;
  onResolve: (answer: any, shouldSaveToProfile: boolean) => void;
  onIgnore: () => void;
}

export const InterventionDialog: React.FC<InterventionDialogProps> = ({ request, onResolve, onIgnore }) => {
  const [userAnswer, setUserAnswer] = useState<string>('');
  const [askSaveToProfile, setAskSaveToProfile] = useState<boolean>(false);
  const [isCaptchaResolved, setIsCaptchaResolved] = useState<boolean>(false);

  const isCaptcha = request.type === 'CAPTCHA';
  const isConflict = request.type === 'CONFLICT';

  const handleConfirmAnswer = () => {
    if (!userAnswer.trim()) return;

    if (request.suggestedSaveField) {
      // Prompt Section 7: "Deseja salvar esta informação no seu perfil?"
      setAskSaveToProfile(true);
    } else {
      onResolve(userAnswer, false);
    }
  };

  const handleSaveDecision = (shouldSave: boolean) => {
    onResolve(userAnswer, shouldSave);
  };

  const handleCaptchaSolved = () => {
    setIsCaptchaResolved(true);
    setTimeout(() => {
      onResolve('CAPTCHA_RESOLVIDO', false);
    }, 400);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div className="bg-slate-900 border border-slate-700/80 rounded-2xl max-w-md w-full p-5 shadow-2xl text-white space-y-4">
        {/* Header with Alert Icon */}
        <div className="flex items-center space-x-3 border-b border-slate-800 pb-3">
          <div
            className={`w-10 h-10 rounded-xl flex items-center justify-center ${
              isCaptcha ? 'bg-amber-500/20 text-amber-400' : 'bg-rose-500/20 text-rose-400'
            }`}
          >
            {isCaptcha ? <ShieldAlert className="w-5 h-5" /> : <AlertTriangle className="w-5 h-5" />}
          </div>
          <div>
            <h3 className="font-bold text-sm tracking-wide text-white uppercase">{request.title}</h3>
            <p className="text-xs text-slate-400">O robô autônomo pausou para segurança</p>
          </div>
        </div>

        {/* Section 7 Learning Step: Ask to save to profile */}
        {askSaveToProfile ? (
          <div className="space-y-4 py-2">
            <div className="bg-emerald-500/10 border border-emerald-500/30 rounded-xl p-3 space-y-1">
              <div className="flex items-center space-x-2 text-emerald-400 font-semibold text-xs">
                <BookmarkPlus className="w-4 h-4" />
                <span>Aprendizado do Perfil (Seção 7)</span>
              </div>
              <p className="text-xs text-slate-300">
                Você respondeu: <strong className="text-white">"{userAnswer}"</strong>.
              </p>
              <p className="text-xs text-slate-400">
                Deseja salvar esta informação no seu perfil para reutilizar em pesquisas futuras?
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3 pt-2">
              <button
                onClick={() => handleSaveDecision(true)}
                className="py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-xl flex items-center justify-center space-x-1.5 transition-colors shadow-md shadow-emerald-600/20"
              >
                <Check className="w-4 h-4" />
                <span>[ SIM, SALVAR ]</span>
              </button>
              <button
                onClick={() => handleSaveDecision(false)}
                className="py-2.5 px-4 bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs rounded-xl flex items-center justify-center space-x-1.5 transition-colors"
              >
                <X className="w-4 h-4" />
                <span>[ NÃO SALVAR ]</span>
              </button>
            </div>
          </div>
        ) : isCaptcha ? (
          /* CAPTCHA Resolver Box (Section 16) */
          <div className="space-y-4">
            <div className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-3.5 space-y-2">
              <p className="text-xs text-amber-200 leading-relaxed">
                <strong>Motivo:</strong> CAPTCHA / desafio anti-robô detectado. Por motivos de conformidade ética e termos
                de serviço, o agente autônomo não quebra CAPTCHAs.
              </p>
            </div>

            <p className="text-xs text-slate-400">
              Por favor, resolva a verificação na tela do dispositivo e clique abaixo para continuar a automação.
            </p>

            <button
              onClick={handleCaptchaSolved}
              className={`w-full py-3 px-4 font-bold text-xs rounded-xl flex items-center justify-center space-x-2 transition-all shadow-lg ${
                isCaptchaResolved
                  ? 'bg-emerald-600 text-white'
                  : 'bg-emerald-500 hover:bg-emerald-400 text-slate-950 shadow-emerald-500/20'
              }`}
            >
              <Check className="w-4 h-4" />
              <span>{isCaptchaResolved ? 'Resolvido! Continuando...' : '[ RESOLVER E CONTINUAR ]'}</span>
            </button>
          </div>
        ) : isConflict ? (
          /* Conflict Resolver Box (Section 26) */
          <div className="space-y-4">
            <div className="bg-rose-500/10 border border-rose-500/30 rounded-xl p-3 space-y-2 text-xs">
              <p className="text-rose-300 font-semibold">⚠ CONFLITO DE INFORMAÇÃO DETECTADO</p>
              <p className="text-slate-300">
                Valor no Perfil: <strong className="text-white">{String(request.profileValue)}</strong>
              </p>
              <p className="text-slate-300">
                Nova Resposta: <strong className="text-amber-400">{String(request.currentValue)}</strong>
              </p>
            </div>
            <div className="grid grid-cols-2 gap-3 pt-2">
              <button
                onClick={() => onResolve(request.currentValue, true)}
                className="py-2.5 px-3 bg-amber-600 hover:bg-amber-500 text-white font-bold text-xs rounded-xl"
              >
                [ ATUALIZAR PERFIL ]
              </button>
              <button
                onClick={() => onResolve(request.profileValue, false)}
                className="py-2.5 px-3 bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs rounded-xl"
              >
                [ MANTER PERFIL ]
              </button>
            </div>
          </div>
        ) : (
          /* Standard Missing Info / Manual Prompt (Section 6) */
          <div className="space-y-3">
            <div className="bg-slate-950/70 border border-slate-800 rounded-xl p-3 space-y-1">
              <span className="text-[11px] text-slate-400">Pergunta da pesquisa:</span>
              <p className="text-xs font-semibold text-white">"{request.questionText}"</p>
              <p className="text-[11px] text-rose-400 pt-1">Essa informação não está disponível no perfil.</p>
            </div>

            {/* Answer Options if available */}
            {request.options && request.options.length > 0 ? (
              <div className="space-y-1.5 pt-1">
                <span className="text-xs font-medium text-slate-300">Escolha uma resposta:</span>
                <div className="grid grid-cols-1 gap-1.5 max-h-40 overflow-y-auto pr-1">
                  {request.options.map((opt) => (
                    <button
                      key={opt}
                      onClick={() => setUserAnswer(opt)}
                      className={`text-left text-xs p-2.5 rounded-lg border transition-all ${
                        userAnswer === opt
                          ? 'border-emerald-500 bg-emerald-500/20 text-white font-bold'
                          : 'border-slate-800 bg-slate-800/40 text-slate-300 hover:bg-slate-800'
                      }`}
                    >
                      {opt}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <div className="space-y-1 pt-1">
                <label className="text-xs font-medium text-slate-300">Digite a resposta correta:</label>
                <input
                  type="text"
                  value={userAnswer}
                  onChange={(e) => setUserAnswer(e.target.value)}
                  placeholder="Ex: 9500"
                  className="w-full text-xs p-2.5 bg-slate-950 border border-slate-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            )}

            {/* Bottom Actions */}
            <div className="grid grid-cols-2 gap-3 pt-2">
              <button
                onClick={handleConfirmAnswer}
                disabled={!userAnswer.trim()}
                className="py-2.5 px-4 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white font-bold text-xs rounded-xl flex items-center justify-center space-x-1.5 transition-colors shadow-md shadow-emerald-600/20"
              >
                <Check className="w-3.5 h-3.5" />
                <span>[ RESPONDER AGORA ]</span>
              </button>
              <button
                onClick={onIgnore}
                className="py-2.5 px-4 bg-slate-800 hover:bg-slate-700 text-slate-400 font-semibold text-xs rounded-xl transition-colors"
              >
                <span>[ IGNORAR ]</span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
