"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Notice } from "@/components/ui";
import { EmojiReaction } from "@/components/ui/emoji-reaction";

// FR-11: the analyst confirms or corrects the classification. The form never
// rewrites the deterministic category; it records the correction as feedback.
export default function FeedbackForm({
  caseId,
  currentCategory,
}: {
  caseId: string;
  currentCategory: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [analystId, setAnalystId] = useState("");
  const [correctedValue, setCorrectedValue] = useState(currentCategory);
  const [reason, setReason] = useState("");

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const res = await fetch(`/api/cases/${encodeURIComponent(caseId)}/feedback`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({
          analystId,
          originalValue: currentCategory,
          correctedValue,
          reason: reason.trim() === "" ? null : reason,
        }),
      });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError(
          (data as { message?: string }).message ?? `Not recorded (HTTP ${res.status}).`,
        );
        return;
      }
      router.refresh();
    } catch {
      setError("Not recorded: exception service is unreachable.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="card">
      <h2>Record analyst correction</h2>
      <p className="muted">
        Append-only. Confirms or corrects the classification; the original value is
        retained and the change is audited.
      </p>
      <form onSubmit={submit} aria-label="Record analyst correction">
        <div className="row">
          <label className="field" htmlFor="fb-analyst">
            Analyst
            <input
              id="fb-analyst"
              value={analystId}
              onChange={(e) => setAnalystId(e.target.value)}
              required
              maxLength={128}
              autoComplete="off"
            />
          </label>
          <label className="field" htmlFor="fb-corrected">
            Corrected category
            <input
              id="fb-corrected"
              value={correctedValue}
              onChange={(e) => setCorrectedValue(e.target.value)}
              required
              maxLength={64}
              autoComplete="off"
            />
          </label>
          <label className="field" htmlFor="fb-reason">
            Reason
            <input
              id="fb-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
            />
          </label>
          <button type="submit" disabled={pending} aria-busy={pending}>
            {pending ? "Recording…" : "Record correction"}
          </button>
          {/* RareUI quick tag: taps an emoji sentiment into the reason field. */}
          <span className="field">
            Quick tag
            <EmojiReaction
              size="sm"
              onReact={(name) =>
                setReason((r) => (r ? `${r} [${name}]` : `[${name}]`))
              }
            />
          </span>
        </div>
      </form>
      <div aria-live="polite">
        {error && (
          <Notice kind="error" title="Feedback not recorded">
            {error}
          </Notice>
        )}
      </div>
    </div>
  );
}
