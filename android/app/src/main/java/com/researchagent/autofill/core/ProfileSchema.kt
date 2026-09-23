package com.researchagent.autofill.core

enum class FieldType { TEXT, NUMBER, BOOLEAN, DATE, CHOICE, LIST }

enum class Category(val label: String, val jsonName: String) {
    IDENTIDADE("Identidade", "identidade"),
    LOCALIZACAO("Localização", "localizacao"),
    FAMILIA("Família", "familia"),
    EDUCACAO("Educação", "educacao"),
    TRABALHO("Trabalho", "profissao"),
    FINANCAS("Finanças", "financas"),
    TECNOLOGIA("Tecnologia", "tecnologia"),
    CONSUMO("Consumo", "consumo"),
    ENTRETENIMENTO("Entretenimento", "entretenimento"),
    TRANSPORTE("Transporte", "transporte"),
    VIAGENS("Viagens", "viagens"),
    ALIMENTACAO("Alimentação", "alimentacao"),
    SAUDE("Saúde e hábitos", "saude"),
    ANIMAIS("Animais", "animais"),
    CASA("Casa", "casa"),
    PERSONALIZADO("Campos personalizados", "campos_personalizados")
}

/**
 * Definição de um campo do perfil.
 * @param keywords frases (sem acento) que indicam semanticamente o campo numa pergunta.
 * @param negative palavras que, se presentes, descartam o campo (ex.: "estado" x "estado civil").
 * @param sensitive nunca é enviado para LLM remoto e é mascarado nos logs.
 */
data class FieldDef(
    val key: String,
    val label: String,
    val category: Category,
    val type: FieldType,
    val keywords: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val negative: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val sensitive: Boolean = false,
    val wizardQuestion: String? = null
)

object ProfileSchema {

