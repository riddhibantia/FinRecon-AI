import { buildCaseDetailUrl, caseApiOrigin } from "@/lib/backend";
import { display, shortId } from "@/lib/format";
import { Badge, Notice } from "@/components/ui";
import AiPanel from "@/components/AiPanel";
import CaseActions from "@/components/CaseActions";
import FeedbackForm from "@/components/FeedbackForm";
import type { CaseDetail } from "@/lib/types";

export const dynamic = "force-dynamic";

async function loadCase(id: string): Promise<CaseDetail | null> {
  try {
    const res = await fetch(buildCaseDetailUrl(caseApiOrigin(), id), { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as CaseDetail;
  } catch {
    return null;
  }
}

export default async function CaseDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const detail = await loadCase(id);
  if (!detail) {
    return (
      <Notice kind="error" title="Case unavailable">
        Case {id} could not be loaded. The exception service may be unreachable or
        the case may not exist.
      </Notice>
    );
  }

  return (
    <>
      <h1>
        Case {shortId(detail.exceptionId)} · {display(detail.externalTxnId)}
      </h1>
      <div className="card">
        <p>
          <Badge status={detail.status} /> <strong>{display(detail.category)}</strong> ·
          severity {display(detail.severity)} · assignee {display(detail.assignedTo)}
        </p>
        <p className="muted">
          Opened {display(detail.createdAt)}
          {detail.resolvedAt ? ` · resolved ${detail.resolvedAt}` : ""}
        </p>
      </div>

      <div className="card">
        <h2>Reconciliation discrepancy</h2>
        <p>
          Result <Badge status={detail.matchStatus} /> · mismatch{" "}
          <strong>{display(detail.mismatchType)}</strong> · difference{" "}
          <strong className="diff-val">{display(detail.amountDifference)}</strong> · rule
          version {display(detail.ruleVersion)}
        </p>
        <p className="muted">
          Established by the deterministic engine. The dashboard repeats it verbatim.
        </p>
      </div>

      <div className="card">
        <h2>Evidence ({detail.evidence.length})</h2>
        {detail.evidence.length === 0 ? (
          <p className="muted">No evidence rows recorded for this case.</p>
        ) : (
          <table className="grid">
            <caption className="muted">Evidence</caption>
            <thead>
              <tr>
                <th scope="col">Source</th>
                <th scope="col">Field</th>
                <th scope="col">Expected</th>
                <th scope="col">Observed</th>
              </tr>
            </thead>
            <tbody>
              {detail.evidence.map((e, i) => (
                <tr key={i}>
                  <td>
                    {display(e.sourceType)}
                    <br />
                    <span className="muted">{shortId(e.sourceRecordId)}</span>
                  </td>
                  <td>{display(e.fieldName)}</td>
                  <td>{display(e.expectedValue)}</td>
                  <td>{display(e.observedValue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>Source records ({detail.sources.length})</h2>
        {detail.sources.length === 0 ? (
          <p className="muted">No source records attached.</p>
        ) : (
          <table className="grid">
            <caption className="muted">Source records</caption>
            <thead>
              <tr>
                <th scope="col">System</th>
                <th scope="col">Record</th>
                <th scope="col">Summary</th>
              </tr>
            </thead>
            <tbody>
              {detail.sources.map((s, i) => (
                <tr key={i}>
                  <td>{display(s.sourceType)}</td>
                  <td className="muted">{shortId(s.recordId)}</td>
                  <td>{display(s.summary)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <AiPanel exceptionId={detail.exceptionId} />
      <CaseActions exceptionId={detail.exceptionId} status={detail.status} />
      <FeedbackForm caseId={detail.exceptionId} currentCategory={detail.category} />

      <div className="card">
        <h2>Analyst corrections ({detail.feedback.length})</h2>
        {detail.feedback.length === 0 ? (
          <p className="muted">No analyst corrections recorded.</p>
        ) : (
          <table className="grid">
            <caption className="muted">Analyst feedback</caption>
            <thead>
              <tr>
                <th scope="col">Analyst</th>
                <th scope="col">Original</th>
                <th scope="col">Corrected</th>
                <th scope="col">Reason</th>
                <th scope="col">At</th>
              </tr>
            </thead>
            <tbody>
              {detail.feedback.map((f) => (
                <tr key={f.feedbackId}>
                  <td>{display(f.analystId)}</td>
                  <td>{display(f.originalValue)}</td>
                  <td>{display(f.correctedValue)}</td>
                  <td>{display(f.reason)}</td>
                  <td className="muted">{display(f.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>Resolution actions ({detail.caseActions.length})</h2>
        {detail.caseActions.length === 0 ? (
          <p className="muted">No actions recorded yet.</p>
        ) : (
          <table className="grid">
            <caption className="muted">Resolution actions</caption>
            <thead>
              <tr>
                <th scope="col">Action</th>
                <th scope="col">Actor</th>
                <th scope="col">Notes</th>
                <th scope="col">At</th>
              </tr>
            </thead>
            <tbody>
              {detail.caseActions.map((a, i) => (
                <tr key={i}>
                  <td>{display(a.actionType)}</td>
                  <td>
                    {display(a.actorType)} {display(a.actorId)}
                  </td>
                  <td>{display(a.notes)}</td>
                  <td className="muted">{display(a.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="card">
        <h2>Audit trail ({detail.auditTrail.length})</h2>
        {detail.auditTrail.length === 0 ? (
          <p className="muted">No audit entries.</p>
        ) : (
          <table className="grid">
            <caption className="muted">Audit trail</caption>
            <thead>
              <tr>
                <th scope="col">Actor</th>
                <th scope="col">Action</th>
                <th scope="col">At</th>
              </tr>
            </thead>
            <tbody>
              {detail.auditTrail.map((a, i) => (
                <tr key={i}>
                  <td>
                    {display(a.actorType)} {display(a.actorId)}
                  </td>
                  <td>{display(a.action)}</td>
                  <td className="muted">{display(a.timestamp)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
