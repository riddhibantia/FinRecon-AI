"use client";

import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export interface CategoryDatum {
  category: string;
  count: number;
}

// Counts are passed through verbatim; this component draws them, never computes.
export function CategoryBarChart({ data }: { data: CategoryDatum[] }) {
  const ariaLabel =
    data.length === 0
      ? "No cases by category"
      : `Cases by category: ${data.map((d) => `${d.category} ${d.count}`).join(", ")}`;

  return (
    <div className="chart-wrap" role="img" aria-label={ariaLabel}>
      <ResponsiveContainer width="100%" height={Math.max(180, data.length * 44)}>
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 4, right: 48, bottom: 4, left: 4 }}
        >
          <XAxis type="number" hide />
          <YAxis
            type="category"
            dataKey="category"
            width={168}
            tick={{ fontSize: 13, fill: "var(--mute)" }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            cursor={{ fill: "var(--soft-cloud)" }}
            contentStyle={{
              background: "var(--canvas)",
              border: "1px solid var(--hairline)",
              borderRadius: 0,
              fontSize: 14,
            }}
          />
          <Bar
            dataKey="count"
            fill="var(--ink)"
            label={{ position: "right", fontSize: 13, fill: "var(--ink)" }}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
