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

## ⚠ Action required — leaked credentials (treat as COMPROMISED)
A PostgreSQL password and a Gmail App Password were previously committed in
`application.yml`. They are **not reproduced here** (re-publishing a secret in the
repo defeats the purpose). They remain recoverable in git history until purged.

**Status: the working tree is clean, but git HISTORY is not yet purged and the
credentials are NOT yet confirmed rotated.** A human with repo + Google/DB access
must complete the steps below — see `scripts/purge-secrets.sh` for the exact
history-rewrite commands.

Do this now:
1. **Rotate the DB password** in PostgreSQL; update your `application-local.yml` / `DB_PASSWORD` env / Secrets Manager.
2. **Revoke the old Gmail App Password** (Google Account → Security → App passwords) and issue a new one.
3. **Purge from git history** across all three repos (values live in past commits even after the working tree was cleaned):
   ```bash
   ./scripts/purge-secrets.sh   # wraps git filter-repo; force-pushes; see the script header
   ```
4. After the purge + force-push, have **every** collaborator delete and re-clone (old clones still carry the secrets).
5. Rotate **again** after the purge — any value exposed during the window must not be reused.

## CI gate
A secret-scanning step (gitleaks/trufflehog) should block any PR that reintroduces
a credential literal. See `.github/workflows/` (added in P0-9).
