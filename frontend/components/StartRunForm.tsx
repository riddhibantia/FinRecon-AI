"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Notice } from "@/components/ui";
import type { RunSummary } from "@/lib/types";

export default function StartRunForm() {
  const router = useRouter();
  const [sourceSet, setSourceSet] = useState("DASHBOARD");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [summary, setSummary] = useState<RunSummary | null>(null);

  async function start(event: React.FormEvent) {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const res = await fetch(`/api/reconcile?sourceSet=${encodeURIComponent(sourceSet)}`, {
        method: "POST",
      });
      const body = await res.json();
      if (!res.ok) {
        setError(body.message ?? body.error ?? `Run failed (HTTP ${res.status})`);
        return;
      }
      setSummary(body as RunSummary);
    } catch {
      setError("Reconciliation service is unreachable. No run was started.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="card">
      <h2>Start a reconciliation run</h2>
      <form onSubmit={start}>
        <div className="row">
          <label className="field">
            Source set
            <input value={sourceSet} onChange={(e) => setSourceSet(e.target.value)} required />
          </label>
          <button type="submit" disabled={pending}>
            {pending ? "Running…" : "Run reconciliation"}
          </button>
        </div>
      </form>
      {error && (
        <Notice kind="error" title="Run failed">
          {error}
        </Notice>
      )}
      {summary && (
        <div>
          <p>
            Run <strong>{summary.runId}</strong>: {summary.matched} matched,{" "}
            {summary.mismatched} mismatched of {summary.total} (rule {summary.ruleVersion}).
          </p>
          <button type="button" onClick={() => router.push(`/runs/${summary.runId}`)}>
            Open results
          </button>
        </div>
      )}
    </div>
  );
}
