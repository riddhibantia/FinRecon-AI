import Link from "next/link";

export default function NotFound() {
  return (
    <div className="card" role="status">
      <h1>Not found</h1>
      <p className="muted">This page does not exist or was moved.</p>
      <p>
        <Link href="/">Back to dashboard</Link> · <Link href="/cases">Exception queue</Link>
      </p>
    </div>
  );
}
