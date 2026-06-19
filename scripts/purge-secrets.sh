#!/usr/bin/env bash
# =====================================================================
# purge-secrets.sh — remove the leaked credentials from git HISTORY.
#
#   ⚠ DESTRUCTIVE & IRREVERSIBLE. It rewrites every commit and force-pushes.
#   ⚠ A HUMAN must run this — it is intentionally NOT run by tooling/CI.
#
# What it does:
#   1. Verifies git-filter-repo is installed.
#   2. Rewrites history to scrub the secret VALUES everywhere they appear
#      (so even old blobs of application.yml no longer contain them).
#   3. Leaves the force-push to you (run the printed command after verifying).
#
# Prerequisites:
#   - pip install git-filter-repo   (https://github.com/newren/git-filter-repo)
#   - A clean working tree, and you have ALREADY rotated the credentials.
#   - Run once PER repo if a secret ever touched more than this one.
#
# Usage:
#   ./scripts/purge-secrets.sh            # dry-run: shows what will happen
#   ./scripts/purge-secrets.sh --yes      # actually rewrite history (local only)
#   # then, after verifying:  git push --force-with-lease --all && git push --force-with-lease --tags
# =====================================================================
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "ERROR: git-filter-repo not found. Install with: pip install git-filter-repo" >&2
  exit 1
fi

# The leaked literals. Stored ONLY here, in a file you delete after the purge.
# Replace these with the actual old values before running (kept out of the repo).
REPLACEMENTS_FILE="$(mktemp)"
cat > "$REPLACEMENTS_FILE" <<'EOF'
# old-secret==>redacted     (edit the left side to the REAL old values, then run)
eta8827==>REDACTED_DB_PASSWORD
ttzjwfsuwunfmexb==>REDACTED_MAIL_APP_PASSWORD
EOF

echo "Repo:            $REPO_ROOT"
echo "Replacements:    $REPLACEMENTS_FILE"
echo
echo "Commits still containing the DB password literal:"
git log --oneline -S 'eta8827' || true
echo

if [[ "${1:-}" != "--yes" ]]; then
  echo "DRY RUN. Re-run with --yes to rewrite history locally."
  echo "After rewrite, verify with:  git log --oneline -S 'eta8827'  (should be empty)"
  rm -f "$REPLACEMENTS_FILE"
  exit 0
fi

echo "Rewriting history (this changes every commit hash)..."
git filter-repo --replace-text "$REPLACEMENTS_FILE" --force
rm -f "$REPLACEMENTS_FILE"

echo
echo "DONE locally. Now VERIFY, then force-push:"
echo "  git log --oneline -S 'eta8827'        # expect: empty"
echo "  git remote add origin <url>           # filter-repo drops remotes by design"
echo "  git push --force-with-lease --all"
echo "  git push --force-with-lease --tags"
echo
echo "Then tell EVERY collaborator to delete their clone and re-clone."
echo "Finally, ROTATE the credentials again."
