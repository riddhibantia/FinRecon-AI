"use client";

import { useState } from "react";
import { display } from "@/lib/format";
import { Badge, Notice } from "@/components/ui";
import type { Investigation } from "@/lib/types";

export default function AiPanel({ exceptionId }: { exceptionId: string }) {
  const [pending, setPending] = useState(false);
  const [asOf, setAsOf] = useState(() => new Date().toISOString().slice(0, 10));
  const [tolerance, setTolerance] = useState("");
  const [result, setResult] = useState<Investigation | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function investigate(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const body: Record<string, string> = { exceptionId, as_of: asOf };
      if (tolerance.trim() !== "") body.tolerance = tolerance.trim();
      const res = await fetch("/api/ai/investigate", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(body),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(
          data.message ?? data.reason ?? `Investigation unavailable (HTTP ${res.status}).`,
        );
        return;
      }
      setResult(data as Investigation);
    } catch {
      setError("The AI service is unreachable. Case facts above are unaffected.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="card">
      <h2>AI investigation</h2>
      <p className="muted">
        Cited, read-only analysis. It cannot change the case — assign, resolve, or escalate
        below when you agree.
      </p>
      <form onSubmit={investigate} aria-label="Request AI investigation">
        <div className="row">
          <label className="field" htmlFor="ai-asof">
            Policy date (as_of)
            <input
              id="ai-asof"
              type="date"
              value={asOf}
              onChange={(e) => setAsOf(e.target.value)}
              required
            />
          </label>
          <label className="field" htmlFor="ai-tolerance">
            Tolerance (optional)
            <input
              id="ai-tolerance"
              value={tolerance}
              onChange={(e) => setTolerance(e.target.value)}
              placeholder="0.01"
              inputMode="decimal"
              autoComplete="off"
            />
          </label>
          <button type="submit" disabled={pending} aria-busy={pending}>
            {pending ? "Investigating…" : "Request investigation"}
          </button>
        </div>
      </form>

      <div aria-live="polite">

      {error && (
        <Notice kind="error" title="Investigation unavailable">
          {error}
        </Notice>
      )}

      {result && (
        <div>
          <p>
            <Badge status={result.status} />{" "}
            {result.root_cause && (
              <>
                root cause <strong>{result.root_cause}</strong>
              </>
            )}
          </p>
          {result.summary && <p>{result.summary}</p>}
          {result.recommended_action && (
            <p>
              <strong>Recommended action:</strong> {result.recommended_action}
            </p>
          )}
          <p className="muted">
            Confidence:{" "}
            {result.confidence === null || result.confidence === undefined
              ? `withheld (${display(result.confidence_reason)})`
              : result.confidence}
          </p>
          {typeof result.confidence === "number" && (
            <div
              className="confidence-bar"
              role="img"
              aria-label={`confidence ${result.confidence} percent`}
            >
              <div
                className="confidence-fill"
                style={{ width: `${Math.max(0, Math.min(100, result.confidence))}%` }}
              />
            </div>
          )}

          {result.reasons.length > 0 && (
            <>
              <h3>Review notes</h3>
              <ul>
                {result.reasons.map((r, i) => (
                  <li key={i}>{r}</li>
                ))}
              </ul>
            </>
          )}

          {result.evidence.length > 0 && (
            <>
              <h3>Evidence used</h3>
              <table className="grid">
                <caption className="muted">Sources cited by the investigation</caption>
                <thead>
                  <tr>
                    <th scope="col">Source</th>
                    <th scope="col">Field</th>
                    <th scope="col">Expected</th>
                    <th scope="col">Observed</th>
                  </tr>
                </thead>
                <tbody>
                  {result.evidence.map((e, i) => (
                    <tr key={i}>
                      <td>
                        {display(e.source_type)} {display(e.source_id)}
                      </td>
                      <td>{display(e.field)}</td>
                      <td>{display(e.expected)}</td>
                      <td>{display(e.observed)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}

          {result.citations.length > 0 && (
            <>
              <h3>Policy citations</h3>
              {result.citations.map((c, i) => (
                <div key={i}>
                  <p>
                    <strong>
                      {display(c.document)} v{display(c.version)}
                    </strong>{" "}
                    · {display(c.section)} · page {display(String(c.page))} · score{" "}
                    {display(String(c.score))}
                  </p>
                  <pre className="excerpt">{c.excerpt}</pre>
                </div>
              ))}
            </>
          )}

          {result.draft && (
            <Notice kind="info" title="Draft resolution (approval required)">
              {display(result.draft.action)}
            </Notice>
          )}
        </div>
      )}
      </div>
    </div>
  );
}
