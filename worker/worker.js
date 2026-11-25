const ANALYZE_MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast";
const TRANSCRIBE_MODEL = "@cf/openai/whisper-large-v3-turbo";
const MAX_AUDIO_BASE64_CHARS = 12 * 1024 * 1024;
const ANALYZE_CHUNK_CHARS = 12000;
const ANALYZE_OVERLAP_CHARS = 1000;

export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };
    const reply = (data, status = 200) => new Response(JSON.stringify(data), {
      status,
      headers: { ...cors, "Content-Type": "application/json; charset=UTF-8" },
    });

    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const url = new URL(request.url);

    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/health")) {
      return reply({
        app: "ClipGenius AI",
        status: "online",
        version: "0.2.0",
        endpoints: ["/transcribe", "/analyze"],
        capabilities: ["chunk transcription", "timestamped transcript", "multi-section clip analysis"],
      });
    }

    if (!env.AI) return reply({ error: "Workers AI binding missing", details: "Bind Workers AI as AI in the Worker settings." }, 500);

    if (request.method === "POST" && url.pathname === "/transcribe") {
      try {
        const body = await request.json();
        if (typeof body.audio !== "string" || body.audio.length < 100) {
          return reply({ error: "Audio data is missing or invalid" }, 400);
        }
        if (body.audio.length > MAX_AUDIO_BASE64_CHARS) {
          return reply({ error: "Audio chunk is too large", maxBase64Characters: MAX_AUDIO_BASE64_CHARS }, 413);
        }

        const result = await env.AI.run(TRANSCRIBE_MODEL, {
          audio: body.audio,
          task: "transcribe",
        });
        const text = typeof result?.text === "string" ? result.text : "";
        if (!text.trim()) {
          return reply({
            error: "Whisper returned an empty transcript",
            diagnostic: {
              resultType: typeof result,
              resultKeys: result && typeof result === "object" ? Object.keys(result) : [],
            },
          }, 502);
        }
        return reply({
          success: true,
          text,
          segments: Array.isArray(result?.segments) ? result.segments : [],
          vtt: typeof result?.vtt === "string" ? result.vtt : "",
          language: result?.transcription_info?.language || "",
          duration: Number(result?.transcription_info?.duration) || 0,
          provider: "Cloudflare Whisper large-v3-turbo",
        });
      } catch (error) {
        return reply({ error: "Transcription failed", details: String(error?.message || error) }, 502);
      }
    }

    if (request.method === "POST" && url.pathname === "/analyze") {
      try {
        const body = await request.json();
        const transcript = typeof body.transcript === "string" ? body.transcript.trim() : "";
        if (transcript.length < 20) return reply({ error: "Please provide a transcript of at least 20 characters" }, 400);
        if (transcript.length > 100000) return reply({ error: "Transcript is too long for one analysis job. Maximum is 100,000 characters." }, 413);

        const pieces = splitTranscript(transcript, ANALYZE_CHUNK_CHARS, ANALYZE_OVERLAP_CHARS);
        const allClips = [];
        const diagnostics = [];

        for (let index = 0; index < pieces.length; index++) {
          const prompt = `You are an expert short-form video editor. Analyze only the transcript section below.

Return ONLY a JSON object in this format:
{"clips":[{"title":"Hindi title","start_seconds":12.5,"end_seconds":48.2,"start_hint":"exact opening words","end_hint":"exact ending words","reason":"simple Hindi reason","score":90}]}

Rules:
- Return up to 3 strongest, non-duplicate moments from this section.
- Only recommend a clip when the transcript has timestamp markers like [MM:SS] or [HH:MM:SS].
- start_seconds and end_seconds MUST be copied/estimated from timestamp markers present in the transcript, converted to seconds. Never invent timestamps. If the transcript has no usable timestamps, use null for both time fields.
- Choose complete, engaging moments suitable for 15-60 second Shorts. Prefer a strong hook and a clear payoff.
- Use exact opening and ending words from the transcript in start_hint/end_hint.
- Titles and reasons should be in simple Hindi.
- Score 1-100. Return an empty clips array if no suitable moment exists.
- JSON only, no Markdown.

Transcript section ${index + 1} of ${pieces.length}:
${pieces[index]}`;

          const result = await env.AI.run(ANALYZE_MODEL, {
            messages: [
              { role: "system", content: "You return concise, valid JSON only. Never fabricate timestamps or transcript wording." },
              { role: "user", content: prompt },
            ],
            response_format: { type: "json_object" },
            max_tokens: 1200,
            temperature: 0.15,
          });

          const aiText = extractModelText(result);
          if (!aiText) {
            diagnostics.push({
              section: index + 1,
              error: "AI returned an empty response",
              resultType: typeof result,
              resultKeys: result && typeof result === "object" ? Object.keys(result) : [],
              usage: result?.usage || null,
            });
            continue;
          }

          let parsed;
          try {
            parsed = JSON.parse(stripJsonFences(aiText));
          } catch (parseError) {
            diagnostics.push({ section: index + 1, error: "AI returned invalid JSON", details: String(parseError?.message || parseError), responsePreview: aiText.slice(0, 500) });
            continue;
          }

          if (!Array.isArray(parsed?.clips)) {
            diagnostics.push({ section: index + 1, error: "AI JSON did not contain a clips array" });
            continue;
          }

          for (const clip of parsed.clips) {
            if (!clip || typeof clip.title !== "string" || typeof clip.start_hint !== "string" || typeof clip.end_hint !== "string") continue;
            const start = Number(clip.start_seconds);
            const end = Number(clip.end_seconds);
            const hasTimes = Number.isFinite(start) && Number.isFinite(end) && start >= 0 && end > start;
            allClips.push({
              title: clip.title.trim().slice(0, 120),
              start_seconds: hasTimes ? start : null,
              end_seconds: hasTimes ? end : null,
              start_hint: clip.start_hint.trim().slice(0, 300),
              end_hint: clip.end_hint.trim().slice(0, 300),
              reason: typeof clip.reason === "string" ? clip.reason.trim().slice(0, 500) : "",
              score: Math.max(1, Math.min(100, Math.round(Number(clip.score) || 1))),
            });
          }
        }

        const unique = dedupeClips(allClips).sort((a, b) => b.score - a.score).slice(0, 12);
        if (unique.length === 0 && diagnostics.length > 0) {
          return reply({ error: "AI could not complete clip analysis", details: diagnostics, clips: [] }, 502);
        }
        return reply({
          success: true,
          provider: "Cloudflare Workers AI",
          sectionsAnalyzed: pieces.length,
          clips: unique,
          warnings: diagnostics,
        });
      } catch (error) {
        return reply({ error: "Cloudflare AI analysis failed", details: String(error?.message || error) }, 502);
      }
    }

    return reply({ error: "Route not found" }, 404);
  },
};