    val fields: List<FieldDef> = listOf(
        // ── Identidade ───────────────────────────────────────────────
        FieldDef("nome", "Nome", Category.IDENTIDADE, FieldType.TEXT,
            listOf("seu nome", "nome completo", "primeiro nome", "your name", "full name", "first name", "como se chama"),
            negative = listOf("empresa", "social", "mae", "pai", "filho", "usuario", "banco", "loja", "marca"),
            wizardQuestion = "Qual é o seu nome?"),
        FieldDef("sobrenome", "Sobrenome", Category.IDENTIDADE, FieldType.TEXT,
            listOf("sobrenome", "ultimo nome", "last name", "surname", "apellido")),
        FieldDef("nome_social", "Nome social", Category.IDENTIDADE, FieldType.TEXT,
            listOf("nome social", "como prefere ser chamado")),
        FieldDef("data_nascimento", "Data de nascimento", Category.IDENTIDADE, FieldType.DATE,
            listOf("data de nascimento", "nascimento", "nasceu", "date of birth", "birth date", "birthday",
                "ano de nascimento", "year of birth", "fecha de nacimiento"),
            wizardQuestion = "Qual é a sua data de nascimento? (dd/mm/aaaa)"),
        FieldDef("idade", "Idade", Category.IDENTIDADE, FieldType.NUMBER,
            listOf("idade", "quantos anos voce tem", "faixa etaria", "your age", "how old", "age", "edad", "age group", "age range"),
            negative = listOf("filho", "crianca", "children", "carro", "empresa", "animal", "pet", "casa", "imovel")),
        FieldDef("genero", "Gênero", Category.IDENTIDADE, FieldType.CHOICE,
            listOf("genero", "sexo", "gender", "sex", "identidade de genero", "voce se identifica como"),
            options = listOf("Masculino", "Feminino", "Não-binário", "Prefiro não dizer", "Outro"),
            wizardQuestion = "Qual é o seu gênero?"),
        FieldDef("nacionalidade", "Nacionalidade", Category.IDENTIDADE, FieldType.TEXT,
            listOf("nacionalidade", "nationality", "nacionalidad")),
        FieldDef("idiomas", "Idiomas", Category.IDENTIDADE, FieldType.LIST,
            listOf("idioma", "idiomas", "lingua", "linguas", "language", "languages", "fala quais")),
        FieldDef("email", "E-mail", Category.IDENTIDADE, FieldType.TEXT,
            listOf("e mail", "email", "endereco de email", "correo"), sensitive = true),
        FieldDef("telefone", "Telefone", Category.IDENTIDADE, FieldType.TEXT,
            listOf("telefone", "numero de telefone", "numero de celular", "whatsapp", "phone number", "telefono"),
            sensitive = true),
        FieldDef("cpf", "CPF", Category.IDENTIDADE, FieldType.TEXT, listOf("cpf"), sensitive = true),

        // ── Localização ──────────────────────────────────────────────
        FieldDef("pais", "País", Category.LOCALIZACAO, FieldType.TEXT,
            listOf("pais", "country", "pais onde mora", "pais de residencia"),
            negative = listOf("visit", "viag", "destin", "nascimento", "exterior")),
        FieldDef("estado", "Estado (UF)", Category.LOCALIZACAO, FieldType.TEXT,
            listOf("estado", "uf", "state", "estado onde mora", "provincia"),
            negative = listOf("civil", "saude", "conservacao", "animo", "marital", "emocional"),
            wizardQuestion = "Em qual estado você mora?"),
        FieldDef("cidade", "Cidade", Category.LOCALIZACAO, FieldType.TEXT,
            listOf("cidade", "city", "municipio", "cidade onde mora", "ciudad"),
            negative = listOf("natal", "nasceu", "visit", "viag"),
            wizardQuestion = "Em qual cidade você mora?"),
        FieldDef("bairro", "Bairro", Category.LOCALIZACAO, FieldType.TEXT, listOf("bairro", "neighborhood", "barrio")),
        FieldDef("cep", "CEP", Category.LOCALIZACAO, FieldType.TEXT,
            listOf("cep", "codigo postal", "zip", "zip code", "postal code"), sensitive = true),
        FieldDef("area_residencia", "Área (urbana/rural)", Category.LOCALIZACAO, FieldType.CHOICE,
            listOf("zona urbana", "zona rural", "area urbana", "urban or rural"), options = listOf("Urbana", "Rural")),

        // ── Família ──────────────────────────────────────────────────
        FieldDef("estado_civil", "Estado civil", Category.FAMILIA, FieldType.CHOICE,
            listOf("estado civil", "marital status", "situacao conjugal", "voce e casado", "relacionamento"),
            options = listOf("Solteiro(a)", "Casado(a)", "União estável", "Divorciado(a)", "Separado(a)", "Viúvo(a)"),
            wizardQuestion = "Qual é o seu estado civil?"),
        FieldDef("tem_filhos", "Tem filhos", Category.FAMILIA, FieldType.BOOLEAN,
            listOf("filho", "filhos", "tem filhos", "possui filhos", "children", "kids", "have children", "hijos"),
            negative = listOf("quanto", "quantos", "idade", "how many", "ages", "age"),
            wizardQuestion = "Você tem filhos?"),
        FieldDef("quantidade_filhos", "Quantidade de filhos", Category.FAMILIA, FieldType.NUMBER,
            listOf("quantos filhos", "numero de filhos", "quantidade de filhos", "how many children", "cuantos hijos")),
        FieldDef("idade_filhos", "Idade dos filhos", Category.FAMILIA, FieldType.LIST,
            listOf("idade dos filhos", "idade do filho", "idade de seus filhos", "ages of your children", "age of your children")),
        FieldDef("moradores_residencia", "Moradores na residência", Category.FAMILIA, FieldType.NUMBER,
            listOf("quantas pessoas moram", "pessoas moram", "moradores", "pessoas na residencia", "pessoas residem",
                "household size", "people live in your household", "people in your household", "tamanho do domicilio"),
            wizardQuestion = "Quantas pessoas moram na sua casa (incluindo você)?"),
        FieldDef("dependentes", "Dependentes", Category.FAMILIA, FieldType.NUMBER,
            listOf("dependentes", "dependents")),
        FieldDef("chefe_familia", "Principal responsável pela renda", Category.FAMILIA, FieldType.BOOLEAN,
            listOf("chefe da familia", "chefe de familia", "principal responsavel pela renda", "head of household",
                "principal provedor")),

        // ── Educação ─────────────────────────────────────────────────
        FieldDef("escolaridade", "Escolaridade", Category.EDUCACAO, FieldType.CHOICE,
            listOf("escolaridade", "nivel de escolaridade", "grau de instrucao", "nivel de educacao", "nivel de ensino",
                "education level", "highest level of education", "nivel educativo"),
            options = listOf("Fundamental incompleto", "Fundamental completo", "Médio incompleto", "Médio completo",
                "Superior incompleto", "Superior completo", "Pós-graduação", "Mestrado", "Doutorado"),
            wizardQuestion = "Qual é a sua escolaridade?"),
        FieldDef("curso", "Curso / área de formação", Category.EDUCACAO, FieldType.TEXT,
            listOf("curso", "area de formacao", "formado em", "graduacao em", "field of study", "major", "formacao academica")),
        FieldDef("estudante", "Estuda atualmente", Category.EDUCACAO, FieldType.BOOLEAN,
            listOf("estuda atualmente", "e estudante", "voce estuda", "are you a student", "student")),

        // ── Trabalho ─────────────────────────────────────────────────
        FieldDef("situacao_emprego", "Situação profissional", Category.TRABALHO, FieldType.CHOICE,
            listOf("situacao profissional", "situacao de emprego", "situacao ocupacional", "employment status",
                "esta trabalhando", "trabalha atualmente", "esta empregado", "are you employed", "situacion laboral"),
            options = listOf("Empregado (CLT)", "Servidor público", "Autônomo", "Empresário", "Freelancer",
                "Desempregado", "Estudante", "Aposentado", "Do lar"),
            wizardQuestion = "Qual é a sua situação profissional?"),
        FieldDef("profissao", "Profissão / ocupação", Category.TRABALHO, FieldType.TEXT,
            listOf("profissao", "ocupacao", "com o que voce trabalha", "trabalha com", "occupation", "profession",
                "what do you do", "profesion", "ocupacion"),
            wizardQuestion = "Qual é a sua profissão?"),
        FieldDef("cargo", "Cargo", Category.TRABALHO, FieldType.TEXT,
            listOf("cargo", "job title", "funcao", "posicao na empresa", "nivel hierarquico", "puesto")),
        FieldDef("area_profissional", "Área de atuação", Category.TRABALHO, FieldType.TEXT,
            listOf("area de atuacao", "area atua", "em qual area", "setor de atuacao", "ramo de atividade", "segmento",
                "industry", "sector", "area profissional")),
        FieldDef("setor_empresa", "Setor da empresa", Category.TRABALHO, FieldType.TEXT,
            listOf("setor da empresa", "setor empresa", "industria da empresa", "company industry")),
        FieldDef("tipo_contrato", "Tipo de contrato", Category.TRABALHO, FieldType.CHOICE,
            listOf("tipo de contrato", "regime de contratacao", "vinculo empregaticio", "contract type"),
            options = listOf("CLT", "PJ", "Estágio", "Temporário", "Autônomo", "Servidor")),
        FieldDef("modelo_trabalho", "Modelo de trabalho", Category.TRABALHO, FieldType.CHOICE,
            listOf("modelo de trabalho", "trabalho remoto", "home office", "presencial ou remoto", "hibrido",
                "work from home", "remote work"),
            options = listOf("Presencial", "Remoto", "Híbrido")),
        FieldDef("empresa", "Empresa", Category.TRABALHO, FieldType.TEXT,
            listOf("nome da empresa", "empresa onde trabalha", "empresa em que trabalha", "empregador", "employer", "company name"),
            sensitive = true),
        FieldDef("tamanho_empresa", "Nº de funcionários da empresa", Category.TRABALHO, FieldType.NUMBER,
            listOf("tamanho da empresa", "quantos funcionarios", "numero de funcionarios", "company size",
                "how many employees", "porte da empresa")),
        FieldDef("tempo_experiencia", "Anos de experiência", Category.TRABALHO, FieldType.NUMBER,
            listOf("anos de experiencia", "tempo de experiencia", "years of experience", "ha quanto tempo trabalha na area")),
        FieldDef("tempo_empresa", "Anos na empresa atual", Category.TRABALHO, FieldType.NUMBER,
            listOf("tempo na empresa", "ha quanto tempo trabalha na empresa", "years at your company")),

        // ── Finanças ─────────────────────────────────────────────────
        FieldDef("renda_mensal", "Renda mensal pessoal (R$)", Category.FINANCAS, FieldType.NUMBER,
            listOf("renda mensal", "renda pessoal", "renda individual", "salario", "quanto voce ganha", "personal income",
                "monthly income", "rendimento mensal", "ingreso mensual"),
            negative = listOf("familia", "familiar", "domiciliar", "household", "casa"),
            sensitive = true, wizardQuestion = "Qual é a sua renda mensal pessoal aproximada (R$)?"),
        FieldDef("renda_familiar", "Renda familiar mensal (R$)", Category.FINANCAS, FieldType.NUMBER,
            listOf("renda familiar", "renda da familia", "renda domiciliar", "household income", "renda total da casa",
                "renda do domicilio", "ingreso familiar"),
            sensitive = true, wizardQuestion = "Qual é a renda familiar mensal aproximada (R$)?"),
        FieldDef("possui_cartao_credito", "Possui cartão de crédito", Category.FINANCAS, FieldType.BOOLEAN,
            listOf("cartao de credito", "credit card", "tarjeta de credito"),
            negative = listOf("numero", "cvv", "validade", "bandeira")),
        FieldDef("bancos", "Bancos que usa", Category.FINANCAS, FieldType.LIST,
            listOf("banco", "bancos", "bank", "instituicao financeira", "conta corrente", "banco principal"),
            negative = listOf("dados", "banco de dados", "banco de horas", "digital")),
        FieldDef("usa_pix", "Usa Pix", Category.FINANCAS, FieldType.BOOLEAN, listOf("pix")),
        FieldDef("bancos_digitais", "Bancos digitais", Category.FINANCAS, FieldType.LIST,
            listOf("banco digital", "bancos digitais", "digital bank", "fintech")),
        FieldDef("investimentos", "Investimentos", Category.FINANCAS, FieldType.LIST,
            listOf("investimento", "investimentos", "investe", "invest", "aplicacoes financeiras", "tipos de investimento")),
        FieldDef("seguros", "Seguros", Category.FINANCAS, FieldType.LIST, listOf("seguro", "seguros", "insurance")),
        FieldDef("formas_pagamento", "Formas de pagamento", Category.FINANCAS, FieldType.LIST,
            listOf("forma de pagamento", "formas de pagamento", "como costuma pagar", "payment method")),

        // ── Tecnologia ───────────────────────────────────────────────
        FieldDef("celular_marca", "Marca do celular", Category.TECNOLOGIA, FieldType.TEXT,
            listOf("marca do celular", "marca do seu celular", "marca do smartphone", "smartphone", "celular voce usa",
                "phone brand", "mobile phone", "aparelho celular", "qual celular"),
            negative = listOf("numero", "operadora", "telefone")),
        FieldDef("celular_modelo", "Modelo do celular", Category.TECNOLOGIA, FieldType.TEXT,
            listOf("modelo do celular", "modelo do smartphone", "phone model")),
        FieldDef("sistema_celular", "Sistema do celular", Category.TECNOLOGIA, FieldType.CHOICE,
            listOf("sistema operacional do celular", "android ou ios", "android ou iphone", "mobile operating system",
                "sistema do smartphone"),
            options = listOf("Android", "iOS")),
        FieldDef("computadores", "Computadores", Category.TECNOLOGIA, FieldType.LIST,
            listOf("computador", "notebook", "laptop", "desktop", "pc")),
        FieldDef("sistema_computador", "Sistema do computador", Category.TECNOLOGIA, FieldType.CHOICE,
            listOf("sistema operacional do computador", "sistema operacional", "operating system"),
            options = listOf("Windows", "macOS", "Linux", "ChromeOS"),
            negative = listOf("celular", "smartphone", "mobile")),
        FieldDef("operadoras", "Operadora de celular", Category.TECNOLOGIA, FieldType.LIST,
            listOf("operadora", "operadoras", "carrier", "plano de celular", "mobile carrier")),
        FieldDef("provedor_internet", "Provedor de internet", Category.TECNOLOGIA, FieldType.TEXT,
            listOf("provedor de internet", "internet banda larga", "internet provider", "operadora de internet", "fibra")),
        FieldDef("dispositivos", "Dispositivos que possui", Category.TECNOLOGIA, FieldType.LIST,
            listOf("dispositivos", "aparelhos eletronicos", "smartwatch", "smart tv", "tablet", "console", "devices")),
        FieldDef("apps", "Aplicativos que usa", Category.TECNOLOGIA, FieldType.LIST,
            listOf("aplicativos que usa", "apps que usa", "aplicativos voce usa", "apps you use")),

        // ── Consumo ──────────────────────────────────────────────────
        FieldDef("marcas_utilizadas", "Marcas que usa", Category.CONSUMO, FieldType.LIST,
            listOf("marcas", "marca que voce usa", "marcas que utiliza", "brands", "brand")),
        FieldDef("produtos_utilizados", "Produtos que usa", Category.CONSUMO, FieldType.LIST,
            listOf("produtos que usa", "produtos que utiliza", "products you use", "produtos voce compra")),
        FieldDef("compras_online", "Faz compras online", Category.CONSUMO, FieldType.BOOLEAN,
            listOf("compra online", "compras online", "compras pela internet", "compra pela internet", "shop online",
                "e commerce", "compras en linea")),
        FieldDef("sites_compras", "Sites/lojas de compra", Category.CONSUMO, FieldType.LIST,
            listOf("sites de compra", "lojas online", "marketplace", "onde voce compra", "loja virtual")),
        FieldDef("supermercados", "Supermercados", Category.CONSUMO, FieldType.LIST,
            listOf("supermercado", "supermercados", "grocery", "mercado onde compra")),
        FieldDef("responsavel_compras", "Responsável pelas compras da casa", Category.CONSUMO, FieldType.BOOLEAN,
            listOf("responsavel pelas compras", "decide as compras", "primary shopper", "quem faz as compras")),

        // ── Entretenimento ───────────────────────────────────────────
        FieldDef("streaming", "Streaming de vídeo", Category.ENTRETENIMENTO, FieldType.LIST,
            listOf("streaming", "servicos de video", "servico de streaming", "streaming service", "assiste filmes")),
        FieldDef("musica", "Streaming / apps de música", Category.ENTRETENIMENTO, FieldType.LIST,
            listOf("musica", "streaming de musica", "ouve musica", "music")),
        FieldDef("jogos", "Jogos / plataformas", Category.ENTRETENIMENTO, FieldType.LIST,
            listOf("jogos", "games", "videogame", "joga", "gaming")),
        FieldDef("redes_sociais", "Redes sociais", Category.ENTRETENIMENTO, FieldType.LIST,
            listOf("redes sociais", "rede social", "social media", "social network", "redes sociales")),
        FieldDef("assinaturas", "Assinaturas", Category.ENTRETENIMENTO, FieldType.LIST,
            listOf("assinatura", "assinaturas", "subscriptions", "assina")),

        // ── Transporte ───────────────────────────────────────────────
        FieldDef("possui_carro", "Possui carro", Category.TRANSPORTE, FieldType.BOOLEAN,
            listOf("possui carro", "tem carro", "carro proprio", "own a car", "possui veiculo", "possui automovel", "carro"),
            negative = listOf("marca", "modelo", "ano", "brand", "aplicativo", "alug")),
        FieldDef("veiculos", "Veículos (marca/modelo)", Category.TRANSPORTE, FieldType.LIST,
            listOf("marca do carro", "marca do seu carro", "modelo do carro", "car brand", "qual carro", "marca do veiculo")),
        FieldDef("possui_moto", "Possui moto", Category.TRANSPORTE, FieldType.BOOLEAN,
            listOf("moto", "motocicleta", "motorcycle")),
        FieldDef("possui_bicicleta", "Possui bicicleta", Category.TRANSPORTE, FieldType.BOOLEAN,
            listOf("bicicleta", "bike", "bicycle")),
        FieldDef("transporte_publico", "Usa transporte público", Category.TRANSPORTE, FieldType.BOOLEAN,
            listOf("transporte publico", "onibus", "metro", "public transport")),
        FieldDef("apps_transporte", "Apps de transporte", Category.TRANSPORTE, FieldType.LIST,
            listOf("aplicativo de transporte", "app de transporte", "uber", "ride hailing", "carro por aplicativo")),

        // ── Viagens ──────────────────────────────────────────────────
        FieldDef("frequencia_viagens", "Frequência de viagens", Category.VIAGENS, FieldType.TEXT,
            listOf("com que frequencia voce viaja", "frequencia de viagens", "quantas vezes viaja", "how often do you travel",
                "viagens por ano")),
        FieldDef("viagens_internacionais", "Faz viagens internacionais", Category.VIAGENS, FieldType.BOOLEAN,
            listOf("viagem internacional", "viagens internacionais", "viajou para o exterior", "international travel", "exterior")),
        FieldDef("viagens_nacionais", "Faz viagens nacionais", Category.VIAGENS, FieldType.BOOLEAN,
            listOf("viagem nacional", "viagens nacionais", "domestic travel")),
        FieldDef("destinos", "Destinos visitados", Category.VIAGENS, FieldType.LIST,
            listOf("destinos", "lugares visitou", "destinations")),
        FieldDef("companhias_aereas", "Companhias aéreas", Category.VIAGENS, FieldType.LIST,
            listOf("companhia aerea", "companhias aereas", "airline")),
        FieldDef("plataformas_reserva", "Plataformas de reserva", Category.VIAGENS, FieldType.LIST,
            listOf("plataforma de reserva", "reserva de hotel", "booking", "hospedagem")),

        // ── Alimentação ──────────────────────────────────────────────
        FieldDef("restricoes_alimentares", "Restrições alimentares", Category.ALIMENTACAO, FieldType.LIST,
            listOf("restricao alimentar", "restricoes alimentares", "dieta", "vegetariano", "vegano", "dietary")),
        FieldDef("preferencias_alimentares", "Preferências alimentares", Category.ALIMENTACAO, FieldType.LIST,
            listOf("preferencia alimentar", "culinaria", "comida favorita")),
        FieldDef("frequencia_restaurantes", "Frequência em restaurantes", Category.ALIMENTACAO, FieldType.TEXT,
            listOf("restaurante", "come fora", "eat out", "comer fora")),
        FieldDef("usa_delivery", "Usa delivery de comida", Category.ALIMENTACAO, FieldType.BOOLEAN,
            listOf("delivery", "entrega de comida", "food delivery", "pede comida")),
        FieldDef("apps_delivery", "Apps de delivery", Category.ALIMENTACAO, FieldType.LIST,
            listOf("aplicativo de delivery", "app de delivery", "delivery apps")),

        // ── Saúde / hábitos ──────────────────────────────────────────
        FieldDef("atividade_fisica", "Pratica atividade física", Category.SAUDE, FieldType.BOOLEAN,
            listOf("atividade fisica", "exercicio", "academia", "pratica esporte", "exercise", "workout")),
        FieldDef("fumante", "Fumante", Category.SAUDE, FieldType.BOOLEAN,
            listOf("fuma", "fumante", "cigarro", "tabaco", "smoke", "smoker")),
        FieldDef("bebe_alcool", "Consome bebida alcoólica", Category.SAUDE, FieldType.BOOLEAN,
            listOf("bebida alcoolica", "alcool", "consome alcool", "alcohol", "cerveja")),
        FieldDef("plano_saude", "Possui plano de saúde", Category.SAUDE, FieldType.BOOLEAN,
            listOf("plano de saude", "convenio medico", "health insurance", "seguro saude")),

        // ── Animais ──────────────────────────────────────────────────
        FieldDef("possui_animais", "Possui animais de estimação", Category.ANIMAIS, FieldType.BOOLEAN,
            listOf("animal de estimacao", "animais de estimacao", "pet", "pets", "bicho de estimacao", "mascota"),
            negative = listOf("quais", "quanto", "quantos", "which", "how many", "tipo", "especie"),
            wizardQuestion = "Você tem animais de estimação?"),
        FieldDef("animais", "Quais animais", Category.ANIMAIS, FieldType.LIST,
            listOf("quais animais", "tipo de animal", "especie", "cachorro", "gato", "dog", "cat", "which pets")),
        FieldDef("quantidade_animais", "Quantidade de animais", Category.ANIMAIS, FieldType.NUMBER,
            listOf("quantos animais", "quantos pets", "how many pets")),

        // ── Casa ─────────────────────────────────────────────────────
        FieldDef("tipo_moradia", "Tipo de moradia", Category.CASA, FieldType.CHOICE,
            listOf("tipo de moradia", "tipo de residencia", "casa ou apartamento", "type of home", "tipo de imovel", "housing type"),
            options = listOf("Casa", "Apartamento", "Outro")),
        FieldDef("situacao_moradia", "Imóvel próprio ou alugado", Category.CASA, FieldType.CHOICE,
            listOf("propria ou alugada", "imovel proprio", "casa propria", "aluguel", "own or rent", "mora de aluguel"),
            options = listOf("Própria", "Alugada", "Financiada", "Cedida")),
        FieldDef("quartos", "Número de quartos", Category.CASA, FieldType.NUMBER,
            listOf("quartos", "dormitorios", "bedrooms")),
        FieldDef("eletrodomesticos", "Eletrodomésticos", Category.CASA, FieldType.LIST,
            listOf("eletrodomesticos", "aparelhos em casa", "appliances")),

        FieldDef("observacoes", "Observações (não usadas para responder)", Category.PERSONALIZADO, FieldType.TEXT)
    )

