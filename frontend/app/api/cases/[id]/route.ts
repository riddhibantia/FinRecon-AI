import { buildCaseDetailUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return proxyJson(buildCaseDetailUrl(caseApiOrigin(), id));
}
