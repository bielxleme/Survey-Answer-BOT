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
      description: "Must be 'ANSWER' if provable from profile, or 'ASK_USER' if data is missing or ambiguous.",
    },
    answer: {
      type: 'STRING',
      description: 'The exact answer value or matching option. Null if unknown.',
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

// API Endpoint for Agent Semantic Interpretation
app.post('/api/ai/interpret', async (req: Request, res: Response) => {
  try {
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
3. If the profile does not provide or strictly logically determine the required information, you MUST set action="ASK_USER", answer=null, confidence=0, needs_user=true.
4. If options are provided (radio, checkbox, dropdown), select the option that best matches the profile data without extrapolating.
5. If mathematical or strict logical derivation is permitted (e.g. birthdate to age, tem_filhos=false to 0 children), specify source as derived.
6. Output MUST strictly conform to the JSON schema.`;

    const promptText = JSON.stringify({
      question,
      options: options || [],
      field_type: field_type || 'text',
      user_profile_subset: filtered_profile || {},
    });

    const response = await ai.models.generateContent({
      model: 'gemini-3.8-flash',
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
    return res.json(parsed);
  } catch (error: any) {
    console.error('Error in /api/ai/interpret:', error);
    return res.status(500).json({
      error: error?.message || 'Internal AI interpretation error',
      action: 'ASK_USER',
      needs_user: true,
      confidence: 0,
      reason: 'Failed to process question via LLM; falling back to user intervention.',
    });
  }
});

// Vite middleware mounting for SPA dev server
async function startServer() {
  const vite = await createViteServer({
    server: { middlewareMode: true },
    appType: 'spa',
  });

  app.use(vite.middlewares);

  app.listen(port, '0.0.0.0', () => {
    console.log(`[Research Agent Studio] Server running on http://0.0.0.0:${port}`);
  });
}

startServer();
