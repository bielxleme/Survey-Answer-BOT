import { UserProfile, FieldStatus } from '../types/profile';
import { INITIAL_USER_PROFILE } from '../data/defaultProfile';

const PROFILE_STORAGE_KEY = 'research_agent_user_profile_v1';
const LEARNED_MAPPINGS_KEY = 'research_agent_learned_mappings_v1';

export class ProfileService {
  /**
   * Load profile from persistent storage or default
   */
  public static loadProfile(): UserProfile {
    try {
      const data = localStorage.getItem(PROFILE_STORAGE_KEY);
      if (data) {
        const parsed = JSON.parse(data);
        return {
          ...INITIAL_USER_PROFILE,
          ...parsed,
          identidade: { ...INITIAL_USER_PROFILE.identidade, ...parsed.identidade },
          familia: { ...INITIAL_USER_PROFILE.familia, ...parsed.familia },
          educacao: { ...INITIAL_USER_PROFILE.educacao, ...parsed.educacao },
          trabalho: { ...INITIAL_USER_PROFILE.trabalho, ...parsed.trabalho },
          financas: { ...INITIAL_USER_PROFILE.financas, ...parsed.financas },
          tecnologia: { ...INITIAL_USER_PROFILE.tecnologia, ...parsed.tecnologia },
          consumo: { ...INITIAL_USER_PROFILE.consumo, ...parsed.consumo },
          entretenimento: { ...INITIAL_USER_PROFILE.entretenimento, ...parsed.entretenimento },
          transporte: { ...INITIAL_USER_PROFILE.transporte, ...parsed.transporte },
          viagens: { ...INITIAL_USER_PROFILE.viagens, ...parsed.viagens },
          alimentacao: { ...INITIAL_USER_PROFILE.alimentacao, ...parsed.alimentacao },
          animais: { ...INITIAL_USER_PROFILE.animais, ...parsed.animais },
          casa: { ...INITIAL_USER_PROFILE.casa, ...parsed.casa },
        };
      }
    } catch (e) {
      console.error('Failed to load profile from storage:', e);
    }
    return INITIAL_USER_PROFILE;
  }

