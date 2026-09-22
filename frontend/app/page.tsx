import Link from "next/link";
import { serviceHealthTargets } from "@/lib/backend";
import { Badge, Notice } from "@/components/ui";

export const dynamic = "force-dynamic";

interface HealthState {
  name: string;
  state: string;
  detail: string;
}

async function checkHealth(): Promise<HealthState[]> {
  const checks = await Promise.all(
    serviceHealthTargets().map(async (target) => {
      try {
        const res = await fetch(target.url, {
          cache: "no-store",
          signal: AbortSignal.timeout(5000),
        });
        if (!res.ok) return { name: target.name, state: "DOWN", detail: `HTTP ${res.status}` };
        const body = (await res.json()) as { status?: string };
        return {
          name: target.name,
          state: body.status ?? "UNKNOWN",
          // Never leak internal origin to the browser; the proxy owns routing.
          detail: "configured backend endpoint",
        };
      } catch {
        return { name: target.name, state: "UNREACHABLE", detail: "not reachable" };
      }
    }),
  );
  return checks;
}

export default async function Home() {
  const health = await checkHealth();
  const down = health.filter((h) => h.state !== "UP");

  return (
    <>
      <section className="campaign-tile" aria-label="FinRecon AI overview">
        <h1 className="campaign-title">
          Reconcile
          <br />
          Explain
          <br />
          Resolve
        </h1>
        <p className="campaign-sub">
          Do the financial systems agree about what happened to a payment — and if not, what
          explains the discrepancy? Facts below come from the backend services; this page
          computes nothing financial.
        </p>
        <div className="pill-row">
          <Link href="/runs" className="btn btn-on-image">
            Start a run
          </Link>
          <Link href="/cases" className="btn btn-on-image">
            Work the queue
          </Link>
        </div>
      </section>

      {down.length > 0 && (
        <Notice kind="warn" title="Some backends are unreachable">
          Start the stack first (see README). The dashboard shows stored facts only — never
          guesses.
        </Notice>
      )}

      <div className="card">
        <h2>Service status</h2>
        <table className="grid">
          <caption className="muted">Live reachability of backend services</caption>
          <thead>
            <tr>
              <th scope="col">Service</th>
              <th scope="col">Status</th>
              <th scope="col">Endpoint</th>
            </tr>
          </thead>
          <tbody>
            {health.map((h) => (
              <tr key={h.name}>
                <td>{h.name}</td>
                <td>
                  <Badge status={h.state} />
                </td>
                <td className="muted">{h.detail}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2>Analyst workflow</h2>
        <ol>
          <li>
            <Link href="/runs">Start a reconciliation run</Link> and open its results.
          </li>
          <li>
            <Link href="/cases">Work the exception queue</Link>: filter, inspect evidence,
            request a cited AI investigation, then assign, resolve, or escalate.
          </li>
        </ol>
        <p className="muted">
          AI investigations always end at human review. Resolving or escalating a case
          happens only through the case controls — never automatically.
        </p>
      </div>
    </>
  );
}
