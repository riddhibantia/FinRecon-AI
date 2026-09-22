"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Notice } from "@/components/ui";

async function postAction(
  exceptionId: string,
  action: string,
  body: Record<string, string>,
): Promise<{ ok: boolean; message: string }> {
  try {
    const res = await fetch(`/api/cases/${exceptionId}/${action}`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) {
      const data = await res.json().catch(() => ({}));
      return {
        ok: false,
        message: (data as { message?: string }).message ?? `Failed (HTTP ${res.status})`,
      };
    }
    return { ok: true, message: "" };
  } catch {
    return { ok: false, message: "Exception service is unreachable." };
  }
}

export default function CaseActions({
  exceptionId,
  status,
}: {
  exceptionId: string;
  status: string;
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [assignedTo, setAssignedTo] = useState("");
  const [resolveNotes, setResolveNotes] = useState("");
  const [resolveAction, setResolveAction] = useState("CONFIRMED");
  const [escalateNotes, setEscalateNotes] = useState("");

  async function run(action: string, body: Record<string, string>) {
    setPending(true);
    setError(null);
    const outcome = await postAction(exceptionId, action, body);
    setPending(false);
    if (!outcome.ok) {
      setError(outcome.message);
      return;
    }
    router.refresh();
  }

  const terminal = status === "RESOLVED" || status === "ESCALATED";

  return (
    <div className="card">
      <h2>Case actions</h2>
      {terminal ? (
        <p className="muted">This case is {status.toLowerCase()}; no further actions apply.</p>
      ) : (
        <>
          {status === "OPEN" && (
            <form
              aria-label="Assign case"
              onSubmit={(e) => {
                e.preventDefault();
                void run("assign", { assignedTo, actorId: assignedTo });
              }}
            >
              <div className="row">
                <label className="field" htmlFor="assign-to">
                  Assign to analyst
                  <input
                    id="assign-to"
                    value={assignedTo}
                    onChange={(e) => setAssignedTo(e.target.value)}
                    required
                    maxLength={128}
                    autoComplete="off"
                  />
                </label>
                <button type="submit" disabled={pending} aria-busy={pending}>
                  Assign
                </button>
              </div>
            </form>
          )}
          {status === "INVESTIGATING" && (
            <>
              <form
                aria-label="Resolve case"
                onSubmit={(e) => {
                  e.preventDefault();
                  void run("resolve", {
                    actionType: resolveAction,
                    actorType: "ANALYST",
                    actorId: assignedTo || "analyst",
                    notes: resolveNotes,
                  });
                }}
              >
                <div className="row">
                  <label className="field" htmlFor="resolve-action">
                    Resolution
                    <input
                      id="resolve-action"
                      value={resolveAction}
                      onChange={(e) => setResolveAction(e.target.value)}
                      required
                      maxLength={64}
                    />
                  </label>
                  <label className="field" htmlFor="resolve-notes">
                    Notes
                    <input
                      id="resolve-notes"
                      value={resolveNotes}
                      onChange={(e) => setResolveNotes(e.target.value)}
                      maxLength={500}
                    />
                  </label>
                  <button type="submit" disabled={pending} aria-busy={pending}>
                    Resolve
                  </button>
                </div>
              </form>
              <form
                aria-label="Escalate case"
                onSubmit={(e) => {
                  e.preventDefault();
                  void run("escalate", {
                    actorType: "ANALYST",
                    actorId: "analyst",
                    notes: escalateNotes,
                  });
                }}
              >
                <div className="row">
                  <label className="field" htmlFor="escalate-notes">
                    Escalation notes
                    <input
                      id="escalate-notes"
                      value={escalateNotes}
                      onChange={(e) => setEscalateNotes(e.target.value)}
                      maxLength={500}
                    />
                  </label>
                  <button type="submit" disabled={pending} aria-busy={pending} className="secondary">
                    Escalate
                  </button>
                </div>
              </form>
            </>
          )}
        </>
      )}
      {error && (
        <Notice kind="error" title="Action failed">
          {error}
        </Notice>
      )}
    </div>
  );
}
