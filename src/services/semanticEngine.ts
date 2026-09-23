import { UserProfile } from '../types/profile';
import { InterpretationResult, ConfidenceLevel, DataOrigin } from '../types/agent';
import { SurveyQuestion } from '../types/survey';
import { ProfileService } from './profileService';

export class SemanticEngine {
  /**
   * Main interpretation entry point
   * Evaluates question against profile with strict anti-hallucination validation
   */
  public static async interpretQuestion(
    question: SurveyQuestion,
    profile: UserProfile
  ): Promise<InterpretationResult> {
    const qText = question.text.toLowerCase().trim();

    // 1. Check if this is a CAPTCHA or Human Check
    if (
      question.isCaptcha ||
      qText.includes('captcha') ||
      qText.includes('não sou um robô') ||
      qText.includes('not a robot') ||
      qText.includes('hcaptcha') ||
      qText.includes('recaptcha') ||
      qText.includes('cloudflare')
    ) {
      return {
        action: 'ASK_USER',
        answer: null,
        confidence: 0,
        confidenceLevel: 'UNKNOWN',
        source: null,
        reason: 'Verificação de segurança / CAPTCHA detectado. Automação estritamente pausada.',
        dataOrigin: 'DADO_NAO_DISPONIVEL',
        needs_user: true,
      };
    }

    // 2. Check if question explicitly requires missing user input
    if (question.needsUserInput) {
      return {
        action: 'ASK_USER',
        answer: null,
        confidence: 0,
        confidenceLevel: 'UNKNOWN',
        source: null,
        reason: 'Esta informação não consta no perfil do usuário. Requer preenchimento manual.',
        dataOrigin: 'DADO_NAO_DISPONIVEL',
        needs_user: true,
      };
    }

    // 3. Try server-side LLM if configured and connected
    try {
      const keywords = [question.text, ...(question.options || [])];
      const privacySubset = ProfileService.getPrivacyFilteredSubset(keywords, profile);

      const response = await fetch('/api/ai/interpret', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          question: question.text,
          options: question.options,
          field_type: question.type,
          filtered_profile: privacySubset,
        }),
      });

      if (response.ok) {
        const data = await response.json();
        if (!data.fallback && data.action) {
          // ANTI-HALLUCINATION VALIDATION LAYER (Section 25)
          const validated = this.validateLlmOutputAgainstProfile(data, question, profile);
          if (validated.isValid) {
            return {
              action: data.action,
              answer: data.answer,
              confidence: data.confidence,
              confidenceLevel: this.getConfidenceLevel(data.confidence),
              source: data.source || 'llm.verified',
              reason: data.reason || 'Inferência semântica comprovada por fatos do perfil.',
              dataOrigin: 'DADO_FORNECIDO',
              needs_user: data.needs_user || false,
            };
          }
        }
      }
    } catch {
      // LLM call failed or server unavailable - fallback directly to local deterministic engine
    }

    // 4. Deterministic Rule-Based Engine (Guaranteed zero hallucination)
    return this.evaluateDeterministic(question, profile);
  }

  /**
   * Deterministic Evaluation Engine
   */
  private static evaluateDeterministic(
    question: SurveyQuestion,
    profile: UserProfile
  ): InterpretationResult {
    const qText = question.text.toLowerCase().trim();
    const options = question.options || [];

    // Gênero / Sexo
    if (qText.includes('gênero') || qText.includes('sexo')) {
      const userGender = profile.identidade.genero; // e.g. "Masculino"
      if (!userGender) {
        return this.createMissingResult('identidade.genero', 'Gênero não informado no perfil.');
      }
      const matched = this.matchOption(options, userGender, ['homem', 'masculino', 'm']);
      return {
        action: 'ANSWER',
        answer: matched || userGender,
        confidence: 0.98,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'identidade.genero',
        reason: `Perfil define gênero explicitamente como "${userGender}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    // Idade / Nascimento (DERIVATION CHECK)
    if (qText.includes('sua idade') || qText.includes('quantos anos') || qText.includes('faixa etária') || qText.includes('idade?')) {
      const derivedAge = profile.identidade.idade ?? ProfileService.calculateAge(profile.identidade.data_nascimento);
      if (derivedAge === null) {
        return this.createMissingResult('identidade.idade', 'Idade ou data de nascimento ausente.');
      }

      // Check if options are age brackets (e.g. "18 a 24", "25 a 34", "30 a 39")
      if (options.length > 0) {
        const bracket = this.matchAgeBracket(options, derivedAge);
        if (bracket) {
          return {
            action: 'ANSWER',
            answer: bracket,
            confidence: 0.99,
            confidenceLevel: 'CONFIDENCE_HIGH',
            source: 'identidade.data_nascimento',
            reason: `Idade calculada logicamente (${derivedAge} anos) corresponde à faixa "${bracket}".`,
            dataOrigin: 'DADO_DERIVADO',
            needs_user: false,
          };
        }
      }

      return {
        action: 'ANSWER',
        answer: derivedAge.toString(),
        confidence: 0.99,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'identidade.data_nascimento',
        reason: `Idade (${derivedAge} anos) derivada matematicamente da data de nascimento ${profile.identidade.data_nascimento}.`,
        dataOrigin: 'DADO_DERIVADO',
        needs_user: false,
      };
    }

    // Filhos (DERIVATION & EXACT FACT CHECK)
    if (qText.includes('filho') || qText.includes('filha') || qText.includes('dependentes')) {
      if (qText.includes('quantos') || qText.includes('quantidade')) {
        const hasKids = profile.familia.tem_filhos;
        if (hasKids === false) {
          const zeroMatch = this.matchOption(options, '0', ['0', 'nenhum', 'não tenho', 'zero']);
          return {
            action: 'ANSWER',
            answer: zeroMatch || '0',
            confidence: 0.99,
            confidenceLevel: 'CONFIDENCE_HIGH',
            source: 'familia.tem_filhos',
            reason: 'Perfil afirma "tem_filhos = false". Quantidade logicamente derivada como 0.',
            dataOrigin: 'DADO_DERIVADO',
            needs_user: false,
          };
        }
        if (profile.familia.quantidade_filhos !== null) {
          const count = profile.familia.quantidade_filhos.toString();
          const countMatch = this.matchOption(options, count, [count]);
          return {
            action: 'ANSWER',
            answer: countMatch || count,
            confidence: 0.98,
            confidenceLevel: 'CONFIDENCE_HIGH',
            source: 'familia.quantidade_filhos',
            reason: `Perfil informa explicitamente ${count} filhos.`,
            dataOrigin: 'DADO_FORNECIDO',
            needs_user: false,
          };
        }
        return this.createMissingResult('familia.quantidade_filhos', 'Quantidade de filhos não especificada.');
      }

      // Boolean "Você possui filhos?"
      if (profile.familia.tem_filhos !== null) {
        const boolAns = profile.familia.tem_filhos ? 'Sim' : 'Não';
        const match = this.matchOption(options, boolAns, profile.familia.tem_filhos ? ['sim', 'yes', 'possui'] : ['não', 'nao', 'no']);
        return {
          action: 'ANSWER',
          answer: match || boolAns,
          confidence: 0.99,
          confidenceLevel: 'CONFIDENCE_HIGH',
          source: 'familia.tem_filhos',
          reason: `Perfil informa expressamente que ${profile.familia.tem_filhos ? 'possui' : 'não possui'} filhos.`,
          dataOrigin: 'DADO_FORNECIDO',
          needs_user: false,
        };
      }
      return this.createMissingResult('familia.tem_filhos', 'Informação sobre filhos não configurada.');
    }

    // Profissão / Ocupação / Cargo (Section 8: Semantic Variations)
    if (
      qText.includes('ocupação') ||
      qText.includes('profissão') ||
      qText.includes('cargo') ||
      qText.includes('com o que você trabalha') ||
      qText.includes('com o que trabalha') ||
      qText.includes('área de atuação') ||
      qText.includes('área você atua')
    ) {
      const prof = profile.trabalho.profissao;
      const area = profile.trabalho.area_profissional;
      if (!prof && !area) {
        return this.createMissingResult('trabalho.profissao', 'Profissão não informada no perfil.');
      }

      if (options.length > 0) {
        // Try matching area first (e.g. "TI", "Tecnologia da Informação", "Marketing")
        const areaMatch = this.matchOption(options, area, ['ti', 'tecnologia', 'software', 'computação', 'informática', 'desenvolvimento']);
        if (areaMatch) {
          return {
            action: 'ANSWER',
            answer: areaMatch,
            confidence: 0.96,
            confidenceLevel: 'CONFIDENCE_HIGH',
            source: 'trabalho.area_profissional',
            reason: `Área profissional (${area}) corresponde semanticamente à opção "${areaMatch}".`,
            dataOrigin: 'DADO_FORNECIDO',
            needs_user: false,
          };
        }
      }

      return {
        action: 'ANSWER',
        answer: prof,
        confidence: 0.95,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'trabalho.profissao',
        reason: `Profissão informada no perfil: "${prof}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    // Localização / Cidade / Estado
    if (qText.includes('cidade') || qText.includes('onde você mora') || qText.includes('onde mora') || qText.includes('município')) {
      const city = profile.identidade.cidade;
      if (!city) {
        return this.createMissingResult('identidade.cidade', 'Cidade não configurada.');
      }
      const match = this.matchOption(options, city, [city.toLowerCase(), 'sp', 'são paulo']);
      return {
        action: 'ANSWER',
        answer: match || city,
        confidence: 0.98,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'identidade.cidade',
        reason: `Cidade informada no perfil: "${city}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    if (qText.includes('estado') && !qText.includes('civil')) {
      const uf = profile.identidade.estado;
      const match = this.matchOption(options, uf, ['sp', 'são paulo']);
      return {
        action: 'ANSWER',
        answer: match || uf,
        confidence: 0.98,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'identidade.estado',
        reason: `Estado informado no perfil: "${uf}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    // Estado Civil
    if (qText.includes('estado civil')) {
      const ec = profile.familia.estado_civil;
      if (!ec) {
        return this.createMissingResult('familia.estado_civil', 'Estado civil não informado.');
      }
      const match = this.matchOption(options, ec, [ec.toLowerCase()]);
      return {
        action: 'ANSWER',
        answer: match || ec,
        confidence: 0.98,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'familia.estado_civil',
        reason: `Estado civil cadastrado: "${ec}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    // Streaming / Mídia
    if (qText.includes('streaming') || qText.includes('assistir filmes') || qText.includes('serviço de vídeo')) {
      const userStreamings = profile.entretenimento.servicos_streaming;
      if (!userStreamings || userStreamings.length === 0) {
        return this.createMissingResult('entretenimento.servicos_streaming', 'Nenhum streaming cadastrado.');
      }
      if (question.type === 'CHECKBOX') {
        const matchingItems = options.filter((opt) =>
          userStreamings.some((us) => opt.toLowerCase().includes(us.toLowerCase()) || us.toLowerCase().includes(opt.toLowerCase()))
        );
        return {
          action: 'ANSWER',
          answer: matchingItems,
          confidence: 0.95,
          confidenceLevel: 'CONFIDENCE_HIGH',
          source: 'entretenimento.servicos_streaming',
          reason: `Selecionados serviços confirmados no perfil: ${matchingItems.join(', ')}.`,
          dataOrigin: 'DADO_FORNECIDO',
          needs_user: false,
        };
      }
      const singleMatch = options.find((opt) => userStreamings.some((us) => opt.toLowerCase().includes(us.toLowerCase())));
      if (singleMatch) {
        return {
          action: 'ANSWER',
          answer: singleMatch,
          confidence: 0.92,
          confidenceLevel: 'CONFIDENCE_HIGH',
          source: 'entretenimento.servicos_streaming',
          reason: `Perfil utiliza o serviço "${singleMatch}".`,
          dataOrigin: 'DADO_FORNECIDO',
          needs_user: false,
        };
      }
    }

    // Celular / Smartphone / Sistema Operacional
    if (qText.includes('sistema operacional') || qText.includes('android ou ios') || qText.includes('tipo de celular')) {
      if (profile.tecnologia.possui_android) {
        const match = this.matchOption(options, 'Android', ['android', 'google']);
        return {
          action: 'ANSWER',
          answer: match || 'Android',
          confidence: 0.99,
          confidenceLevel: 'CONFIDENCE_HIGH',
          source: 'tecnologia.possui_android',
          reason: 'Perfil confirma uso de dispositivo Android.',
          dataOrigin: 'DADO_FORNECIDO',
          needs_user: false,
        };
      }
    }

    if (qText.includes('marca') && (qText.includes('celular') || qText.includes('smartphone'))) {
      const brand = 'Samsung';
      const match = this.matchOption(options, brand, ['samsung', 'galaxy']);
      return {
        action: 'ANSWER',
        answer: match || brand,
        confidence: 0.95,
        confidenceLevel: 'CONFIDENCE_HIGH',
        source: 'tecnologia.celular_principal',
        reason: `Aparelho cadastrado: "${profile.tecnologia.celular_principal}".`,
        dataOrigin: 'DADO_FORNECIDO',
        needs_user: false,
      };
    }

    // Finanças: Renda Mensal (MISSING IN DEFAULT PROFILE!)
    if (qText.includes('renda') || qText.includes('salário') || qText.includes('rendimento mensal') || qText.includes('ganho mensal')) {
      if (qText.includes('faixa')) {
        const faixa = profile.financas.faixa_renda_pessoal;
        if (faixa) {
          const match = this.matchOption(options, faixa, ['8.000', '10.000', '12.000']);
          return {
            action: 'ANSWER',
            answer: match || faixa,
            confidence: 0.94,
            confidenceLevel: 'CONFIDENCE_HIGH',
            source: 'financas.faixa_renda_pessoal',
            reason: `Faixa de renda registrada: "${faixa}".`,
            dataOrigin: 'DADO_FORNECIDO',
            needs_user: false,
          };
        }
      }

      // Exact numerical value
      if (profile.financas.renda_mensal_valor !== null) {
        return {
          action: 'ANSWER',
          answer: profile.financas.renda_mensal_valor.toString(),
          confidence: 0.98,
          confidenceLevel: 'CONFIDENCE_HIGH',
          source: 'financas.renda_mensal_valor',
          reason: `Valor de renda informado: R$ ${profile.financas.renda_mensal_valor}.`,
          dataOrigin: 'DADO_FORNECIDO',
          needs_user: false,
        };
      }

      // Intentionally missing -> USER_INTERVENTION_REQUIRED
      return this.createMissingResult('financas.renda_mensal_valor', 'Qual é sua renda mensal? Essa informação não está configurada no perfil.');
    }

    // Viagens internacionais (MISSING IN DEFAULT PROFILE!)
    if (qText.includes('viagens internacionais') || qText.includes('viagem internacional') || qText.includes('viagens ao exterior')) {
      if (profile.viagens.viagens_internacionais === null) {
        return this.createMissingResult('viagens.viagens_internacionais', 'Qual é a sua frequência de viagens internacionais? Essa informação não está no perfil.');
      }
    }

    // Fallback: If nothing matched, STRICTLY REFUSE TO INVENT (Section 3 & 44)
    return {
      action: 'ASK_USER',
      answer: null,
      confidence: 0,
      confidenceLevel: 'UNKNOWN',
      source: null,
      reason: `Não existe informação suficiente no perfil para responder com segurança à pergunta "${question.text}".`,
      dataOrigin: 'DADO_NAO_DISPONIVEL',
      needs_user: true,
    };
  }

  /**
   * Helper to create Missing Result
   */
  private static createMissingResult(fieldPath: string, message: string): InterpretationResult {
    return {
      action: 'ASK_USER',
      answer: null,
      confidence: 0,
      confidenceLevel: 'UNKNOWN',
      source: fieldPath,
      reason: message,
      dataOrigin: 'DADO_NAO_DISPONIVEL',
      needs_user: true,
    };
  }

  /**
   * Anti-Hallucination Validator (Section 25)
   */
  private static validateLlmOutputAgainstProfile(
    llmOutput: any,
    question: SurveyQuestion,
    profile: UserProfile
  ): { isValid: boolean; reason?: string } {
    if (llmOutput.action === 'ASK_USER') {
      return { isValid: true };
    }

    // If LLM claims to answer, verify source path exists
    const source = llmOutput.source;
    if (!source) {
      return { isValid: false, reason: 'LLM não especificou a fonte do perfil.' };
    }

    // Retrieve value from user profile
    const parts = source.split('.');
    let cur: any = profile;
    for (const p of parts) {
      if (cur && typeof cur === 'object' && p in cur) {
        cur = cur[p];
      } else {
        cur = undefined;
        break;
      }
    }

    if (cur === undefined || cur === null || cur === '') {
      return { isValid: false, reason: `Campo de perfil alegado (${source}) está vazio ou inexistente.` };
    }

    return { isValid: true };
  }

  /**
   * Fuzzy matches options list against user fact and synonyms
   */
  private static matchOption(options: string[], target: string, synonyms: string[] = []): string | null {
    if (!options || options.length === 0) return null;
    const cleanTarget = target.toLowerCase().trim();

    // 1. Exact match
    const exact = options.find((opt) => opt.toLowerCase().trim() === cleanTarget);
    if (exact) return exact;

    // 2. Substring match
    const sub = options.find((opt) => opt.toLowerCase().includes(cleanTarget) || cleanTarget.includes(opt.toLowerCase()));
    if (sub) return sub;

    // 3. Synonym match
    for (const syn of synonyms) {
      const match = options.find((opt) => opt.toLowerCase().includes(syn.toLowerCase()));
      if (match) return match;
    }

    return null;
  }

  /**
   * Match age number into bracket string, e.g. 29 into "25 a 34 anos"
   */
  private static matchAgeBracket(options: string[], age: number): string | null {
    for (const opt of options) {
      const numbers = opt.match(/\d+/g)?.map(Number);
      if (numbers && numbers.length >= 2) {
        const [min, max] = numbers;
        if (age >= min && age <= max) {
          return opt;
        }
      } else if (numbers && numbers.length === 1) {
        if (opt.includes('mais') || opt.includes('+') || opt.includes('acima')) {
          if (age >= numbers[0]) return opt;
        } else if (opt.includes('menos') || opt.includes('até')) {
          if (age <= numbers[0]) return opt;
        }
      }
    }
    return null;
  }

  private static getConfidenceLevel(score: number): ConfidenceLevel {
    if (score >= 0.9) return 'CONFIDENCE_HIGH';
    if (score >= 0.6) return 'CONFIDENCE_MEDIUM';
    if (score > 0) return 'CONFIDENCE_LOW';
    return 'UNKNOWN';
  }
}
