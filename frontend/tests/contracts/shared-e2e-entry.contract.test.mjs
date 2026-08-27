import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const frontendRoot = new URL('../..', import.meta.url);
const repositoryRoot = new URL('../../..', import.meta.url);

function readFrontendFile(path) {
  return readFileSync(new URL(path, frontendRoot), 'utf8');
}

function findPlaywrightConfigs(directory = fileURLToPath(frontendRoot), relativeDirectory = '') {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const relativePath = relativeDirectory ? `${relativeDirectory}/${entry.name}` : entry.name;
    if (entry.isDirectory()) {
      return entry.name === 'node_modules'
        ? []
        : findPlaywrightConfigs(join(directory, entry.name), relativePath);
    }
    return /(^|\/)playwright\.config\.[cm]?[jt]s$/.test(relativePath) ? [relativePath] : [];
  });
}

test('shared E2E package commands use the single Playwright runner', () => {
  const packageJson = JSON.parse(readFrontendFile('package.json'));

  assert.equal(packageJson.scripts['test:e2e'], 'playwright test');
  assert.equal(packageJson.scripts['test:e2e:contract'], 'node --test tests/contracts/shared-e2e-entry.contract.test.mjs');
  assert.equal(packageJson.scripts['test:e2e:verify-failure'], 'node scripts/verify-e2e-failure.mjs');
  assert.match(packageJson.devDependencies['@playwright/test'], /^\^1\./);
});

test('repository has one Playwright config and one ignored artifact convention', () => {
  const configFiles = findPlaywrightConfigs();
  const config = readFrontendFile('playwright.config.ts');
  const gitignore = readFileSync(new URL('.gitignore', repositoryRoot), 'utf8');

  assert.deepEqual(configFiles, ['playwright.config.ts']);
  assert.match(config, /E2E_BASE_URL/);
  assert.match(config, /playwright-report/);
  assert.match(config, /test-results/);
  assert.match(config, /trace:\s*'retain-on-failure'/);
  assert.match(config, /screenshot:\s*'only-on-failure'/);
  assert.match(config, /video:\s*'retain-on-failure'/);
  assert.match(gitignore, /^frontend\/playwright-report\/$/m);
  assert.match(gitignore, /^frontend\/test-results\/$/m);
});

test('shared fixture, real-application smoke and local runbook are present', () => {
  const fixture = readFrontendFile('tests/e2e/fixtures.ts');
  const smoke = readFrontendFile('tests/e2e/shared/application.smoke.spec.ts');
  const runbook = readFrontendFile('tests/e2e/README.md');

  assert.match(fixture, /loginAs/);
  assert.match(fixture, /logout/);
  assert.match(fixture, /waitForBusinessState/);
  assert.match(fixture, /failureEvidenceName/);
  assert.match(smoke, /\/api\/v1\/system\/health/);
  assert.match(smoke, /OnlineJudgeForSE/);
  assert.match(runbook, /docker compose -f deploy\/docker\/compose\.yml up -d --build/);
  assert.match(runbook, /E2E_BASE_URL/);
  assert.match(runbook, /test:e2e --/);
  assert.match(runbook, /playwright-report/);
  assert.match(runbook, /test-results/);
});
