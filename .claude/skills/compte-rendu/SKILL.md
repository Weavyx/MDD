---
name: compte-rendu
description: Factual end-of-session report for review in the claude.ai hub. Manual only.
disable-model-invocation: true
---
Branch: !`git branch --show-current`
Status: !`git status --short`
Commits since main: !`git log --oneline main..HEAD`
Diff vs main: !`git diff --stat main...HEAD`

Write a factual report of this session. No self-praise, no adjectives about quality. Sections:
1. Done — each change with `file:line`.
2. Verification — commands run with the relevant output lines (build, tests, format, visual checks); state explicitly what was not run.
3. Deviations — anything done differently from the request, and why.
4. ⚠ Anomalies seen in passing (`file:line`), not fixed.
5. Vault — decisions made or changed this session that need a vault note, or that make an existing note wrong.
6. Open items — remaining, blocked, or awaiting a decision.
Mark every claim [lu] / [grep] / [exec] / [déduit].
