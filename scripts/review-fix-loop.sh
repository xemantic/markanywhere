#!/usr/bin/env bash
#
# Automates the "/code-review" → "fix + commit" cycle on a feature branch.
#
# Each step is a separate headless `claude -p` process, so every review starts
# from a fresh context and never sees the reasoning behind the previous fixes.
# The loop stops when the fixer finds no critical/major correctness finding in a
# review, when a round leaves uncommitted changes (build red), or at MAX_ROUNDS.
#
# Usage: scripts/review-fix-loop.sh [base-branch] [max-rounds]
#   env: PERMISSION_MODE (default acceptEdits), LOG_DIR (default: a temp dir)

set -euo pipefail

base=${1:-main}
max_rounds=${2:-5}
permission_mode=${PERMISSION_MODE:-acceptEdits}
log_dir=${LOG_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/review-fix-loop.XXXXXX")}
sentinel=NO_CRITICAL_FINDINGS

cd "$(git rev-parse --show-toplevel)"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "working tree has uncommitted changes — commit or stash them first" >&2
  exit 1
fi

read_tools=(
  "Bash(git diff:*)" "Bash(git log:*)" "Bash(git show:*)" "Bash(git status:*)"
  "Bash(git rev-parse:*)" "Bash(git merge-base:*)"
  "Bash(grep:*)" "Bash(find:*)" "Bash(ls:*)" "Bash(cat:*)" "Bash(head:*)" "Bash(tail:*)"
  "Bash(sed -n:*)" "Bash(wc:*)"
)
fix_tools=("${read_tools[@]}" "Bash(./gradlew:*)" "Bash(git add:*)" "Bash(git commit:*)")

echo "logs: $log_dir"

for round in $(seq 1 "$max_rounds"); do
  review="$log_dir/review-$round.md"
  fix_log="$log_dir/fix-$round.log"

  echo "== round $round: review"
  claude -p "/code-review high review the current branch against $base" \
    --permission-mode "$permission_mode" \
    --allowedTools "${read_tools[@]}" \
    > "$review"

  if [[ ! -s "$review" ]]; then
    echo "round $round: the review produced no output — stopping" >&2
    exit 1
  fi

  echo "== round $round: fix"
  head_before=$(git rev-parse HEAD)
  claude -p "Below is a code review of the current branch against $base.

Fix only the findings that are real correctness bugs of critical or major severity caused by this branch:
not cleanups (reuse, simplification, efficiency, altitude, conventions), not contrived edge cases,
not intentional divergences documented in CLAUDE.md. Check each one against the code before acting on it.

For each bug you fix, follow the TDD rule in CLAUDE.md: add a permanent, named regression test first,
watch it fail, then fix. Drop a finding whose test does not fail.
Run jvmTest for every module you touched, the JS tests if you changed a commonMain Regex,
and apiCheck if you changed public API. If everything is green, create ONE commit (never stage .claude/)
matching the subject style of \`git log -5 --format=%s\`, and end its message with:
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
If you cannot get the build green, do not commit.

If no finding qualifies, change nothing and print $sentinel as the very last line of your answer.

--- review ---
$(cat "$review")" \
    --permission-mode "$permission_mode" \
    --allowedTools "${fix_tools[@]}" \
    > "$fix_log"

  if [[ "$(tail -n 1 "$fix_log" | tr -d '[:space:]')" == "$sentinel" ]]; then
    echo "done: no critical/major findings left after $round round(s)"
    exit 0
  fi

  # --porcelain also lists untracked files (e.g. a new regression test)
  if [[ -n "$(git status --porcelain)" ]]; then
    echo "round $round left uncommitted changes (build red?) — stopping, see $fix_log" >&2
    exit 1
  fi

  if [[ "$(git rev-parse HEAD)" == "$head_before" ]]; then
    echo "round $round: the fixer committed nothing (all findings dropped?) — stopping, see $fix_log"
    exit 0
  fi

  echo "round $round: $(git log -1 --format='%h %s')"
done

echo "stopped: reached $max_rounds rounds"
