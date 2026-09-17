// Pure presentational helpers. No imports, no fetch, no backend knowledge.
// The UI displays backend values verbatim; these only choose fallbacks.
export function display(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  return String(value);
}

export function formatMoney(
  value: string | null | undefined,
  currency?: string | null,
): string {
  if (value === null || value === undefined || value === "") return "—";
  return currency ? `${value} ${currency}` : String(value);
}

export function shortId(id: string | null | undefined): string {
  if (!id) return "—";
  return id.length > 8 ? id.slice(0, 8) : id;
}

export type StatusTone = "open" | "working" | "done" | "bad" | "neutral";

export function statusTone(status: string | null | undefined): StatusTone {
  switch ((status ?? "").toUpperCase()) {
    case "OPEN":
    case "MISSING_SETTLEMENT":
      return "open";
    case "INVESTIGATING":
    case "HUMAN_REVIEW":
      return "working";
    case "MATCHED":
    case "RESOLVED":
    case "COMPLETED":
    case "UP":
      return "done";
    case "MANUAL_REVIEW":
    case "ESCALATED":
    case "FAILED":
    case "MISMATCHED":
    case "DOWN":
      return "bad";
    default:
      return "neutral";
  }
}