function extractModelText(result) {
  if (typeof result === "string") return result.trim();
  if (typeof result?.response === "string") return result.response.trim();
  if (typeof result?.result?.response === "string") return result.result.response.trim();
  if (typeof result?.output_text === "string") return result.output_text.trim();
  return "";
}

function stripJsonFences(value) {
  let text = String(value || "").replace(/```json/gi, "").replace(/```/g, "").trim();
  const first = text.indexOf("{");
  const last = text.lastIndexOf("}");
  if (first >= 0 && last > first) text = text.slice(first, last + 1);
  return text;
}

function splitTranscript(text, maxChars, overlap) {
  if (text.length <= maxChars) return [text];
  const chunks = [];
  let start = 0;
  while (start < text.length) {
    let end = Math.min(text.length, start + maxChars);
    if (end < text.length) {
      const boundary = text.lastIndexOf("\n", end);
      if (boundary > start + Math.floor(maxChars * 0.6)) end = boundary;
    }
    chunks.push(text.slice(start, end));
    if (end >= text.length) break;
    start = Math.max(start + 1, end - overlap);
  }
  return chunks;
}

function dedupeClips(clips) {
  const result = [];
  for (const clip of clips) {
    const duplicate = result.some(existing => {
      if (clip.start_seconds == null || existing.start_seconds == null) {
        return existing.start_hint.toLowerCase() === clip.start_hint.toLowerCase();
      }
      return Math.abs(existing.start_seconds - clip.start_seconds) < 12 && Math.abs(existing.end_seconds - clip.end_seconds) < 15;
    });
    if (!duplicate) result.push(clip);
  }
  return result;
}
