import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const rootPath = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const evidenceRoot = 'output/test/issue-262';

function git(...args) {
  return execFileSync('git', args, { cwd: rootPath, encoding: 'utf8' }).trim();
}

async function read(relativePath) {
  return readFile(resolve(rootPath, relativePath), 'utf8');
}

test('Issue #262 evidence commit is bound to its tested parent and contains evidence only', async () => {
  const headSha = git('rev-parse', 'HEAD');
  const parentSha = git('rev-parse', 'HEAD^');
  const parentTree = git('rev-parse', `${parentSha}^{tree}`);
  const changed = git('diff', '--name-only', '--diff-filter=ACMRTUXB', `${parentSha}..${headSha}`)
    .split(/\r?\n/)
    .filter(Boolean);
  const deleted = git('diff', '--name-only', '--diff-filter=D', `${parentSha}..${headSha}`)
    .split(/\r?\n/)
    .filter(Boolean);

  assert.equal(deleted.length, 0, `evidence commit must not delete files: ${deleted.join(', ')}`);
  assert.ok(changed.length > 0, 'evidence commit must contain recorded results');
  for (const path of changed) {
    assert.match(path, /^output\/test\/issue-262\//, `executable or out-of-scope path in evidence commit: ${path}`);
    assert.match(path, /\.(?:log|md|png|txt)$/, `unsupported evidence file type: ${path}`);
  }

  const environment = await read(`${evidenceRoot}/environment.txt`);
  const report = await read(`${evidenceRoot}/README.md`);
  const rawIndex = await read(`${evidenceRoot}/raw/README.md`);
  const rawBackend = await read(`${evidenceRoot}/raw/backend-target.log`);
  const rawE2e = await Promise.all([
    read(`${evidenceRoot}/raw/e2e-lrn.log`),
    read(`${evidenceRoot}/raw/e2e-lrn-repeat.log`)
  ]);
  const diffCheck = await read(`${evidenceRoot}/raw/diff-check.log`);
  const baseSha = environment.match(/^base_sha=([0-9a-f]{40})$/m)?.[1];
  const executionSha = environment.match(/^execution_sha=([0-9a-f]{40})$/m)?.[1];
  const executionTree = environment.match(/^execution_tree=([0-9a-f]{40})$/m)?.[1];

  assert.ok(baseSha, 'environment must record a full base SHA');
  assert.equal(executionSha, parentSha, 'execution_sha must equal the evidence commit direct parent');
  assert.equal(executionTree, parentTree, 'execution_tree must equal the tested parent tree');
  assert.match(report, new RegExp(baseSha));
  assert.match(report, new RegExp(parentSha));
  assert.match(rawIndex, new RegExp(parentSha));
  assert.match(rawBackend, /Tests run: 101, Failures: 0, Errors: 0, Skipped: 0/);
  assert.deepEqual(rawE2e.every((log) => /4 passed/.test(log)), true);
  assert.match(diffCheck, new RegExp(`command=git diff --check ${baseSha}\\.\\.${parentSha}`));
  assert.match(diffCheck, /^exit_code=0$/m);
});
