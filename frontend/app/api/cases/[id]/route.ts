import { buildCaseDetailUrl, caseApiOrigin } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

export async function GET(_request: Request, { params }: { params: { id: string } }) {
  return proxyJson(buildCaseDetailUrl(caseApiOrigin(), params.id));
}
