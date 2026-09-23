import express, { Request, Response } from 'express';
import { createServer as createViteServer } from 'vite';
import { GoogleGenAI } from '@google/genai';
import dotenv from 'dotenv';

dotenv.config();

const app = express();
const port = 3000;

app.use(express.json());

// Initialize Gemini SDK with telemetry header
const ai = new GoogleGenAI({
  apiKey: process.env.GEMINI_API_KEY || '',
  httpOptions: {
    headers: {
      'User-Agent': 'aistudio-build',
    },
  },
});

interface InterpretationRequest {
  question: string;
  options?: string[];
  field_type?: string;
  filtered_profile: Record<string, any>;
}

// Strictly structured JSON schema for question interpretation
const interpretationSchema = {
  type: 'OBJECT',
  properties: {
    action: {
      type: 'STRING',
      enum: ['ANSWER', 'ASK_USER'],
      description: "Must be 'ANSWER' if provable from profile, or 'ASK_USER' if data is missing or ambiguous.",
    },
    answer: {
      type: 'STRING',
      description: 'The exact answer value or matching option. Empty or null if unknown.',
    },
    confidence: {
      type: 'NUMBER',
      description: 'Confidence between 0.0 and 1.0. 0.95+ for exact facts, 0 if unknown.',
    },
    source: {
      type: 'STRING',
      description: 'The profile key that proves this answer, e.g., "identidade.idade" or "trabalho.profissao".',
    },
    reason: {
      type: 'STRING',
      description: 'Explanation grounded strictly in provided profile data. Never speculate.',
    },
    needs_user: {
      type: 'BOOLEAN',
      description: 'True if data is not available in the profile, forcing human intervention.',
    },
  },
  required: ['action', 'confidence', 'reason', 'needs_user'],
};

// Available Gemini models for fast semantic interpretation with fallback redundancy
const CANDIDATE_MODELS = [
  'gemini-3.1-flash-lite',
  'gemini-3.8-flash',
  'gemini-flash-latest',
];

// API Endpoint for Agent Semantic Interpretation
app.post('/api/ai/interpret', async (req: Request, res: Response) => {
  const { question, options, field_type, filtered_profile } = req.body as InterpretationRequest;

  if (!question) {
    return res.status(400).json({ error: 'Question is required' });
  }

  if (!process.env.GEMINI_API_KEY) {
    return res.json({
      fallback: true,
      message: 'No GEMINI_API_KEY configured. Utilizing deterministic rule-based semantic engine.',
    });
  }

  const systemInstruction = `You are the core semantic intelligence of an Android Research Auto-Fill Agent.
CRITICAL MANDATE:
1. NEVER hallucinate or invent personal information (no fake age, profession, salary, kids, brand preferences, etc.).
2. Ground your decision SOLELY on the user profile fields passed in the input.
3. If the profile does not provide or strictly logically determine the required information, you MUST set action="ASK_USER", answer="", confidence=0, needs_user=true.
4. If options are provided (radio, checkbox, dropdown), select the exact matching option string that represents the profile data.
5. If mathematical or strict logical derivation is permitted (e.g. birthdate to age, tem_filhos=false to 0 children), specify source as derived.
6. Output MUST strictly conform to the JSON schema.`;

  const promptText = JSON.stringify({
    question,
    options: options || [],
    field_type: field_type || 'text',
    user_profile_subset: filtered_profile || {},
  });

  // Try candidate models in order with resilience against temporary 503/429 spikes
  let lastError: any = null;

  for (const model of CANDIDATE_MODELS) {
    try {
      const response = await ai.models.generateContent({
        model,
        contents: promptText,
        config: {
          systemInstruction,
          responseMimeType: 'application/json',
          // @ts-ignore
          responseSchema: interpretationSchema,
          temperature: 0.1,
        },
      });

      const text = response.text?.trim() || '{}';
      const parsed = JSON.parse(text);

      // Normalize action and needs_user
      if (parsed.action !== 'ANSWER' && parsed.action !== 'ASK_USER') {
        if (parsed.answer && parsed.confidence >= 0.7) {
          parsed.action = 'ANSWER';
          parsed.needs_user = false;
        } else {
          parsed.action = 'ASK_USER';
          parsed.needs_user = true;
        }
      }

      return res.json(parsed);
    } catch (err: any) {
      lastError = err;
      const statusCode = err?.status || err?.code;
      // If temporary overload (503 / 429), try next candidate model
      console.warn(`[Gemini API] Model ${model} returned ${statusCode || err?.message}. Trying fallback...`);
    }
  }

  // Graceful fallback to deterministic local engine (prevents app disruption & 500 error display)
  console.log('[Gemini API] All remote models temporarily unavailable. Gracefully delegating to local deterministic engine.');
  return res.json({
    fallback: true,
    action: 'ASK_USER',
    needs_user: true,
    confidence: 0,
    reason: lastError?.message || 'Model temporarily experiencing high demand. Seamlessly using local deterministic engine.',
  });
});

// Endpoint to download the ready-to-install signed APK file
app.get(['/api/download/ResearchAgent.apk', '/ResearchAgent.apk'], async (_req: Request, res: Response) => {
  try {
    const { getOrGenerateApk } = await import('./src/utils/apkGenerator.js');
    const apkBuffer = await getOrGenerateApk();

    res.setHeader('Content-Type', 'application/vnd.android.package-archive');
    res.setHeader('Content-Disposition', 'attachment; filename="ResearchAgent.apk"');
    res.setHeader('Content-Length', apkBuffer.length.toString());
    res.setHeader('Cache-Control', 'public, max-age=3600');
    return res.send(apkBuffer);
  } catch (err: any) {
    console.error('Erro ao disponibilizar ResearchAgent.apk:', err);
    return res.status(500).json({ error: 'Falha ao gerar APK instalável', details: err?.message });
  }
});

// Endpoint to download the complete native Android Studio project ZIP
app.get('/api/download/android-project.zip', async (_req: Request, res: Response) => {
  try {
    const { ANDROID_CODEBASE } = await import('./src/data/androidCodebase.js');
    const { ADDITIONAL_PROJECT_FILES } = await import('./src/utils/androidProjectZip.js');
    const JSZip = (await import('jszip')).default;

    const zip = new JSZip();
    const root = zip.folder('ResearchAgent-Android');
    if (!root) {
      return res.status(500).send('Erro ao inicializar arquivo zip');
    }

    for (const file of ANDROID_CODEBASE) {
      root.file(file.path, file.content);
    }
    for (const file of ADDITIONAL_PROJECT_FILES) {
      root.file(file.path, file.content);
    }

    const zipBuffer = await zip.generateAsync({
      type: 'nodebuffer',
      compression: 'DEFLATE',
      compressionOptions: { level: 6 },
    });

    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="ResearchAgent_Android_Project.zip"');
    res.setHeader('Content-Length', zipBuffer.length.toString());
    return res.send(zipBuffer);
  } catch (err: any) {
    console.error('Erro ao gerar zip do projeto Android:', err);
    return res.status(500).json({ error: 'Falha ao empacotar projeto Android', details: err?.message });
  }
});

// Vite middleware mounting for SPA dev server
async function startServer() {
  const vite = await createViteServer({
    server: {
      middlewareMode: true,
      hmr: false,
    },
    appType: 'spa',
  });

  app.use(vite.middlewares);

  app.listen(port, '0.0.0.0', () => {
    console.log(`[Research Agent Studio] Server running on http://0.0.0.0:${port}`);
  });
}

startServer();
