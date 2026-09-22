"use client";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <html lang="en">
      <body>
        <main className="page">
          <div className="notice error" role="alert">
            <strong>Application error</strong>
            <div className="muted">{error.message || "Unexpected error."}</div>
            <div style={{ marginTop: 12 }}>
              <button type="button" onClick={() => reset()}>
                Try again
              </button>
            </div>
          </div>
        </main>
      </body>
    </html>
  );
}
