#!/usr/bin/env node

import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const verifier = join(repoRoot, 'scripts/dev/verify-mermaid-svg.mjs');
const committed = join(repoRoot, 'docs/最终提交/assets/fig_4_14a_hwk_submission_ssd.svg');
const temporaryDirectory = mkdtempSync(join(tmpdir(), 'oj-mermaid-svg-test-'));
const mutated = join(temporaryDirectory, 'reversed-edge.svg');

try {
  const source = readFileSync(committed, 'utf8');
  let mutatedEdge = false;
  const reversed = source.replace(
    /(data-et="message"[^>]*data-from=")([^"]+)("[^>]*data-to=")([^"]+)(")/,
    (match, prefix, from, middle, to, suffix) => {
      assert.notEqual(from, to, 'mutation fixture must contain a directed edge');
      mutatedEdge = true;
      return `${prefix}${to}${middle}${from}${suffix}`;
    }
  );
  assert.equal(mutatedEdge, true, 'expected a Mermaid message edge to mutate');
  writeFileSync(mutated, reversed);

  const result = spawnSync(process.execPath, [verifier, committed, mutated], {
    encoding: 'utf8',
  });
  assert.notEqual(
    result.status,
    0,
    `reversed Mermaid edge must be rejected, but verifier exited successfully: ${result.stdout}`
  );
  assert.match(result.stderr, /semantic SVG mismatch/);
  console.log('Mermaid SVG reversed-edge mutation: PASS');
} finally {
  rmSync(temporaryDirectory, { recursive: true, force: true });
}
