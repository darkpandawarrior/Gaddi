#!/usr/bin/env bash
# provision.sh — swap __PROVISION_<KEY>__ sentinels for real values, or check that none leaked.
#
# WHY SENTINELS: every capability that needs provisioning ships with a greppable fake value and a
# capability flag that returns NOT_CONFIGURED while the fake is in place. The app therefore refuses
# to draw a working-looking Apple Pay / Play Games button instead of drawing one that fails at the
# worst moment. `check` is what CI runs; `apply` is what a human runs once, locally.
#
# Usage:
#   provision.sh check [root]              exit 1 if any sentinel remains (default: CI gate)
#   provision.sh list                      show every registered value and where to get it
#   provision.sh apply <values.env> [root] replace sentinels, validating each against its regex
#   provision.sh revert <values.env> [root] put the sentinels back (run BEFORE committing)
#
# values.env is KEY=value lines. IT MUST NEVER BE COMMITTED — keep it outside the repo or gitignored.

set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REG="$HERE/placeholders.json"
CMD="${1:-check}"

command -v jq >/dev/null || { echo "provision.sh needs jq (brew install jq)" >&2; exit 2; }
[ -f "$REG" ] || { echo "registry not found: $REG" >&2; exit 2; }

keys()     { jq -r '.values[].key' "$REG"; }
sentinel() { jq -r --arg k "$1" '.values[]|select(.key==$k)|.sentinel' "$REG"; }
regex()    { jq -r --arg k "$1" '.values[]|select(.key==$k)|.realFormat' "$REG"; }

case "$CMD" in
  list)
    jq -r '.values[] | "\(.key)\n    sentinel : \(.sentinel)\n    where    : \(.where)\n    costs    : \(.costs)\n    blocks   : \(.blocks)\n    \(if .note then "note     : " + .note else "" end)\n"' "$REG"
    ;;

  check)
    ROOT="${2:-.}"; found=0
    while read -r k; do
      s="$(sentinel "$k")"
      # -F: sentinels are literal. Skip VCS and build output.
      hits="$(grep -rlF "$s" "$ROOT" --exclude-dir=.git --exclude-dir=build --exclude-dir=node_modules 2>/dev/null)"
      if [ -n "$hits" ]; then
        found=1
        echo "UNPROVISIONED  $k"
        echo "$hits" | sed 's/^/                 /'
      fi
    done < <(keys)
    if [ "$found" -eq 0 ]; then echo "provision: no sentinels remain under $ROOT"; exit 0; fi
    echo
    echo "The capability flags for the above return NOT_CONFIGURED, so those features are inert."
    echo "That is the intended state until you provision. Run 'provision.sh list' for what each needs."
    exit 1
    ;;

  apply|revert)
    VALS="${2:-}"; ROOT="${3:-.}"
    [ -f "$VALS" ] || { echo "usage: provision.sh $CMD <values.env> [root]" >&2; exit 2; }
    # shellcheck disable=SC1090
    set -a; . "$VALS"; set +a
    n=0
    while read -r k; do
      s="$(sentinel "$k")"; re="$(regex "$k")"; v="${!k:-}"
      [ -n "$v" ] || { echo "skip $k (not set in $VALS)"; continue; }
      if ! printf '%s' "$v" | grep -qE "$re"; then
        echo "REFUSING $k: '$v' does not match $re" >&2
        echo "         A malformed value fails at runtime, not at build time — fix it here." >&2
        exit 1
      fi
      if [ "$CMD" = apply ]; then from="$s"; to="$v"; else from="$v"; to="$s"; fi
      files="$(grep -rlF "$from" "$ROOT" --exclude-dir=.git --exclude-dir=build 2>/dev/null)"
      [ -n "$files" ] || continue
      # macOS/BSD sed needs the empty -i arg; this script targets darwin.
      echo "$files" | while read -r f; do LC_ALL=C sed -i '' "s|$from|$to|g" "$f"; done
      c=$(echo "$files" | wc -l | tr -d ' '); n=$((n+c))
      echo "$CMD $k -> $c file(s)"
    done < <(keys)
    echo "provision: $CMD touched $n file(s)"
    # NB: must be an if, not `[ ... ] && cat`. As the last statement of the branch, a failed
    # test would become the script's exit code and make every successful `revert` look like a
    # failure to CI. Caught by the round-trip self-test.
    if [ "$CMD" = apply ]; then cat <<'WARN'

  Real values are now in your working tree. Do NOT commit them.
  Run 'provision.sh revert <values.env>' before `git commit`, or keep these builds local only.
WARN
    fi
    ;;

  *) sed -n '2,20p' "$0"; exit 2 ;;
esac
