import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = new URL('../..', import.meta.url);

function readRepositoryFile(path) {
  return readFileSync(new URL(path, repositoryRoot), 'utf8');
}

test('the disposable business E2E command runs every maintained browser scenario', () => {
  const packageJson = JSON.parse(readRepositoryFile('frontend/package.json'));
  const runner = readRepositoryFile('scripts/test/run-business-e2e-disposable.mjs');
  const threeServiceRunner = readRepositoryFile('scripts/test/run-business-e2e-three-service.mjs');
  const verifier = readRepositoryFile('scripts/ci/browser-e2e-verify.sh');

  assert.equal(
    packageJson.scripts['test:e2e:business:disposable'],
    'node ../scripts/test/run-business-e2e-disposable.mjs'
  );
  for (const target of [
    'tests/e2e/auth',
    'tests/e2e/crs',
    'tests/e2e/grd',
    'tests/e2e/hwk',
    'tests/e2e/lab',
    'tests/e2e/lrn/lrn-business-closure.spec.ts',
    'tests/e2e/lrn/notification-read-on-open.spec.ts',
    'tests/e2e/shared'
  ]) {
    assert.match(threeServiceRunner, new RegExp(target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
  assert.match(runner, /run_disposable_environment\.sh/);
  assert.match(runner, /--after-ready/);
  assert.match(runner, /IDENTITY_SEED_DATA_ENABLED/);
  assert.doesNotMatch(runner, /jdbc:h2/i);
  assert.match(threeServiceRunner, /E2E_THREE_SERVICE_PROOF_FILE/);
  assert.match(threeServiceRunner, /PLAYWRIGHT_JUNIT_OUTPUT_FILE/);
  assert.match(runner, /E2E_ARTIFACT_DIR/);
  assert.match(verifier, /npm ci --no-audit --no-fund/);
  assert.match(verifier, /playwright install/);
  assert.match(verifier, /PLAYWRIGHT_JUNIT_OUTPUT_FILE/);
  assert.match(verifier, /npm run test:e2e:business:disposable/);
});

test('GitHub Actions blocks delivery on the real browser E2E gate and preserves evidence', () => {
  const workflow = readRepositoryFile('.github/workflows/ci.yml');

  assert.match(workflow, /^  browser-e2e-gate:\n/m);
  assert.match(workflow, /name: Browser business E2E/);
  assert.match(workflow, /browser-e2e-gate:[\s\S]*?needs: \[validate-workflows\]/);
  assert.match(workflow, /scripts\/ci\/browser-e2e-verify\.sh/);
  assert.match(workflow, /frontend\/playwright-report\/\*\*/);
  assert.match(workflow, /needs: \[validate-workflows, backend-gate, frontend-gate, contracts-gate, browser-e2e-gate\]/);
});

test('Browser E2E installs the pinned Docker Scout SBOM producer before the disposable runner', () => {
  const workflow = readRepositoryFile('.github/workflows/ci.yml');
  const browserStart = workflow.indexOf('  browser-e2e-gate:\n');
  const deliveryStart = workflow.indexOf('  delivery:\n');
  assert.ok(browserStart >= 0 && deliveryStart > browserStart, 'workflow must delimit the Browser E2E job');
  const browserJob = workflow.slice(browserStart, deliveryStart);
  const scoutInstall = browserJob.indexOf('name: Install Docker Scout 1.24.0');
  const runner = browserJob.indexOf('scripts/ci/browser-e2e-verify.sh');

  assert.ok(scoutInstall >= 0, 'Browser E2E must not depend on the runner default Docker Scout plugin');
  assert.ok(runner > scoutInstall, 'Browser E2E must install Docker Scout before building SBOM-bearing disposable images');
  assert.match(browserJob, /docker-scout_1\.24\.0_linux_amd64\.tar\.gz/);
  assert.match(browserJob, /f4e2814bd61040365153d5b964b144cb2dc6ee536a68b5bac4cadf00fc0ec34b/);
  assert.match(browserJob, /sha256sum --check --status/);
  assert.match(browserJob, /cli-plugins\/docker-scout/);
  assert.match(browserJob, /docker scout version/);
});
