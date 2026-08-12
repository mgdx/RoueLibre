#!/usr/bin/env bash
#
# Publishes the generated datasets to github.com/mgdx/RoueLibre-data, at the
# addresses the manifests already name. Run it once `gh auth status` answers.
#
#   bash publish_data.sh            # does it
#   bash publish_data.sh --dry-run  # says what it would do
#
# Re-runnable: an asset already uploaded is replaced (--clobber), so an
# interrupted upload is resumed by running the command again.
set -euo pipefail
cd /home/leo/AndroidStudioProjects/RoueLibre

REPO="mgdx/RoueLibre-data"
TAG="data-2026-08"
STAGE="data/release/$TAG"
DRY=0
[[ "${1:-}" == "--dry-run" ]] && DRY=1

run() {
  if [[ "$DRY" -eq 1 ]]; then echo "  would run: $*"; else "$@"; fi
}

command -v gh >/dev/null || { echo "gh is missing: sudo apt install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in: gh auth login" >&2; exit 1; }

echo "── Repository ──"
if gh repo view "$REPO" >/dev/null 2>&1; then
  echo "$REPO already exists"
else
  echo "Creating $REPO…"
  run gh repo create "$REPO" --public \
    --description "Offline datasets for the Roue Libre application: base map, routing graph and address index, one set per conurbation."
  # A release needs a commit to hang from, so the repository gets its README
  # first — which is also where the licences of the data have to be stated.
  work="$(mktemp -d)"
  cp docs/data-repo-README.md "$work/README.md"
  (
    cd "$work"
    git init -q -b main
    git add README.md
    git commit -q -m "Say what this repository holds, and under which licences"
    git remote add origin "git@github.com:$REPO.git"
    [[ "$DRY" -eq 1 ]] || git push -q -u origin main
  )
  rm -rf "$work"
fi

echo
echo "── Release $TAG ──"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "Release $TAG already there, its assets will be completed"
else
  run gh release create "$TAG" --repo "$REPO" \
    --title "Data of 12 August 2026 — 101 conurbations" \
    --notes-file docs/data-release-notes.md
fi

echo
echo "── Assets ──"
count="$(find "$STAGE" -type f | wc -l)"
size="$(du -sh "$STAGE" | cut -f1)"
echo "$count files, $size"
# Uploaded in batches: one gh call per file would cost a round trip each, one
# call for 442 would sit for an hour with nothing to show and no way back in.
find "$STAGE" -type f -print0 | sort -z | xargs -0 -n 20 \
  bash -c 'if [[ '"$DRY"' -eq 1 ]]; then echo "  would upload: $#"; else
             gh release upload '"$TAG"' --repo '"$REPO"' --clobber "$@" && echo "  uploaded $# files"; fi' _

echo
echo "── Check ──"
if [[ "$DRY" -eq 0 ]]; then
  # What the application will actually ask for, asked the same way.
  for probe in manifest-vlille.json catalogue.json; do
    code="$(curl -sSL -o /dev/null -w '%{http_code}' \
      "https://github.com/$REPO/releases/latest/download/$probe")"
    echo "  latest/download/$probe → HTTP $code"
  done
  url="$(/usr/bin/python3 -c "
import json;print(json.load(open('data/out/vlille/manifest.json'))['datasets'][0]['files'][0]['url'])")"
  echo "  $(basename "$url") → HTTP $(curl -sSL -o /dev/null -w '%{http_code}' "$url")"
fi
echo "Done."
