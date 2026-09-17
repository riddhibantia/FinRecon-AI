import { aiApiOrigin, buildInvestigateUrl } from "@/lib/backend";
import { proxyJson } from "@/lib/proxy";

// Read-only AI access: the dashboard may request investigations, never
// training, persistence, or case mutation through the AI service.
export async function POST(request: Request) {
  return proxyJson(buildInvestigateUrl(aiApiOrigin()), {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: await request.text(),
  });
}
