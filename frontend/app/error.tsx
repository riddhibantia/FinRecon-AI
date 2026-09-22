"use client";

export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="notice error" role="alert">
      <strong>Something went wrong</strong>
      <div className="muted">{error.message || "Unexpected error."}</div>
      <div style={{ marginTop: 12 }}>
        <button type="button" onClick={() => reset()}>
          Try again
        </button>
      </div>
    </div>
  );
}
