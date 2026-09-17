import StartRunForm from "@/components/StartRunForm";

export default function RunsPage() {
  return (
    <>
      <h1>Reconciliation runs</h1>
      <p className="muted">
        Each run applies the deterministic engine (rule version is recorded with the run)
        over all known payments. Same inputs always produce the same results.
      </p>
      <StartRunForm />
    </>
  );
}
