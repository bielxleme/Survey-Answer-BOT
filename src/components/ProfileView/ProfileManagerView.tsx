import React, { useState } from 'react';
import {
  User,
  Users,
  GraduationCap,
  Briefcase,
  Wallet,
  Smartphone,
  ShoppingBag,
  Tv,
  Car,
  Plane,
  Utensils,
  Dog,
  Home,
  Plus,
  Trash2,
  CheckCircle2,
  AlertCircle,
  HelpCircle,
  MinusCircle,
  Wand2,
  Upload,
  Download,
  RotateCcw,
  Sparkles,
} from 'lucide-react';
import { UserProfile, FieldStatus } from '../../types/profile';
import { ProfileService } from '../../services/profileService';

interface ProfileManagerViewProps {
  profile: UserProfile;
  onUpdateProfile: (updated: UserProfile) => void;
  onOpenWizard: () => void;
  onOpenJsonEditor: () => void;
  onResetProfile: () => void;
}

type CategoryTab =
  | 'identidade'
  | 'familia'
  | 'educacao'
  | 'trabalho'
  | 'financas'
  | 'tecnologia'
  | 'consumo'
  | 'entretenimento'
  | 'transporte'
  | 'viagens'
  | 'alimentacao'
  | 'animais'
  | 'casa'
  | 'personalizados';

