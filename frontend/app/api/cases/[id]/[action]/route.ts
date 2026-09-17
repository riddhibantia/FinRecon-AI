import { NextResponse } from "next/server";
import { buildCaseActionUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function POST(request: Request, { params }: { params: { id: string; action: string } }) {
  const target = buildCaseActionUrl(caseApiOrigin(), params.id, params.action);
  if (!target) {
    return NextResponse.json(
      { error: "UNKNOWN_ACTION", message: `Case action not supported: ${params.action}` },
      { status: 404 },
    );
  }
  return proxyJson(target, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: await request.text(),
  });
}