    private val byKey: Map<String, FieldDef> = fields.associateBy { it.key }

    fun field(key: String): FieldDef? = byKey[key] ?: customField(key)

    fun byCategory(category: Category): List<FieldDef> = fields.filter { it.category == category }

    const val CUSTOM_PREFIX = "custom."

    fun isCustom(key: String) = key.startsWith(CUSTOM_PREFIX)

    fun customKey(label: String): String =
        CUSTOM_PREFIX + Text.normalize(label).replace(' ', '_').take(60).ifBlank { "campo" }

    /** Campos personalizados são definidos dinamicamente a partir do rótulo. */
    fun customField(key: String, label: String? = null): FieldDef? {
        if (!isCustom(key)) return null
        val lbl = label ?: key.removePrefix(CUSTOM_PREFIX).replace('_', ' ')
        return FieldDef(key, lbl, Category.PERSONALIZADO, FieldType.TEXT, keywords = listOf(lbl))
    }

    /** Resolve um nome vindo de JSON (ex.: "tem_filhos", "familia.tem_filhos", "faixa_renda_pessoal"). */
    fun resolveJsonName(name: String): FieldDef? {
        val simple = name.substringAfterLast('.').lowercase()
        byKey[simple]?.let { return it }
        return JSON_ALIASES[simple]?.let { byKey[it] }
    }

