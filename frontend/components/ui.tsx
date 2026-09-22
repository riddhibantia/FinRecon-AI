import { statusTone } from "@/lib/format";

export function Badge({ status }: { status: string | null | undefined }) {
  // Nike DESIGN.md: pill, flat, text signal only (sale/success) — never bg color.
  // Text label itself carries meaning, so status is never color-only.
  return (
    <span className={`badge ${statusTone(status)}`} aria-label={`status ${status ?? "unknown"}`}>
      {status ?? "—"}
    </span>
  );
}

export function Notice({
  kind,
  title,
  children,
}: {
  kind: "error" | "warn" | "info";
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className={`notice ${kind}`} role={kind === "error" ? "alert" : "status"}>
      <strong>{title}</strong>
      <div>{children}</div>
    </div>
  );
}
