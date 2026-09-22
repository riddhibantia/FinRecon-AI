"use client";

import { AnimatedCounter } from "@/components/ui/animated-counter";

// KPI stat value with an odometer roll (Rare UI). Falls back to verbatim
// text when the backend value is not numeric — never guesses a number.
export function StatCounter({
  value,
  decimals,
  prefix,
  suffix,
  grouping = "western",
}: {
  value: string | number;
  decimals?: number;
  prefix?: React.ReactNode;
  suffix?: React.ReactNode;
  grouping?: "western" | "indian";
}) {
  const raw = String(value);
  const n = Number(raw);
  if (!Number.isFinite(n)) return <>{raw}</>;
  const inferred =
    decimals ??
    (raw.includes(".") ? Math.min(15, raw.split(".")[1].length) : 0);
  return (
    <AnimatedCounter
      value={n}
      decimals={inferred}
      prefix={prefix}
      suffix={suffix}
      grouping={grouping}
      className="stat-value"
    />
  );
}
