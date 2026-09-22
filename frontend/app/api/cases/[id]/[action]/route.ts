import { NextResponse } from "next/server";
import { buildCaseActionUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function POST(request: Request, { params }: { params: Promise<{ id: string; action: string }> }) {
  const { id, action } = await params;
  const target = buildCaseActionUrl(caseApiOrigin(), id, action);
  if (!target) {
    return NextResponse.json(
      { error: "UNKNOWN_ACTION", message: `Case action not supported: ${action}` },
      { status: 404 },
    );
  }
  return proxyJson(target, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: await request.text(),
  });
}
