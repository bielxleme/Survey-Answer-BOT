import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import JSZip from 'jszip';
import { ApkSigner, SigningKey } from 'apk_sign_ts';

function adler32(buf: Buffer | Uint8Array): number {
  let a = 1,
    b = 0;
  for (let i = 0; i < buf.length; i++) {
    a = (a + buf[i]) % 65521;
    b = (b + a) % 65521;
  }
  return ((b << 16) | a) >>> 0;
}

export async function getOrGenerateApk(): Promise<Buffer> {
  const cachedApkPath = path.resolve(process.cwd(), 'public/ResearchAgent.apk');
  if (fs.existsSync(cachedApkPath)) {
    return fs.readFileSync(cachedApkPath);
  }

  const baseApkPath = path.resolve(process.cwd(), 'server-assets/base.apk');
  const keyPath = path.resolve(process.cwd(), 'server-assets/release.key');
  const certPath = path.resolve(process.cwd(), 'server-assets/release.crt');

  if (!fs.existsSync(baseApkPath) || !fs.existsSync(keyPath) || !fs.existsSync(certPath)) {
    throw new Error('Assets base para compilação do APK não encontrados no servidor.');
  }

  // Generate inlined main.html from dist
  let html = '<html><body><h1>Research Agent</h1></body></html>';
  const distIndexPath = path.resolve(process.cwd(), 'dist/index.html');
  const distAssetsDir = path.resolve(process.cwd(), 'dist/assets');

  if (fs.existsSync(distIndexPath) && fs.existsSync(distAssetsDir)) {
    html = fs.readFileSync(distIndexPath, 'utf8');
    const assetFiles = fs.readdirSync(distAssetsDir);
    const cssFile = assetFiles.find((f) => f.endsWith('.css'));
    const jsFile = assetFiles.find((f) => f.startsWith('index-') && f.endsWith('.js'));

    if (cssFile) {
      const css = fs.readFileSync(path.join(distAssetsDir, cssFile), 'utf8');
      html = html.replace(/<link[^>]*stylesheet[^>]*>/, () => `<style>${css}</style>`);
    }
    if (jsFile) {
      const js = fs.readFileSync(path.join(distAssetsDir, jsFile), 'utf8');
      html = html.replace(
        /<script type="module"[^>]*src="[^"]*"[^>]*><\/script>/,
        () => `<script>${js}</script>`
      );
    }
  }

  const baseApkBuf = fs.readFileSync(baseApkPath);
  const zip = await JSZip.loadAsync(baseApkBuf);

  // 1. Patch classes.dex to load local asset file:///android_asset/main.html
  const dexFile = zip.file('classes.dex');
  if (dexFile) {
    const dex = Buffer.from(await dexFile.async('nodebuffer'));
    const oldUrl = Buffer.from('https://github.com/bishwassagar', 'utf8');
    const newUrl = Buffer.from('file:///android_asset/main.html', 'utf8');
    const idx = dex.indexOf(oldUrl);
    if (idx !== -1) {
      newUrl.copy(dex, idx);

      // Recalculate SHA-1 at bytes 12..31
      const sha1 = crypto.createHash('sha1').update(dex.subarray(32)).digest();
      sha1.copy(dex, 12);

      // Recalculate Adler-32 at bytes 8..11 (little-endian)
      const ck = adler32(dex.subarray(12));
      dex.writeUInt32LE(ck, 8);

      zip.file('classes.dex', dex);
    }
  }

  // 2. Patch resources.arsc (replace My Application with Research Agent)
  const arscFile = zip.file('resources.arsc');
  if (arscFile) {
    const arsc = Buffer.from(await arscFile.async('nodebuffer'));
    const oldName = Buffer.from('My Application', 'utf8');
    const newName = Buffer.from('Research Agent', 'utf8');
    let arscIdx = arsc.indexOf(oldName);
    while (arscIdx !== -1) {
      newName.copy(arsc, arscIdx);
      arscIdx = arsc.indexOf(oldName, arscIdx + 1);
    }
    zip.file('resources.arsc', arsc);
  }

  // 3. Add assets/main.html
  zip.file('assets/main.html', html);

  // 4. Remove old META-INF signatures
  zip.remove('META-INF');
  Object.keys(zip.files).forEach((f) => {
    if (f.startsWith('META-INF/')) {
      zip.remove(f);
    }
  });

  // 5. Generate unsigned APK
  const unsignedApk = await zip.generateAsync({
    type: 'uint8array',
    compression: 'DEFLATE',
    compressionOptions: { level: 6 },
  });

  // 6. Sign APK with v1 + v2 + v3 schemes
  const keyPem = fs.readFileSync(keyPath, 'utf8');
  const certPem = fs.readFileSync(certPath, 'utf8');
  const signingKey = SigningKey.fromPEM(keyPem, certPem);
  const signer = new ApkSigner({ signingKey });

  const { signedApk } = await signer.sign(unsignedApk);
  const signedBuf = Buffer.from(signedApk);

  // Cache APK
  try {
    fs.mkdirSync(path.dirname(cachedApkPath), { recursive: true });
    fs.writeFileSync(cachedApkPath, signedBuf);
  } catch (e) {
    console.warn('Não foi possível salvar cache de ResearchAgent.apk:', e);
  }

  return signedBuf;
}
