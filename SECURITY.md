# Security — Secrets Handling

## Configuration policy
- `application.yml` (committed) contains **only** non-secret, safe defaults
  (`postgres`/`test` for local Docker/LocalStack). It uses `${ENV:default}`
  placeholders so real values are supplied by the environment.
- Real secrets live in **environment variables** or `application-local.yml`
  (git-ignored) — never in a committed file.
- `application-local.yml.example` is a copy-me template.

### Required environment variables (production)
| Variable | Purpose |
|----------|---------|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP (mail is disabled if unset) |
| `AWS_ACCESS_KEY`, `AWS_SECRET_KEY` | S3 / object storage |

## ⚠ Action required — rotate leaked credentials
The following were previously committed in `application.yml` and must be treated
as **compromised**:
- the PostgreSQL password (`eta8827`)
- the Gmail App Password (`ttzjwfsuwunfmexb`)

Do this now:
1. **Rotate the DB password** in PostgreSQL and update your `application-local.yml` / env.
2. **Revoke the Gmail App Password** (Google Account → Security → App passwords) and issue a new one.
3. **Purge from git history** (the values live in past commits even after this change):
   ```bash
   git filter-repo --path lims-core-service-app/src/main/resources/application.yml --invert-paths
   # or use BFG; then force-push and have all clones re-clone
   ```
4. After purge, rotate again (a value seen during the window should not be reused).

## CI gate
A secret-scanning step (gitleaks/trufflehog) should block any PR that reintroduces
a credential literal. See `.github/workflows/` (added in P0-9).