export const ProfileManagerView: React.FC<ProfileManagerViewProps> = ({
  profile,
  onUpdateProfile,
  onOpenWizard,
  onOpenJsonEditor,
  onResetProfile,
}) => {
  const [activeCategory, setActiveCategory] = useState<CategoryTab>('identidade');
  const [newCustomKey, setNewCustomKey] = useState('');
  const [newCustomValue, setNewCustomValue] = useState('');

  const categories: Array<{ id: CategoryTab; label: string; icon: React.ReactNode; desc: string }> = [
    { id: 'identidade', label: 'Identidade', icon: <User className="w-4 h-4" />, desc: 'Nome, nascimento, gênero, cidade e idiomas' },
    { id: 'familia', label: 'Família', icon: <Users className="w-4 h-4" />, desc: 'Estado civil, filhos e moradores' },
    { id: 'educacao', label: 'Educação', icon: <GraduationCap className="w-4 h-4" />, desc: 'Escolaridade, cursos e pós-graduação' },
    { id: 'trabalho', label: 'Trabalho', icon: <Briefcase className="w-4 h-4" />, desc: 'Profissão, cargo, empresa e tempo de atuação' },
    { id: 'financas', label: 'Finanças', icon: <Wallet className="w-4 h-4" />, desc: 'Faixas de renda, bancos e cartões (sem senhas)' },
    { id: 'tecnologia', label: 'Tecnologia', icon: <Smartphone className="w-4 h-4" />, desc: 'Celulares, sistemas operacionais e dispositivos' },
    { id: 'consumo', label: 'Consumo', icon: <ShoppingBag className="w-4 h-4" />, desc: 'Marcas favoritas, frequência e compras online' },
    { id: 'entretenimento', label: 'Entretenimento', icon: <Tv className="w-4 h-4" />, desc: 'Streaming, redes sociais, jogos e música' },
    { id: 'transporte', label: 'Transporte', icon: <Car className="w-4 h-4" />, desc: 'Veículos, transporte público e aplicativos' },
    { id: 'viagens', label: 'Viagens', icon: <Plane className="w-4 h-4" />, desc: 'Frequência de viagens, destinos e companhias' },
    { id: 'alimentacao', label: 'Alimentação', icon: <Utensils className="w-4 h-4" />, desc: 'Hábitos alimentares, restrições e delivery' },
    { id: 'animais', label: 'Animais', icon: <Dog className="w-4 h-4" />, desc: 'Animais de estimação, espécies e produtos pet' },
    { id: 'casa', label: 'Casa & Moradia', icon: <Home className="w-4 h-4" />, desc: 'Tipo de imóvel, cômodos e eletrodomésticos' },
    { id: 'personalizados', label: 'Personalizados', icon: <Plus className="w-4 h-4" />, desc: 'Campos e atributos customizados livres' },
  ];

  const renderStatusBadge = (status: FieldStatus) => {
    switch (status) {
      case 'INFORMADO':
        return (
          <span className="flex items-center space-x-1 text-[10px] font-bold bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded-full">
            <CheckCircle2 className="w-3 h-3" />
            <span>✓ INFORMADO</span>
          </span>
        );
      case 'DERIVAVEL':
        return (
          <span className="flex items-center space-x-1 text-[10px] font-bold bg-cyan-500/10 text-cyan-400 border border-cyan-500/30 px-2 py-0.5 rounded-full">
            <HelpCircle className="w-3 h-3" />
            <span>? DERIVÁVEL</span>
          </span>
        );
      case 'NECESSARIO':
        return (
          <span className="flex items-center space-x-1 text-[10px] font-bold bg-rose-500/10 text-rose-400 border border-rose-500/30 px-2 py-0.5 rounded-full animate-pulse">
            <AlertCircle className="w-3 h-3" />
            <span>! NECESSÁRIO</span>
          </span>
        );
      case 'NAO_CONFIGURADO':
        return (
          <span className="flex items-center space-x-1 text-[10px] font-medium bg-slate-800 text-slate-400 border border-slate-700 px-2 py-0.5 rounded-full">
            <MinusCircle className="w-3 h-3" />
            <span>— NÃO CONFIGURADO</span>
          </span>
        );
    }
  };

  const handleFieldChange = (category: keyof UserProfile, field: string, value: any) => {
    const updated = {
      ...profile,
      [category]: {
        ...(profile[category] as any),
        [field]: value,
      },
    };
    onUpdateProfile(updated);
    ProfileService.saveProfile(updated);
  };

  const handleAddCustomField = () => {
    if (!newCustomKey.trim()) return;
    const updated = {
      ...profile,
      campos_personalizados: {
        ...profile.campos_personalizados,
        [newCustomKey.trim()]: newCustomValue,
      },
    };
    onUpdateProfile(updated);
    ProfileService.saveProfile(updated);
    setNewCustomKey('');
    setNewCustomValue('');
  };

  const handleRemoveCustomField = (key: string) => {
    const updatedCustom = { ...profile.campos_personalizados };
    delete updatedCustom[key];
    const updated = { ...profile, campos_personalizados: updatedCustom };
    onUpdateProfile(updated);
    ProfileService.saveProfile(updated);
  };

  return (
    <div className="max-w-6xl mx-auto py-6 px-4 space-y-6">
      {/* Top Banner & Quick Actions */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="text-emerald-400 font-black text-xl">MEUS DADOS</span>
            <span className="text-xs bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded font-mono">
              Fonte da Verdade Pessoal
            </span>
          </div>
          <p className="text-xs text-slate-400 mt-1 max-w-xl">
            O agente utiliza exclusivamente as informações desta base. Nenhuma informação pessoal é inventada. Se um dado
            necessário faltar, o agente solicitará sua intervenção.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={onOpenWizard}
            className="flex items-center space-x-1.5 px-3 py-2 bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-400 hover:to-teal-400 text-slate-950 font-bold text-xs rounded-xl shadow-md transition-all active:scale-95"
          >
            <Wand2 className="w-3.5 h-3.5" />
            <span>Assistente de Perfil</span>
          </button>

          <button
            onClick={onOpenJsonEditor}
            className="flex items-center space-x-1.5 px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 font-semibold text-xs rounded-xl transition-colors"
          >
            <Upload className="w-3.5 h-3.5" />
            <span>Importar / Ver JSON</span>
          </button>

          <button
            onClick={onResetProfile}
            className="flex items-center space-x-1 px-2.5 py-2 bg-slate-800 hover:bg-rose-950/40 text-slate-400 hover:text-rose-300 border border-slate-700 hover:border-rose-900/60 font-semibold text-xs rounded-xl transition-colors"
            title="Restaurar Perfil Padrão"
          >
            <RotateCcw className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Main Grid: Category Tabs + Form Editor */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {/* Left Side: Category Tabs */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-2 shadow-xl space-y-1 md:max-h-[640px] md:overflow-y-auto">
          {categories.map((cat) => {
            const isActive = activeCategory === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => setActiveCategory(cat.id)}
                className={`w-full flex items-center space-x-3 px-3 py-2.5 rounded-xl text-left transition-all ${
                  isActive
                    ? 'bg-emerald-500/15 border border-emerald-500/30 text-emerald-300 font-bold shadow-sm'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/60'
                }`}
              >
                <div
                  className={`p-1.5 rounded-lg ${
                    isActive ? 'bg-emerald-500 text-slate-950' : 'bg-slate-800 text-slate-400'
                  }`}
                >
                  {cat.icon}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-xs font-semibold truncate">{cat.label}</div>
                  <div className="text-[10px] text-slate-500 truncate">{cat.desc}</div>
                </div>
              </button>
            );
          })}
        </div>

        {/* Right Side: Fields Form Editor */}
        <div className="md:col-span-3 bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl space-y-5">
          {/* Active Category Header */}
          <div className="border-b border-slate-800 pb-3 flex items-center justify-between">
            <div>
              <h3 className="font-bold text-base text-white capitalize">
                {categories.find((c) => c.id === activeCategory)?.label}
              </h3>
              <p className="text-xs text-slate-400 mt-0.5">
                {categories.find((c) => c.id === activeCategory)?.desc}
              </p>
            </div>
            {activeCategory === 'financas' && (
              <span className="text-[10px] bg-amber-500/10 text-amber-300 border border-amber-500/30 px-2 py-1 rounded-lg">
                🛡️ Senhas e chaves bancárias nunca são armazenadas
              </span>
            )}
          </div>

          {/* Form Content by Category */}
          <div className="space-y-4">
            {activeCategory === 'identidade' && (
              <>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">Nome Completo</label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'nome', profile))}
                    </div>
                    <input
                      type="text"
                      value={profile.identidade.nome}
                      onChange={(e) => handleFieldChange('identidade', 'nome', e.target.value)}
                      className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">Gênero</label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'genero', profile))}
                    </div>
                    <select
                      value={profile.identidade.genero}
                      onChange={(e) => handleFieldChange('identidade', 'genero', e.target.value)}
                      className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    >
                      <option value="">Selecione...</option>
                      <option value="Masculino">Masculino</option>
                      <option value="Feminino">Feminino</option>
                      <option value="Outro">Outro</option>
                      <option value="Prefiro não informar">Prefiro não informar</option>
                    </select>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">Data de Nascimento</label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'data_nascimento', profile))}
                    </div>
                    <input
                      type="date"
                      value={profile.identidade.data_nascimento}
                      onChange={(e) => handleFieldChange('identidade', 'data_nascimento', e.target.value)}
                      className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">
                        Idade (Derivada Logicamente)
                      </label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'idade', profile))}
                    </div>
                    <div className="w-full text-xs p-2.5 bg-slate-950/60 border border-slate-800 rounded-xl text-emerald-400 font-mono flex items-center justify-between">
                      <span>
                        {ProfileService.calculateAge(profile.identidade.data_nascimento) !== null
                          ? `${ProfileService.calculateAge(profile.identidade.data_nascimento)} anos`
                          : 'Preencha a data de nascimento'}
                      </span>
                      <span className="text-[10px] text-slate-500">Cálculo exato</span>
                    </div>
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">Cidade</label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'cidade', profile))}
                    </div>
                    <input
                      type="text"
                      value={profile.identidade.cidade}
                      onChange={(e) => handleFieldChange('identidade', 'cidade', e.target.value)}
                      className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>

                  <div className="space-y-1.5">
                    <div className="flex justify-between items-center">
                      <label className="text-xs font-semibold text-slate-300">Estado (UF)</label>
                      {renderStatusBadge(ProfileService.getFieldStatus('identidade', 'estado', profile))}
                    </div>
                    <input
                      type="text"
                      value={profile.identidade.estado}
                      onChange={(e) => handleFieldChange('identidade', 'estado', e.target.value)}
                      className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    />
                  </div>
                </div>
              </>
            )}

            {activeCategory === 'familia' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Estado Civil</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('familia', 'estado_civil', profile))}
                  </div>
                  <select
                    value={profile.familia.estado_civil}
                    onChange={(e) => handleFieldChange('familia', 'estado_civil', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="">Selecione...</option>
                    <option value="Solteiro">Solteiro(a)</option>
                    <option value="Casado">Casado(a) / União Estável</option>
                    <option value="Divorciado">Divorciado(a)</option>
                    <option value="Viúvo">Viúvo(a)</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Possui Filhos?</label>
                    {renderStatusBadge(
                      profile.familia.tem_filhos !== null ? 'INFORMADO' : 'NAO_CONFIGURADO'
                    )}
                  </div>
                  <select
                    value={profile.familia.tem_filhos === null ? '' : profile.familia.tem_filhos ? 'true' : 'false'}
                    onChange={(e) => {
                      const val = e.target.value === '' ? null : e.target.value === 'true';
                      handleFieldChange('familia', 'tem_filhos', val);
                    }}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="">Selecione...</option>
                    <option value="false">Não possui filhos</option>
                    <option value="true">Sim, possui filhos</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Quantidade de Filhos</label>
                    {renderStatusBadge(
                      ProfileService.getFieldStatus('familia', 'quantidade_filhos', profile)
                    )}
                  </div>
                  <div className="w-full text-xs p-2.5 bg-slate-950/60 border border-slate-800 rounded-xl text-emerald-400 font-mono">
                    {profile.familia.tem_filhos === false
                      ? '0 (Derivado logicamente de tem_filhos = false)'
                      : profile.familia.quantidade_filhos ?? 'Não configurado'}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Moradores na Residência</label>
                    {renderStatusBadge(
                      ProfileService.getFieldStatus('familia', 'moradores_residencia', profile)
                    )}
                  </div>
                  <input
                    type="number"
                    value={profile.familia.moradores_residencia ?? ''}
                    onChange={(e) =>
                      handleFieldChange(
                        'familia',
                        'moradores_residencia',
                        e.target.value ? parseInt(e.target.value, 10) : null
                      )
                    }
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>
              </div>
            )}

            {activeCategory === 'trabalho' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Status de Emprego</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('trabalho', 'status_emprego', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.trabalho.status_emprego}
                    onChange={(e) => handleFieldChange('trabalho', 'status_emprego', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Profissão Principal</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('trabalho', 'profissao', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.trabalho.profissao}
                    onChange={(e) => handleFieldChange('trabalho', 'profissao', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Área de Atuação</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('trabalho', 'area_profissional', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.trabalho.area_profissional}
                    onChange={(e) => handleFieldChange('trabalho', 'area_profissional', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Modelo de Trabalho</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('trabalho', 'trabalho_remoto', profile))}
                  </div>
                  <select
                    value={profile.trabalho.trabalho_remoto}
                    onChange={(e) => handleFieldChange('trabalho', 'trabalho_remoto', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="Remoto">Remoto</option>
                    <option value="Híbrido">Híbrido</option>
                    <option value="Presencial">Presencial</option>
                  </select>
                </div>
              </div>
            )}

            {activeCategory === 'financas' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Faixa de Renda Pessoal</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('financas', 'faixa_renda_pessoal', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.financas.faixa_renda_pessoal}
                    onChange={(e) => handleFieldChange('financas', 'faixa_renda_pessoal', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">
                      Renda Mensal Exata (R$)
                    </label>
                    {renderStatusBadge(
                      profile.financas.renda_mensal_valor !== null ? 'INFORMADO' : 'NAO_CONFIGURADO'
                    )}
                  </div>
                  <input
                    type="number"
                    placeholder="Ex: 9500 (Deixado vazio para testar intervenção)"
                    value={profile.financas.renda_mensal_valor ?? ''}
                    onChange={(e) =>
                      handleFieldChange(
                        'financas',
                        'renda_mensal_valor',
                        e.target.value ? parseFloat(e.target.value) : null
                      )
                    }
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Possui Cartão de Crédito</label>
                    {renderStatusBadge(
                      profile.financas.possui_cartao_credito !== null ? 'INFORMADO' : 'NAO_CONFIGURADO'
                    )}
                  </div>
                  <select
                    value={profile.financas.possui_cartao_credito ? 'true' : 'false'}
                    onChange={(e) => handleFieldChange('financas', 'possui_cartao_credito', e.target.value === 'true')}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  >
                    <option value="true">Sim</option>
                    <option value="false">Não</option>
                  </select>
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Bancos Utilizados</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('financas', 'bancos', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.financas.bancos.join(', ')}
                    onChange={(e) =>
                      handleFieldChange(
                        'financas',
                        'bancos',
                        e.target.value.split(',').map((s) => s.trim()).filter(Boolean)
                      )
                    }
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>
              </div>
            )}

            {activeCategory === 'tecnologia' && (
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Smartphone Principal</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('tecnologia', 'celular_principal', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.tecnologia.celular_principal}
                    onChange={(e) => handleFieldChange('tecnologia', 'celular_principal', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Sistema Operacional</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('tecnologia', 'sistema_operacional', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.tecnologia.sistema_operacional}
                    onChange={(e) => handleFieldChange('tecnologia', 'sistema_operacional', e.target.value)}
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                </div>
              </div>
            )}

            {activeCategory === 'entretenimento' && (
              <div className="space-y-4">
                <div className="space-y-1.5">
                  <div className="flex justify-between items-center">
                    <label className="text-xs font-semibold text-slate-300">Serviços de Streaming</label>
                    {renderStatusBadge(ProfileService.getFieldStatus('entretenimento', 'servicos_streaming', profile))}
                  </div>
                  <input
                    type="text"
                    value={profile.entretenimento.servicos_streaming.join(', ')}
                    onChange={(e) =>
                      handleFieldChange(
                        'entretenimento',
                        'servicos_streaming',
                        e.target.value.split(',').map((s) => s.trim()).filter(Boolean)
                      )
                    }
                    className="w-full text-xs p-2.5 bg-slate-950 border border-slate-800 rounded-xl text-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
                  />
                  <p className="text-[11px] text-slate-500">Separe os serviços por vírgula</p>
                </div>
              </div>
            )}

            {activeCategory === 'personalizados' && (
              <div className="space-y-4">
                <div className="bg-slate-950/60 border border-slate-800 rounded-xl p-3.5 space-y-3">
                  <span className="text-xs font-bold text-slate-200">Adicionar Novo Campo Personalizado</span>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <input
                      type="text"
                      placeholder="Nome do campo (ex: clube_futebol)"
                      value={newCustomKey}
                      onChange={(e) => setNewCustomKey(e.target.value)}
                      className="text-xs p-2 bg-slate-900 border border-slate-700 rounded-lg text-white"
                    />
                    <input
                      type="text"
                      placeholder="Valor (ex: São Paulo FC)"
                      value={newCustomValue}
                      onChange={(e) => setNewCustomValue(e.target.value)}
                      className="text-xs p-2 bg-slate-900 border border-slate-700 rounded-lg text-white"
                    />
                  </div>
                  <button
                    onClick={handleAddCustomField}
                    className="py-1.5 px-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs rounded-lg flex items-center space-x-1.5 transition-colors"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Salvar Campo</span>
                  </button>
                </div>

                <div className="space-y-2">
                  <span className="text-xs font-semibold text-slate-300">Campos Customizados Salvos:</span>
                  {Object.entries(profile.campos_personalizados).length === 0 ? (
                    <p className="text-xs text-slate-500 italic">Nenhum campo personalizado cadastrado.</p>
                  ) : (
                    <div className="space-y-2">
                      {Object.entries(profile.campos_personalizados).map(([key, val]) => (
                        <div
                          key={key}
                          className="flex items-center justify-between p-2.5 bg-slate-950 border border-slate-800 rounded-xl"
                        >
                          <div>
                            <span className="text-xs font-mono text-emerald-400 font-semibold">{key}:</span>
                            <span className="text-xs text-slate-200 ml-2">{String(val)}</span>
                          </div>
                          <button
                            onClick={() => handleRemoveCustomField(key)}
                            className="text-slate-500 hover:text-rose-400 p-1"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {/* Other categories fallback generic message */}
            {!['identidade', 'familia', 'trabalho', 'financas', 'tecnologia', 'entretenimento', 'personalizados'].includes(
              activeCategory
            ) && (
              <div className="py-6 text-center space-y-2">
                <p className="text-xs text-slate-400">
                  Esta categoria ({activeCategory}) está pronta e conectada ao motor semântico do agente.
                </p>
                <button
                  onClick={onOpenJsonEditor}
                  className="text-xs text-emerald-400 hover:underline font-semibold"
                >
                  Abrir editor JSON completo para modificar todos os campos
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
