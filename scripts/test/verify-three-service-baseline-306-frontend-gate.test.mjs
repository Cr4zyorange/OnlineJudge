import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, cpSync, mkdtempSync, mkdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

function writeExecutable(path, contents) {
  writeFileSync(path, contents, 'utf8');
  chmodSync(path, 0o755);
}

test('the formal frontend gate rejects a malformed #306 Mermaid source after its locked dependency install', () => {
  const root = mkdtempSync(join(tmpdir(), 'onlinejudge-three-service-baseline-306-frontend-gate-'));
  try {
    for (const relativePath of [
      'scripts/ci/frontend-verify.sh',
      'scripts/test/verify-three-service-baseline-306-render.test.mjs',
      'scripts/test/verify-three-service-baseline-306-frontend-gate.test.mjs',
      'scripts/dev/render-mermaid.mjs',
      'docs/diagrams/arch/issue306-three-service-context.mmd',
      'docs/diagrams/arch/issue306-assessment-worker-fencing.mmd',
      'docs/diagrams/arch/issue306-three-service-deployment.mmd',
      'frontend/package.json',
      'frontend/package-lock.json'
    ]) {
      cpSync(join(repoRoot, relativePath), join(root, relativePath), { recursive: true });
    }

    const nodeModules = join(repoRoot, 'frontend/node_modules');
    assert.ok(readFileSync(join(nodeModules, 'mermaid/dist/mermaid.min.js'), 'utf8').length > 0,
      'formal frontend gate must run this mutation after npm ci installs locked Mermaid');
    symlinkSync(nodeModules, join(root, 'frontend/node_modules'), 'dir');

    const brokenDiagram = join(root, 'docs/diagrams/arch/issue306-three-service-context.mmd');
    writeFileSync(brokenDiagram, `${readFileSync(brokenDiagram, 'utf8')}\nCourse -->\n`, 'utf8');

    const fakeBin = join(root, 'fake-bin');
    mkdirSync(fakeBin, { recursive: true });
    writeExecutable(join(fakeBin, 'node'), `#!/usr/bin/env bash
if [[ "${'$'}{1:-}" == "-v" ]]; then
  printf 'v22.0.0\\n'
  exit 0
fi
unset NODE_TEST_CONTEXT
exec ${JSON.stringify(process.execPath)} "${'$'}@"
`);
    writeExecutable(join(fakeBin, 'npm'), `#!/usr/bin/env bash
case "${'$'}{1:-}" in
  -v) printf '10.9.2\\n' ;;
  ci|run) exit 0 ;;
  *) exit 0 ;;
esac
`);

    const result = spawnSync('bash', [join(root, 'scripts/ci/frontend-verify.sh'), root], {
      cwd: root,
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${fakeBin}:${process.env.PATH}`,
        OJ_CI_NODE_MAJOR: '22',
        OJ_CI_NPM_VERSION: '10.9.2',
        OJ306_RENDER_GATE_MUTATION: '1'
      }
    });

    assert.notEqual(result.status, 0, `${result.stdout}\n${result.stderr}`);
    assert.match(`${result.stdout}\n${result.stderr}`, /verify-three-service-baseline-306-render\.test\.mjs/);
    assert.match(`${result.stdout}\n${result.stderr}`, /渲染失败：.*issue306-three-service-context\.mmd/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
