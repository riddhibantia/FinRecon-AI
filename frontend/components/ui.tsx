import { statusTone } from "@/lib/format";

export function Badge({ status }: { status: string | null | undefined }) {
  return <span className={`badge ${statusTone(status)}`}>{status ?? "—"}</span>;
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
    <div className={`notice ${kind}`}>
      <strong>{title}</strong>
      <div>{children}</div>
    </div>
  );
}