  /**
   * Save profile to storage (Simulates Android Keystore encrypted storage)
   */
  public static saveProfile(profile: UserProfile): void {
    try {
      localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(profile));
    } catch (e) {
      console.error('Failed to save profile to storage:', e);
    }
  }

  /**
   * Reset profile to defaults
   */
  public static resetProfile(): UserProfile {
    localStorage.removeItem(PROFILE_STORAGE_KEY);
    return INITIAL_USER_PROFILE;
  }

  /**
   * Calculate derived age from birthdate
   */
  public static calculateAge(birthDateStr: string | null | undefined): number | null {
    if (!birthDateStr) return null;
    const parts = birthDateStr.split('-');
    if (parts.length !== 3) return null;
    const birth = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
    const today = new Date();
    let age = today.getFullYear() - birth.getFullYear();
    const m = today.getMonth() - birth.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
      age--;
    }
    return age > 0 && age < 120 ? age : null;
  }

  /**
   * Evaluates field status for UI badges:
   * ✓ INFORMADO
   * ? DERIVAVEL
   * ! NECESSARIO
   * — NAO_CONFIGURADO
   */
  public static getFieldStatus(
    categoryKey: keyof UserProfile,
    fieldKey: string,
    profile: UserProfile,
    isMarkedAsNeeded: boolean = false
  ): FieldStatus {
    if (isMarkedAsNeeded) {
      return 'NECESSARIO';
    }

    // Special Derivations (Section 10)
    if (categoryKey === 'identidade' && fieldKey === 'idade') {
      if (profile.identidade.idade !== null && profile.identidade.idade !== undefined) {
        return 'INFORMADO';
      }
      if (profile.identidade.data_nascimento) {
        return 'DERIVAVEL';
      }
      return 'NAO_CONFIGURADO';
    }

    if (categoryKey === 'familia' && fieldKey === 'quantidade_filhos') {
      if (profile.familia.quantidade_filhos !== null && profile.familia.quantidade_filhos !== undefined) {
        return 'INFORMADO';
      }
      if (profile.familia.tem_filhos === false) {
        return 'DERIVAVEL'; // Mathematically 0
      }
      return 'NAO_CONFIGURADO';
    }

    const cat = profile[categoryKey] as any;
    if (!cat) return 'NAO_CONFIGURADO';

    const val = cat[fieldKey];
    if (val === null || val === undefined || val === '') {
      return 'NAO_CONFIGURADO';
    }
    if (Array.isArray(val) && val.length === 0) {
      return 'NAO_CONFIGURADO';
    }
    return 'INFORMADO';
  }

  /**
   * Derive values when requested
   */
  public static getDerivedValue(path: string, profile: UserProfile): any {
    if (path === 'identidade.idade') {
      if (profile.identidade.idade !== null) return profile.identidade.idade;
      return this.calculateAge(profile.identidade.data_nascimento);
    }
    if (path === 'familia.quantidade_filhos') {
      if (profile.familia.quantidade_filhos !== null) return profile.familia.quantidade_filhos;
      if (profile.familia.tem_filhos === false) return 0;
    }
    return null;
  }

  /**
   * Privacy Sandbox (Section 21):
   * Extract ONLY relevant fields for a given question before sending to AI or parsing
   */
  public static getPrivacyFilteredSubset(keywords: string[], profile: UserProfile): Record<string, any> {
    const subset: Record<string, any> = {};
    const textToMatch = keywords.join(' ').toLowerCase();

    if (textToMatch.includes('idade') || textToMatch.includes('nascimento') || textToMatch.includes('ano')) {
      subset.identidade = {
        data_nascimento: profile.identidade.data_nascimento,
        idade: profile.identidade.idade ?? this.calculateAge(profile.identidade.data_nascimento),
      };
    }
    if (textToMatch.includes('gênero') || textToMatch.includes('sexo')) {
      subset.identidade = { ...(subset.identidade || {}), genero: profile.identidade.genero };
    }
    if (textToMatch.includes('cidade') || textToMatch.includes('estado') || textToMatch.includes('onde mora') || textToMatch.includes('reside')) {
      subset.identidade = {
        ...(subset.identidade || {}),
        cidade: profile.identidade.cidade,
        estado: profile.identidade.estado,
        pais: profile.identidade.pais,
      };
    }
    if (textToMatch.includes('filho') || textToMatch.includes('criança') || textToMatch.includes('família')) {
      subset.familia = {
        tem_filhos: profile.familia.tem_filhos,
        quantidade_filhos: profile.familia.quantidade_filhos ?? (profile.familia.tem_filhos === false ? 0 : null),
        estado_civil: profile.familia.estado_civil,
      };
    }
    if (textToMatch.includes('trabalh') || textToMatch.includes('profiss') || textToMatch.includes('ocup') || textToMatch.includes('cargo') || textToMatch.includes('área')) {
      subset.trabalho = {
        status_emprego: profile.trabalho.status_emprego,
        profissao: profile.trabalho.profissao,
        cargo: profile.trabalho.cargo,
        area_profissional: profile.trabalho.area_profissional,
      };
    }
    if (textToMatch.includes('renda') || textToMatch.includes('salário') || textToMatch.includes('ganho') || textToMatch.includes('banco') || textToMatch.includes('cartão')) {
      subset.financas = {
        faixa_renda_pessoal: profile.financas.faixa_renda_pessoal,
        faixa_renda_familiar: profile.financas.faixa_renda_familiar,
        renda_mensal_valor: profile.financas.renda_mensal_valor,
        possui_cartao_credito: profile.financas.possui_cartao_credito,
        bancos: profile.financas.bancos,
      };
    }
    if (textToMatch.includes('celular') || textToMatch.includes('smartphone') || textToMatch.includes('sistema operacional') || textToMatch.includes('android') || textToMatch.includes('iphone')) {
      subset.tecnologia = {
        celular_principal: profile.tecnologia.celular_principal,
        sistema_operacional: profile.tecnologia.sistema_operacional,
        possui_android: profile.tecnologia.possui_android,
        possui_iphone: profile.tecnologia.possui_iphone,
      };
    }
    if (textToMatch.includes('streaming') || textToMatch.includes('música') || textToMatch.includes('netflix') || textToMatch.includes('spotify')) {
      subset.entretenimento = {
        servicos_streaming: profile.entretenimento.servicos_streaming,
      };
    }
    if (textToMatch.includes('carro') || textToMatch.includes('moto') || textToMatch.includes('veículo') || textToMatch.includes('transporte')) {
      subset.transporte = {
        possui_carro: profile.transporte.possui_carro,
        marcas_veiculos: profile.transporte.marcas_veiculos,
        possui_moto: profile.transporte.possui_moto,
      };
    }
    if (textToMatch.includes('animal') || textToMatch.includes('pet') || textToMatch.includes('cachorro') || textToMatch.includes('gato')) {
      subset.animais = {
        possui_animais: profile.animais.possui_animais,
        quantidade_animais: profile.animais.quantidade_animais,
        especies: profile.animais.especies,
      };
    }

    // Fallback if very broad or nothing matched: send minimal public non-sensitive subset
    if (Object.keys(subset).length === 0) {
      return {
        identidade: {
          genero: profile.identidade.genero,
          cidade: profile.identidade.cidade,
          pais: profile.identidade.pais,
        },
      };
    }

    return subset;
  }

  /**
   * Save learned mapping into memory (Section 27)
   */
  public static saveLearnedMapping(questionText: string, targetKey: string, answerValue: any) {
    try {
      const stored = localStorage.getItem(LEARNED_MAPPINGS_KEY);
      const list = stored ? JSON.parse(stored) : [];
      list.push({
        question: questionText,
        key: targetKey,
        value: answerValue,
        learnedAt: new Date().toISOString(),
      });
      localStorage.setItem(LEARNED_MAPPINGS_KEY, JSON.stringify(list));
    } catch {
      // Ignored
    }
  }

  public static getLearnedMappings(): Array<{ question: string; key: string; value: any; learnedAt: string }> {
    try {
      const stored = localStorage.getItem(LEARNED_MAPPINGS_KEY);
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }
}
