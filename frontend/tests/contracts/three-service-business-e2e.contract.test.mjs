import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repositoryRoot = new URL('../../..', import.meta.url);

function readRepositoryFile(path) {
  return readFileSync(new URL(path, repositoryRoot), 'utf8');
}

test('business E2E enters the disposable nine-workload platform rather than a monolith', () => {
  const packageJson = JSON.parse(readRepositoryFile('frontend/package.json'));
  const entry = readRepositoryFile('scripts/test/run-business-e2e-disposable.mjs');
  const executionCore = readRepositoryFile('scripts/test/run-business-e2e-three-service.mjs');

  assert.equal(packageJson.scripts['test:e2e:business:disposable'], 'node ../scripts/test/run-business-e2e-disposable.mjs');
  assert.match(entry, /run_disposable_environment\.sh/);
  assert.match(entry, /--after-ready/);
  assert.doesNotMatch(entry, /jdbc:h2/i);
  assert.doesNotMatch(entry, /SPRING_DATASOURCE_URL/);
  assert.doesNotMatch(entry, /npm.*run.*dev/i);
  for (const target of [
    'tests/e2e/auth', 'tests/e2e/crs', 'tests/e2e/grd', 'tests/e2e/hwk', 'tests/e2e/lab',
    'tests/e2e/lrn/lrn-business-closure.spec.ts',
    'tests/e2e/lrn/notification-read-on-open.spec.ts', 'tests/e2e/shared'
  ]) {
    assert.match(executionCore, new RegExp(target.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
  }
});
