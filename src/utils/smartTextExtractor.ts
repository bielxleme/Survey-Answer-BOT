/**
 * Extrator Inteligente de Pergunta (Smart Question Extractor)
 * Quando o usuário seleciona ou clica em qualquer trecho (uma letra, uma palavra ou frase parcial),
 * o algoritmo analisa os limites de pontuação (? . ! :) e quebras de linha para capturar a
 * FRASE COMPLETA da pergunta, mesmo que a seleção tenha sido imperfeita.
 */

export function extractFullSentence(fullText: string, selectedSnippet: string, clickIndex?: number): string {
  if (!fullText) return '';
  const trimmedSnippet = selectedSnippet.trim();

  // Se o texto inteiro já é curto ou não tem pontuação composta, retorna o texto limpo
  if (fullText.length <= 120 && !fullText.includes('\n')) {
    return cleanSentence(fullText);
  }

  // Determinar o ponto de partida no texto original
  let startIndex = 0;
  let endIndex = fullText.length;

  if (trimmedSnippet) {
    const foundIdx = fullText.indexOf(trimmedSnippet);
    if (foundIdx !== -1) {
      startIndex = foundIdx;
      endIndex = foundIdx + trimmedSnippet.length;
    }
  } else if (clickIndex !== undefined && clickIndex >= 0 && clickIndex < fullText.length) {
    startIndex = clickIndex;
    endIndex = clickIndex;
  }

  // Expandir para trás até o início da frase/parágrafo
  let sentenceStart = 0;
  for (let i = startIndex - 1; i >= 0; i--) {
    const char = fullText[i];
    // Se encontrar delimitador de frase anterior (. ? ! \n)
    if (char === '\n' || (['.', '?', '!'].includes(char) && i + 1 < fullText.length && fullText[i + 1] === ' ')) {
      sentenceStart = i + 1;
      break;
    }
  }

  // Expandir para frente até o final da frase/pergunta
  let sentenceEnd = fullText.length;
  for (let i = endIndex; i < fullText.length; i++) {
    const char = fullText[i];
    if (char === '?' || char === '\n') {
      sentenceEnd = i + 1;
      break;
    }
    if (['.', '!'].includes(char) && (i + 1 === fullText.length || fullText[i + 1] === ' ' || fullText[i + 1] === '\n')) {
      sentenceEnd = i + 1;
      break;
    }
  }

  const result = fullText.substring(sentenceStart, sentenceEnd);
  return cleanSentence(result) || cleanSentence(fullText);
}

function cleanSentence(text: string): string {
  return text
    .replace(/^[\s\d\.\-\)\*:]+/, '') // remove números ou marcadores no início (ex: "1. ", "• ")
    .replace(/\s+/g, ' ')
    .trim();
}
