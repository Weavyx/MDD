---
name: audit
description: Read-only audit that answers numbered questions with a sourced report. Manual only.
disable-model-invocation: true
context: fork
background: false
disallowed-tools: Edit, Write, NotebookEdit
argument-hint: "[numbered questions]"
---
Read-only audit. Questions:

$ARGUMENTS

Rules: create, edit or delete nothing; no state-changing git, package or Docker command; never print secret values (report key names only). Answer each question by its number. Every claim cites `file:line` or the command and the relevant output line, marked [lu] / [grep] / [exec] / [déduit]. Anything not established: "UNKNOWN — reason". End with `⚠ file:line — what` anomalies (not fixed), then the list of commands run.
