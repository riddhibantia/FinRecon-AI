// Structural types mirroring backend contracts (P2/P3/P4) and the P8
// investigation output. The UI never reshapes financial facts.
export interface CaseSummary {
  exceptionId: string;
  resultId: string;
  category: string;
  severity: string;
  status: string;
  assignedTo: string | null;
  externalTxnId: string;
  amountDifference: string | null;
  createdAt: string;
}

export interface EvidenceRow {
  sourceType: string;
  sourceRecordId: string;
  fieldName: string | null;
  expectedValue: string | null;
  observedValue: string | null;
}

export interface SourceRecord {
  sourceType: string;
  recordId: string;
  summary: string;
}

export interface CaseActionRow {
  actionType: string;
  actorType: string;
  actorId: string;
  notes: string | null;
  createdAt: string;
}

export interface AuditRow {
  actorType: string;
  actorId: string;
  action: string;
  timestamp: string;
}

export interface CaseDetail extends CaseSummary {
  resolvedAt: string | null;
  runId: string;
  ruleVersion: string;
  matchStatus: string;
  mismatchType: string | null;
  sources: SourceRecord[];
  evidence: EvidenceRow[];
  caseActions: CaseActionRow[];
  auditTrail: AuditRow[];
}

export interface RunSummary {
  runId: string;
  status: string;
  ruleVersion: string;
  total: number;
  matched: number;
  mismatched: number;
}

export interface ResultView {
  resultId: string;
  runId: string;
  paymentId: string;
  externalTxnId: string;
  matchStatus: string;
  mismatchType: string | null;
  amountDifference: string | null;
  createdAt: string;
}

export interface InvestigationCitation {
  document: string;
  version: string;
  section: string;
  page: number;
  excerpt: string;
  score: number;
}

export interface InvestigationEvidence {
  source_type: string;
  source_id: string;
  field: string | null;
  expected: string | number | null;
  observed: string | number | null;
}

export interface Investigation {
  exceptionId: string;
  root_cause: string | null;
  summary: string | null;
  recommended_action: string | null;
  confidence: number | null;
  confidence_reason: string | null;
  evidence: InvestigationEvidence[];
  citations: InvestigationCitation[];
  status: string;
  reasons: string[];
  draft: { action?: string; citations?: unknown } | null;
  human_approval_required: boolean;
}
