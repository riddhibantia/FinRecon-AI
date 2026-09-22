# Dashboard screenshots (Phase 7)

Live captures of the redesigned dashboard against seeded demo data.
PNGs are **not** committed — they are produced by the `screenshots` CI job
and attached to each run as the `dashboard-screenshots` artifact, so the
images always match the code under test.

## What is captured

| File | Page | Notes |
|---|---|---|
| `home.png` | `/` | Hero, KPI stat cards, category chart, service status |
| `cases.png` | `/cases` | Queue filters, search, sort, pagination |
| `case-detail.png` | `/cases/[id]` | Tabs, sticky rail, evidence diff emphasis (first queue case) |
| `metrics.png` | `/metrics` | Stat groups, category/severity/ageing charts |
| `metrics-table.png` | `/metrics?view=table` | Table fallback for every chart |

Seed data comes from `python scripts/demo.py` (six scenarios; cases end
resolved with analyst follow-up recorded, so the queue, activity tab, and
metrics all have content).

## Regenerate locally

Needs Docker (postgres) plus the usual local stack:

```powershell
Copy-Item .env.template .env
docker compose up postgres
gradle :services:finrecon-app:bootRun
cd ai-service; python -m uvicorn app:app --port 8000
python scripts/demo.py
cd frontend; npm ci; npm run build; npm run start -- --port 3000
```

Then capture with headless Chrome (tall viewport approximates full page):

```powershell
$shot = { param($f, $u) & 'C:\Program Files\Google\Chrome\Application\chrome.exe' `
  --headless=new --no-sandbox --disable-gpu --hide-scrollbars `
  --window-size=1440,2400 --screenshot=$f $u }
& $shot docs/screenshots/home.png http://localhost:3000/
& $shot docs/screenshots/cases.png http://localhost:3000/cases
& $shot docs/screenshots/metrics.png http://localhost:3000/metrics
& $shot docs/screenshots/metrics-table.png 'http://localhost:3000/metrics?view=table'
```

## Status

Screenshots job added to `.github/workflows/ci.yml`; first captures land
as artifacts on the next green run. Reviewed from there, not from memory.
