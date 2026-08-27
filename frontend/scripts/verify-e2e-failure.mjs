import { existsSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const temporarySpec = resolve('tests/e2e/.runner-failure.contract.spec.ts');
const playwrightCli = resolve('node_modules/@playwright/test/cli.js');

if (existsSync(temporarySpec)) {
  throw new Error(`refusing to overwrite existing contract fixture: ${temporarySpec}`);
}

try {
  writeFileSync(temporarySpec, `
import { expect, test } from '@playwright/test';

test('deliberate assertion failure returns a non-zero exit', () => {
  expect('actual').toBe('expected');
});
`, { mode: 0o600 });

const result = spawnSync(
    process.execPath,
    // Playwright 在 Windows 上无法把含反斜杠的绝对路径识别为测试文件，
    // 统一转成正斜杠，保证本地（Windows）与 CI（Linux）行为一致。
    [playwrightCli, 'test', temporarySpec.replaceAll('\\', '/'), '--workers=1'],
    {
      cwd: process.cwd(),
      encoding: 'utf8',
      env: {
        ...process.env,
        E2E_FAILURE_ARTIFACTS: 'off'
      }
    }
  );

  const combinedOutput = `${result.stdout ?? ''}\n${result.stderr ?? ''}`;
  if (result.error) {
    throw result.error;
  }
  if (result.status === 0) {
    throw new Error('deliberate assertion unexpectedly returned exit code 0');
  }
  if (!/1 failed/.test(combinedOutput)) {
    throw new Error('Playwright did not report the deliberate assertion failure');
  }

  process.stdout.write(`PASS: deliberate assertion failed with exit code ${result.status}\n`);
} finally {
  rmSync(temporarySpec, { force: true });
}
