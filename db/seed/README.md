# db/seed - P1 minimal fixtures

`p1_minimal_seed.sql`: one payment + one ledger entry + one settlement
(master #3 example). Synthetic data only. Apply with
`psql "$DATABASE_URL" -f db/seed/p1_minimal_seed.sql`.
