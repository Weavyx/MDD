#!/usr/bin/env node
/**
 * PostToolUse hook (matcher Edit|Write) : formate avec Prettier le fichier
 * qui vient d'etre ecrit, s'il appartient au front.
 *
 * Lit le JSON du hook sur stdin, en extrait tool_input.file_path, et ne fait
 * rien (code 0) si le fichier n'est pas sous front/ ou n'a pas une extension
 * geree. En cas d'echec de Prettier, ecrit stderr et sort en 1 : PostToolUse
 * traite tout code non nul comme une erreur non bloquante, l'edition est deja
 * appliquee.
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import process from 'node:process';

const EXTENSIONS = new Set(['.ts', '.html', '.scss', '.css', '.json']);

const projectDir = process.env.CLAUDE_PROJECT_DIR
  ? path.resolve(process.env.CLAUDE_PROJECT_DIR)
  : path.resolve(import.meta.dirname, '..', '..');
const frontDir = path.join(projectDir, 'front');
const prettierBin = path.join(frontDir, 'node_modules', 'prettier', 'bin', 'prettier.cjs');

let raw = '';
process.stdin.setEncoding('utf8');
for await (const chunk of process.stdin) raw += chunk;

let payload;
try {
  payload = JSON.parse(raw);
} catch {
  // Entree illisible : rien a formater, on ne bloque pas.
  process.exit(0);
}

const filePath = payload?.tool_input?.file_path;
if (typeof filePath !== 'string' || filePath.length === 0) process.exit(0);

const absolute = path.resolve(projectDir, filePath);
if (!EXTENSIONS.has(path.extname(absolute).toLowerCase())) process.exit(0);

const relativeToFront = path.relative(frontDir, absolute);
const insideFront =
  relativeToFront.length > 0 &&
  !relativeToFront.startsWith('..') &&
  !path.isAbsolute(relativeToFront);
if (!insideFront) process.exit(0);

const result = spawnSync(process.execPath, [prettierBin, '--write', absolute], {
  cwd: frontDir,
  encoding: 'utf8',
});

if (result.error) {
  process.stderr.write(`format.mjs: impossible de lancer Prettier : ${result.error.message}\n`);
  process.exit(1);
}

if (result.status !== 0) {
  process.stderr.write(result.stderr || result.stdout || 'format.mjs: Prettier a echoue\n');
  process.exit(1);
}

process.exit(0);