    private val JSON_ALIASES = mapOf(
        "sistema_operacional" to "sistema_celular",
        "celular" to "celular_marca",
        "computador" to "computadores",
        "servicos_streaming" to "streaming",
        "servicos_assinatura" to "assinaturas",
        "animais_estimacao" to "animais",
        "quantidade_moradores" to "moradores_residencia",
        "possui_seguro" to "seguros",
        "possui_investimentos" to "investimentos",
        "tipos_investimentos" to "investimentos",
        "tipo_trabalho" to "modelo_trabalho",
        "tipo_emprego" to "situacao_emprego",
        "area" to "area_profissional",
        "operadora" to "operadoras",
        "empregado" to "situacao_emprego",
        "sites_utilizados" to "sites_compras",
        "restricoes" to "restricoes_alimentares",
        "preferencias_alimentacao" to "preferencias_alimentares",
        "usa_transporte_publico" to "transporte_publico",
        "usa_aplicativos_transporte" to "apps_transporte",
        "frequencia" to "frequencia_viagens",
        "destinos_visitados" to "destinos",
        "faixa_renda" to "renda_mensal",
        "faixa_renda_pessoal" to "renda_mensal",
        "faixa_renda_familiar" to "renda_familiar",
        "compras_online_sites" to "sites_compras",
        "bancos_utilizados" to "bancos",
        "moradores" to "moradores_residencia"
    )

    /** Campos prioritários do assistente de perfil. */
    val wizardFields: List<FieldDef> get() = fields.filter { it.wizardQuestion != null } +
        listOf("profissao", "celular_marca", "possui_carro", "possui_cartao_credito", "compras_online",
            "streaming", "redes_sociais", "tipo_moradia", "situacao_moradia", "animais")
            .mapNotNull { k -> byKey[k]?.takeIf { it.wizardQuestion == null } }
}
