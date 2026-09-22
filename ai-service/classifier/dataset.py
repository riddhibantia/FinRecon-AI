"""Synthetic source mutations labeled only by the actual Java P3 engine."""
import argparse
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import random
import subprocess
import tempfile
import zipfile

from classifier.schema import CLASSES, Snapshot

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_JAR = ROOT / "services/finrecon-app/build/libs/finrecon-app.jar"
ENGINE_SOURCE = ROOT / "services/finrecon-app/src/main/java/com/finrecon/reconciliation/reconcile/ReconciliationEngine.java"


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def json_bytes(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")


def scenario_families(groups=200, seed=17):
    """Variants of one simulated payment share a group, never a split boundary.

    Mutation names describe inputs; they are not assigned as class labels.
    Both isolated faults and multiple-fault precedence examples are included.
    """
    if groups < 1:
        raise ValueError("groups must be positive")
    rng = random.Random(seed)
    for group in range(groups):
        amount = Decimal(rng.randrange(1000, 10000000)) / 100
        fee = (amount * Decimal(str(rng.choice([0.01, 0.02, 0.05])))).quantize(Decimal("0.01"))
        net = amount - fee
        delta = Decimal(rng.randrange(1, 1000)) / 100
        currency = rng.choice(["INR", "USD", "EUR", "GBP"])
        status = rng.choice(["SUCCESS", "SETTLED", "POSTED"])
        time = datetime(2025, 1, 1, 10, tzinfo=timezone(timedelta(hours=5, minutes=30))) + timedelta(days=rng.randrange(500), hours=rng.randrange(12))
        base = {
            "payment": {"amount": str(amount), "currency": currency, "status": status, "event_time": time.isoformat()},
            "ledgers": [{"gross_amount": str(amount), "fee_amount": str(fee), "net_amount": str(net), "currency": currency,
                         "posting_status": status, "posted_at": (time + timedelta(hours=rng.randrange(1, 20))).isoformat()}],
            "settlements": [{"settled_amount": str(net), "fee_amount": str(fee), "currency": currency,
                             "settlement_status": status, "settlement_date": (time.date() + timedelta(days=rng.choice([0, 1, 2]))).isoformat()}],
            "context": {"settlement_window_days": 2},
        }
        for mutation in ("clean", "duplicate", "missing", "gross", "no_ledger", "ambiguous", "fee", "partial", "over", "fx", "status", "late", "fee_and_partial", "fx_and_status", "duplicate_and_fee", "missing_and_no_ledger"):
            source = deepcopy(base)
            l, s = source["ledgers"][0], source["settlements"][0]
            if mutation in ("fee", "fee_and_partial", "duplicate_and_fee"):
                s["fee_amount"] = str(fee + delta)
            if mutation in ("partial", "fee_and_partial"):
                s["settled_amount"] = str((net * Decimal(str(rng.choice([0.25, 0.5, 0.75])))).quantize(Decimal("0.01")))
            if mutation in ("duplicate", "duplicate_and_fee"):
                source["settlements"].append(deepcopy(s))
            if mutation in ("missing", "missing_and_no_ledger"):
                source["settlements"] = []
            if mutation == "gross":
                l["gross_amount"] = str(amount + delta)
                l["net_amount"] = s["settled_amount"] = str(net + delta)
            if mutation in ("no_ledger", "missing_and_no_ledger"):
                source["ledgers"] = []
            if mutation == "ambiguous":
                source["ledgers"].append(deepcopy(l))
            if mutation == "over":
                s["settled_amount"] = str(net + delta)
            if mutation in ("fx", "fx_and_status"):
                s["currency"] = "USD" if currency != "USD" else "INR"
            if mutation in ("status", "fx_and_status"):
                s["settlement_status"] = "FAILED"
            if mutation == "late":
                s["settlement_date"] = (time.date() + timedelta(days=rng.randrange(3, 15))).isoformat()
            yield {"group_id": f"payment-family-{group:06d}", "mutation": mutation,
                   "snapshot": Snapshot.model_validate(source).model_dump(mode="json")}


def label_snapshots(snapshots, jar=DEFAULT_JAR):
    jar = Path(jar).resolve()
    if not jar.is_file():
        raise FileNotFoundError(f"Build the unchanged P3 bootJar first: {jar}")
    bridge = Path(__file__).with_name("P3LabelBridge.java")
    with tempfile.TemporaryDirectory(prefix="finrecon-p3-") as directory:
        root = Path(directory)
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if name.startswith(("BOOT-INF/classes/", "BOOT-INF/lib/")) and not name.endswith("/"):
                    destination = (root / name).resolve()
                    if not destination.is_relative_to(root.resolve()):
                        raise ValueError("unsafe bootJar member path")
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    destination.write_bytes(archive.read(name))
        classpath = os.pathsep.join([str(root / "BOOT-INF/classes"), str(root / "BOOT-INF/lib/*"), str(root)])
        # Prefer the JAVA_HOME toolchain: the bootJar is class version 65
        # (Java 21), so an older javac earlier on PATH must not win.
        javac = "javac"
        java = "java"
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            suffix = ".exe" if os.name == "nt" else ""
            javac_candidate = Path(java_home) / "bin" / f"javac{suffix}"
            java_candidate = Path(java_home) / "bin" / f"java{suffix}"
            if javac_candidate.is_file():
                javac = str(javac_candidate)
            if java_candidate.is_file():
                java = str(java_candidate)
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", classpath, "-d", str(root), str(bridge)], check=True, capture_output=True, text=True)
        completed = subprocess.run([java, "-cp", classpath, "P3LabelBridge"],
            input="\n".join(json_bytes(source).decode() for source in snapshots) + "\n",
            check=True, capture_output=True, text=True, encoding="utf-8")
        return [json.loads(line) for line in completed.stdout.splitlines()]


def generate_dataset(output, groups=200, seed=17, jar=DEFAULT_JAR):
    if groups < 20:
        raise ValueError("at least 20 payment families required for grouped evaluation")
    scenarios = list(scenario_families(groups, seed))
    outcomes = label_snapshots([row["snapshot"] for row in scenarios], jar)
    if len(outcomes) != len(scenarios):
        raise ValueError("P3 bridge returned an incomplete label set")
    group_names = sorted({row["group_id"] for row in scenarios})
    random.Random(seed).shuffle(group_names)
    train_end, validation_end = int(groups * .7), int(groups * .85)
    partitions = {name: "train" if i < train_end else "validation" if i < validation_end else "test" for i, name in enumerate(group_names)}
    rows, matched = [], 0
    for scenario, outcome in zip(scenarios, outcomes, strict=True):
        if outcome["match_status"] == "MATCHED":
            matched += 1
            continue
        if outcome["match_status"] != "MISMATCHED" or outcome["label"] not in CLASSES:
            raise ValueError(f"Unsupported P3 outcome: {outcome}")
        rows.append(scenario | {"label": outcome["label"], "split": partitions[scenario["group_id"]]})
    if {row["label"] for row in rows} != set(CLASSES):
        raise ValueError("P3-generated data does not cover all nine taxonomy classes")
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(b"".join(json_bytes(row) + b"\n" for row in rows))
    manifest = {
        "format": "p6-dataset-v1", "seed": seed, "groups": groups, "rows": len(rows), "matched_excluded": matched,
        "dataset_sha256": digest(output), "label_authority": "unchanged ReconciliationEngine.reconcile",
        "bootjar_sha256": digest(jar), "engine_source_sha256": digest(ENGINE_SOURCE),
        "bridge_sha256": digest(Path(__file__).with_name("P3LabelBridge.java")), "generator_sha256": digest(__file__),
        "rule_versions": sorted({row["rule_version"] for row in outcomes}),
        "window_days": sorted({row["settlement_window_days"] for row in outcomes}),
        "split": "seeded payment-family shuffle; 70% train, 15% validation, 15% test; all mutations grouped",
        "limitations": "Synthetic mutations of P3 fixtures; not real operational accuracy or novel-fault generalization.",
    }
    output.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("artifacts/p6/dataset.jsonl"))
    parser.add_argument("--groups", type=int, default=200)
    parser.add_argument("--seed", type=int, default=17)
    parser.add_argument("--jar", type=Path, default=DEFAULT_JAR)
    args = parser.parse_args()
    print(json.dumps(generate_dataset(args.output, args.groups, args.seed, args.jar), indent=2))
