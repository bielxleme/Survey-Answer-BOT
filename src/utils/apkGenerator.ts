import fs from 'fs';
import path from 'path';

/**
 * APK nativo do Research Agent.
 *
 * ANTES: este arquivo "remendava" um APK genérico de WebView de terceiros (server-assets/base.apk),
 * trocando uma string dentro do classes.dex. Isso quebrava a ordenação obrigatória da tabela de
 * strings do DEX → o Android rejeitava o DEX e o app fechava ao abrir. Além disso, o bundle do Vite
 * (que usa `import.meta`) era embutido como <script> clássico → tela branca.
 *
 * AGORA: o APK é compilado de verdade a partir do projeto Kotlin em `android/`
 * (GitHub Actions → Releases). O servidor apenas entrega esse arquivo, sem modificá-lo.
 */
export const GITHUB_REPO = process.env.GITHUB_REPO || 'bielxleme/Survey-Answer-BOT';
export const RELEASE_APK_URL = `https://github.com/${GITHUB_REPO}/releases/latest/download/ResearchAgent.apk`;

const CANDIDATES = [
  'android/app/build/outputs/apk/release/app-release.apk', // build local (Android Studio / ./gradlew assembleRelease)
  'public/downloads/ResearchAgent.apk', // cópia do APK baixado do GitHub Releases
];

/** Retorna o APK nativo se existir localmente; senão null (o servidor redireciona para o GitHub Releases). */
export function findNativeApk(): Buffer | null {
  for (const rel of CANDIDATES) {
    const p = path.resolve(process.cwd(), rel);
    if (!fs.existsSync(p)) continue;
    const buf = fs.readFileSync(p);
    // sanidade: APK é um ZIP ("PK\x03\x04") e o nosso contém o pacote com.researchagent.autofill
    const isZip = buf.length > 4 && buf.readUInt32LE(0) === 0x04034b50;
    // o resources.arsc (sem compressão) do APK nativo contém o pacote em UTF-16
    const ours = buf.includes(Buffer.from('com.researchagent.autofill', 'utf16le'));
    if (isZip && ours) {
      return buf;
    }
  }
  return null;
}
