**Who to give what, in what order, and how to start**

Use this document during the actual build. The full master document is
the specification; this file is the execution checklist.

# 1. The Order

| **Order** | **Agent**      | **Phase** | **Give Them**                                         |
|-----------|----------------|-----------|-------------------------------------------------------|
| 1         | OpenCore       | P0        | Repository + development foundation                   |
| 2         | OpenCore       | P1        | Database/domain model                                 |
| 3         | OpenCore       | P2        | Ingestion + normalization                             |
| 4         | OpenCore       | P3        | Deterministic reconciliation                          |
| 5         | OpenCore       | P4        | Exception + case management                           |
| 6         | OpenCore       | P5        | Kafka + Redis (only now)                              |
| 7         | OMP            | P6        | ML dataset + baseline classifier                      |
| 8         | OMP            | P7        | RAG knowledge base                                    |
| 9         | OMP            | P8        | LangGraph AI investigator                             |
| 10        | OMP            | P9        | Fine-tuning only if justified                         |
| 11        | OpenCore       | P10       | Frontend + full product integration                   |
| 12        | OpenCore       | P11-P12   | Security, CI/CD, observability, cloud-ready packaging |
| 13        | OMP + OpenCore | P13       | Final ML/AI evaluation + end-to-end hardening         |

# 2. Start Here — Give This to OpenCore First

Do not give OMP the project first. OpenCore must establish the
financial-domain foundation.

> Read the FinRecon AI master project document completely before
> changing code.  
>   
> You are responsible for Phase P0 only: project foundation.  
> Do NOT implement ML, RAG, LangGraph, fine-tuning, Kafka, Kubernetes,
> or AWS in this phase.  
>   
> Tasks:  
> 1. Create the repository structure and module boundaries.  
> 2. Create local Docker Compose for PostgreSQL and required development
> services only.  
> 3. Create Java 21/Spring Boot core service skeleton, Python/FastAPI
> ai-service skeleton, and React/Next.js frontend skeleton.  
> 4. Add environment templates, README, health endpoints,
> formatting/linting conventions, and basic CI skeleton.  
> 5. Do not invent domain tables or business rules beyond the master
> document.  
>   
> Before finishing:  
> - everything starts locally  
> - basic health checks work  
> - tests/builds pass  
> - provide a handoff summary listing changed files, commands, tests,
> and remaining work.

# 3. After P0 — Continue OpenCore

Run P1 -\> P2 -\> P3 -\> P4 in order. Do not skip ahead.

> P1 prompt: Implement ONLY the canonical domain model, PostgreSQL
> migrations, constraints, indexes, seed data, and source fixtures from
> the master document.  
> Exit: three source records can be stored and queried reproducibly.  
>   
> P2 prompt: Implement ONLY ingestion + normalization for gateway,
> ledger, and settlement records. Support CSV and REST first. Add
> validation and idempotency.  
> Exit: repeated input does not duplicate records and canonical objects
> are produced.  
>   
> P3 prompt: Implement ONLY deterministic reconciliation. Add
> exact/fallback matching, amount/fee/net/currency/status/time checks,
> variance objects, exception taxonomy mapping, and rule versioning.  
> Exit: known fixtures always produce the expected result with automated
> tests.  
>   
> P4 prompt: Implement ONLY exception/case management, evidence,
> assignments, resolution actions, and audit history.  
> Exit: an analyst can trace a case from exception -\> evidence -\>
> source records -\> resolution.

# 4. Then Give the Project to OMP

OMP starts only after P4 is green and the core APIs/data model are
stable. OMP should build on the existing facts rather than redesign
them.

> P6 prompt: Build the exception-classification dataset from
> deterministic reconciliation outputs. Start with Logistic Regression,
> then benchmark XGBoost. Do not change reconciliation rules.  
> Exit: saved features/model + reproducible training script + macro
> F1/per-class metrics + leakage checks.  
>   
> P7 prompt: Build the RAG knowledge base using synthetic
> reconciliation/settlement SOPs, fee schedules, FX policy, escalation
> rules, and data dictionary. Use pgvector and preserve
> document/version/page metadata.  
> Exit: fixed evaluation questions retrieve relevant evidence and
> citations.  
>   
> P8 prompt: Build the LangGraph investigation workflow. Use tools to
> get payment/ledger/settlement facts, calculate variance, retrieve
> policy, find similar cases, and draft a resolution. Do not let the LLM
> invent financial facts.  
> Exit: end-to-end agent tests pass, citations are verified, and
> insufficient evidence causes manual-review output.  
>   
> P9 prompt: Only if the labelled dataset is sufficient, benchmark a
> small LoRA/QLoRA model against the existing baseline. Keep fine-tuning
> only if measured results justify it.  
> Exit: baseline-vs-fine-tuned report and decision.

# 5. Give It Back to OpenCore

> P10: Build the React/Next.js analyst dashboard and integrate the
> stable APIs. Implement queue, filters, case details, source
> comparison, evidence, AI investigation, feedback, resolution, and
> manager metrics.  
>   
> P11-P12: Add RBAC, secrets, masking, audit checks, structured logs,
> metrics, CI quality gates, Docker builds, and optional AWS packaging.
> Do not add infrastructure that does not improve the demonstrated
> system.

# 6. Final Joint Pass

OMP evaluates the ML/RAG/agent quality; OpenCore validates the complete
application, APIs, performance, reliability, and UI.

> P13 checklist:  
> - 5+ representative scenarios work end-to-end  
> - reconciliation metrics are measured  
> - classifier metrics are measured  
> - RAG retrieval/citation metrics are measured  
> - agent task-success metric is measured  
> - P95 latency is measured  
> - tests are green  
> - final README/demo is reproducible  
> - only real measured metrics are used in the resume

# 7. Rules for Using the Agents

- One phase per prompt. Do not say “build the entire project.”

- One owner per file/module at a time.

- Commit after every completed phase before handing off.

- Never let the AI layer silently replace deterministic financial logic.

- Never invent data, benchmarks, or performance claims.

- Every handoff must state changed files, tests run, commands, known
  gaps, and next phase.

- If an agent proposes a new technology, require a concrete reason and
  update the master document before adopting it.

# 8. The Simplest Mental Model

> OPENCORE builds the machine that knows the FACTS.
>
> OMP builds the intelligence that understands and EXPLAINS those facts.
>
> OPENCORE integrates everything into the PRODUCT.

First: OpenCore. Then: OMP. Then: OpenCore again. Finish together.
