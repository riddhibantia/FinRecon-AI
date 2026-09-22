import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  Circle,
  CircleAlert,
  CircleCheck,
  CircleDot,
  Clock,
} from "lucide-react";
import { statusTone } from "@/lib/format";

const TONE_ICONS = {
  open: CircleDot,
  working: Clock,
  done: CircleCheck,
  bad: CircleAlert,
  neutral: Circle,
} as const;

export function Badge({ status }: { status: string | null | undefined }) {
  // Nike DESIGN.md: pill, flat, text signal only (sale/success) — never bg color.
  // Text label itself carries meaning, so status is never color-only.
  // Icon is decorative (aria-hidden) and always paired with the text label.
  const tone = statusTone(status);
  const Icon = TONE_ICONS[tone];
  return (
    <span
      className={`badge ${tone}`}
      aria-label={`status ${status ?? "unknown"}`}
    >
      <Icon className="badge-icon" size={12} aria-hidden="true" />
      {status ?? "—"}
    </span>
  );
}

function severityLevel(severity: string | null | undefined): string {
  return (severity ?? "").toUpperCase();
}

export function Severity({ severity }: { severity: string | null | undefined }) {
  const level = severityLevel(severity);
  let Icon = Circle;
  let tone = "neutral";
  if (level === "CRITICAL" || level === "HIGH" || level === "BLOCKER") {
    Icon = CircleAlert;
    tone = "bad";
  } else if (level === "MEDIUM" || level === "MODERATE") {
    Icon = AlertTriangle;
    tone = "working";
  } else if (level === "LOW" || level === "INFO") {
    Icon = ArrowDown;
    tone = "done";
  } else if (level === "RESOLVED" || level === "CLOSED") {
    Icon = CircleCheck;
    tone = "done";
  } else if (level === "OPEN" || level === "NEW") {
    Icon = ArrowUp;
    tone = "open";
  }
  return (
    <span className={`badge ${tone}`} aria-label={`severity ${severity ?? "unknown"}`}>
      <Icon className="badge-icon" size={12} aria-hidden="true" />
      {severity ?? "—"}
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
  const Icon = kind === "error" ? CircleAlert : kind === "warn" ? AlertTriangle : CheckCircle2;
  return (
    <div className={`notice ${kind}`} role={kind === "error" ? "alert" : "status"}>
      <strong className="notice-title">
        <Icon className="badge-icon" size={14} aria-hidden="true" />
        {title}
      </strong>
      <div>{children}</div>
    </div>
  );
}
