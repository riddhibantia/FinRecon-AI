import { NextResponse } from "next/server";

// P0 health contract. No dashboard logic.
export function GET() {
  return NextResponse.json({ status: "UP", service: "frontend" });
}
