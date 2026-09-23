/**
 * User Profile types for Research Agent
 * Implements all 13 categories and status indicators
 */

export type FieldStatus = 'INFORMADO' | 'DERIVAVEL' | 'NECESSARIO' | 'NAO_CONFIGURADO';

export interface IdentidadeProfile {
  nome: string;
  nome_social: string;
  idade: number | null; // Pode ser derivado da data de nascimento
  data_nascimento: string; // YYYY-MM-DD
  genero: string;
  nacionalidade: string;
  cidade: string;
  estado: string;
  pais: string;
  cep: string;
  idiomas: string[];
}

export interface FamiliaProfile {
  estado_civil: string;
  tem_filhos: boolean | null;
  quantidade_filhos: number | null;
  idade_filhos: number[];
  moradores_residencia: number | null;
  dependentes: number | null;
}

export interface EducacaoProfile {
  escolaridade: string;
  curso: string;
  graduacao: string;
  pos_graduacao: string;
  area_formacao: string;
}

export interface TrabalhoProfile {
  status_emprego: string; // Empregado, Autônomo, Desempregado, etc.
  profissao: string;
  cargo: string;
  area_profissional: string;
  tipo_contrato: string; // CLT, PJ, etc.
  trabalho_remoto: string; // Remoto, Presencial, Híbrido
  empresa: string;
  tempo_empresa: string;
  experiencia_anos: number | null;
  tamanho_empresa: string;
}

export interface FinancasProfile {
  faixa_renda_pessoal: string;
  faixa_renda_familiar: string;
  renda_mensal_valor: number | null;
  possui_cartao_credito: boolean | null;
  formas_pagamento: string[];
  bancos: string[];
  usa_pix: boolean | null;
  usa_banco_digital: boolean | null;
  bancos_digitais: string[];
  possui_investimentos: boolean | null;
  tipos_investimentos: string[];
  possui_seguro: string[];
}

export interface TecnologiaProfile {
  celular_principal: string;
  sistema_operacional: string; // Android
  possui_android: boolean;
  possui_iphone: boolean;
  computadores: string[];
  marcas_dispositivos: string[];
  aplicativos_frequentes: string[];
  servicos_digitais: string[];
}

export interface ConsumoProfile {
  marcas_utilizadas: string[];
  produtos_frequentes: string[];
  compras_online: boolean | null;
  frequencia_compras_online: string;
  sites_compras: string[];
  categorias_compras: string[];
}

export interface EntretenimentoProfile {
  servicos_streaming: string[];
  generos_musica: string[];
  jogos: string[];
  redes_sociais: string[];
  frequencia_redes_sociais: Record<string, string>;
  canais_noticias: string[];
}

export interface TransporteProfile {
  possui_carro: boolean | null;
  marcas_veiculos: string[];
  possui_moto: boolean | null;
  usa_bicicleta: boolean | null;
  usa_transporte_publico: boolean | null;
  usa_apps_transporte: boolean | null;
  apps_transporte: string[];
  frequencia_transporte: string;
}

export interface ViagensProfile {
  frequencia_viagens: string;
  viagens_nacionais: boolean | null;
  viagens_internacionais: boolean | null;
  frequencia_viagens_internacionais: string;
  destinos_visitados: string[];
  companhias_aereas: string[];
  plataformas_reserva: string[];
}

export interface AlimentacaoProfile {
  habitos_alimentares: string;
  restricoes_alimentares: string[];
  frequencia_restaurantes: string;
  usa_delivery: boolean | null;
  frequencia_delivery: string;
  preferencias_culinarias: string[];
}

export interface AnimaisProfile {
  possui_animais: boolean | null;
  quantidade_animais: number | null;
  especies: string[];
  produtos_pet: string[];
}

export interface CasaProfile {
  tipo_moradia: string; // Casa, Apartamento
  propriedade_ou_aluguel: string; // Própria, Alugada
  quantidade_moradores: number | null;
  quantidade_quartos: number | null;
  eletrodomesticos: string[];
}

export interface UserProfile {
  identidade: IdentidadeProfile;
  familia: FamiliaProfile;
  educacao: EducacaoProfile;
  trabalho: TrabalhoProfile;
  financas: FinancasProfile;
  tecnologia: TecnologiaProfile;
  consumo: ConsumoProfile;
  entretenimento: EntretenimentoProfile;
  transporte: TransporteProfile;
  viagens: ViagensProfile;
  alimentacao: AlimentacaoProfile;
  animais: AnimaisProfile;
  casa: CasaProfile;
  campos_personalizados: Record<string, string | number | boolean>;
  observacoes: string;
}
