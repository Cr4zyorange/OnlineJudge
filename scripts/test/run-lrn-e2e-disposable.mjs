#!/usr/bin/env node
import { randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { closeSync, existsSync, openSync } from 'node:fs';
import { chmod, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const backendDir = join(repoRoot, 'backend');
const frontendDir = join(repoRoot, 'frontend');
const backendJar = join(backendDir, 'target', 'onlinejudge-backend-0.1.0-SNAPSHOT.jar');
const commandSuffix = process.platform === 'win32' ? '.cmd' : '';
const javaCommand = process.platform === 'win32' ? 'java.exe' : 'java';
const tempDir = await mkdtemp(join(tmpdir(), 'onlinejudge-lrn-e2e-'));
const databasePath = join(tempDir, 'onlinejudge');
const backendLogPath = join(tempDir, 'backend.log');
const frontendLogPath = join(tempDir, 'frontend.log');
const proofPath = join(tempDir, 'disposable-proof.json');
const children = [];
let failed = false;

try {
  const backendPort = await choosePort(process.env.E2E_LRN_BACKEND_PORT);
  const frontendPort = await choosePort(process.env.E2E_LRN_FRONTEND_PORT, new Set([backendPort]));
  const backendUrl = `http://127.0.0.1:${backendPort}`;
  const baseUrl = `http://127.0.0.1:${frontendPort}`;

  await run(`${'mvn'}${commandSuffix}`, ['-q', '-DskipTests', 'package'], backendDir);
  if (!existsSync(backendJar)) {
    throw new Error(`backend jar not found: ${backendJar}`);
  }

  const backendLog = openSync(backendLogPath, 'w');
  const backend = spawn(javaCommand, ['-jar', backendJar], {
    cwd: backendDir,
    env: {
      ...process.env,
      SPRING_PROFILES_ACTIVE: '',
      SPRING_DATASOURCE_URL: `jdbc:h2:file:${databasePath.replaceAll('\\', '/')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;AUTO_SERVER=TRUE`,
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.h2.Driver',
      SPRING_DATASOURCE_USERNAME: 'sa',
      SPRING_DATASOURCE_PASSWORD: '',
      SPRING_SQL_INIT_MODE: 'always',
      ONLINEJUDGE_COURSE_SCHEMA_INITIALIZER_ENABLED: 'true',
      ONLINEJUDGE_DEMO_DATA_ENABLED: 'true',
      ONLINEJUDGE_STORAGE_LOCAL_ROOT: join(tempDir, 'uploads'),
      SERVER_ADDRESS: '127.0.0.1',
      SERVER_PORT: String(backendPort)
    },
    detached: process.platform !== 'win32',
    stdio: ['ignore', backendLog, backendLog]
  });
  closeSync(backendLog);
  children.push(backend);
  await waitFor(`${backendUrl}/api/v1/system/health`, 'isolated backend health');
  await waitForLogin(backendUrl, 'teacher001', 'Teacher001@pass');
  await waitForLogin(backendUrl, 'student001', 'Student001@pass');

  const frontendLog = openSync(frontendLogPath, 'w');
  const frontend = spawnPortable(`npm${commandSuffix}`, [
    'run', 'dev', '--', '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort'
  ], {
    cwd: frontendDir,
    env: { ...process.env, VITE_API_PROXY_TARGET: backendUrl },
    detached: process.platform !== 'win32',
    stdio: ['ignore', frontendLog, frontendLog]
  });
  closeSync(frontendLog);
  children.push(frontend);
  await waitFor(baseUrl, 'isolated Vite frontend');

  const token = randomBytes(32).toString('hex');
  await writeFile(proofPath, `${JSON.stringify({
    token,
    baseUrl,
    backendUrl,
    backendPid: backend.pid,
    frontendPid: frontend.pid,
    databasePath
  }, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  if (process.platform !== 'win32') {
    await chmod(proofPath, 0o600);
  }

  await run(`npm${commandSuffix}`, [
    'run', 'test:e2e', '--', 'tests/e2e/lrn', '--workers=4'
  ], frontendDir, {
    ...process.env,
    E2E_BASE_URL: baseUrl,
    E2E_LRN_DISPOSABLE_PROOF_FILE: proofPath,
    E2E_LRN_DISPOSABLE_TOKEN: token,
    E2E_TEACHER_ACCOUNT: 'teacher001',
    E2E_TEACHER_PASSWORD: 'Teacher001@pass',
    E2E_STUDENT_ACCOUNT: 'student001',
    E2E_STUDENT_PASSWORD: 'Student001@pass'
  });
} catch (error) {
  failed = true;
  console.error(`run-lrn-e2e-disposable: ${error instanceof Error ? error.message : String(error)}`);
  await printTail(backendLogPath, 'disposable LRN backend log');
  await printTail(frontendLogPath, 'disposable LRN frontend log');
  process.exitCode = 1;
} finally {
  for (const child of children.reverse()) {
    stopProcessTree(child.pid);
  }
  const safeTempRoot = resolve(tmpdir());
  if (dirname(resolve(tempDir)) !== safeTempRoot || !basename(tempDir).startsWith('onlinejudge-lrn-e2e-')) {
    throw new Error(`refusing to remove unexpected temp path: ${tempDir}`);
  }
  await rm(tempDir, { recursive: true, force: true });
  if (!failed) {
    console.log('run-lrn-e2e-disposable: PASS; isolated services and database removed');
  }
}

async function choosePort(raw, excluded = new Set()) {
  if (raw) {
    const value = Number(raw);
    if (!Number.isInteger(value) || value < 1000 || value > 65535 || excluded.has(value)) {
      throw new Error('configured disposable port must be a distinct integer from 1000 to 65535');
    }
    await assertPortAvailable(value);
    return value;
  }
  for (;;) {
    const value = await new Promise((resolvePort, reject) => {
      const server = createServer();
      server.once('error', reject);
      server.listen(0, '127.0.0.1', () => {
        const address = server.address();
        const port = typeof address === 'object' && address ? address.port : 0;
        server.close((closeError) => closeError ? reject(closeError) : resolvePort(port));
      });
    });
    if (!excluded.has(value)) {
      return value;
    }
  }
}

async function assertPortAvailable(port) {
  await new Promise((resolvePort, reject) => {
    const server = createServer();
    server.once('error', () => reject(new Error(`port ${port} is already in use`)));
    server.listen(port, '127.0.0.1', () => server.close(resolvePort));
  });
}

async function waitFor(url, label) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_000) });
      if (response.ok) {
        return;
      }
    } catch {
      // Service is still starting.
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }
  throw new Error(`${label} did not become ready within 30 seconds`);
}

async function waitForLogin(baseUrl, account, password) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      const response = await fetch(`${baseUrl}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ account, password }),
        signal: AbortSignal.timeout(1_000)
      });
      if (response.ok) {
        return;
      }
    } catch {
      // Seed initialization is still running.
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }
  throw new Error(`seeded account ${account} did not become ready within 30 seconds`);
}

function run(command, args, cwd, env = process.env) {
  return new Promise((resolveRun, reject) => {
    const child = spawnPortable(command, args, {
      cwd,
      env,
      stdio: 'inherit',
      windowsHide: true
    });
    child.once('error', reject);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolveRun();
      } else {
        reject(new Error(`${command} exited with ${code ?? signal}`));
      }
    });
  });
}

function spawnPortable(command, args, options) {
  if (process.platform !== 'win32') {
    return spawn(command, args, options);
  }
  const commandLine = [command, ...args]
    .map(quoteWindowsCommandArgument)
    .join(' ');
  return spawn(process.env.ComSpec || 'C:\\Windows\\System32\\cmd.exe', [
    '/d', '/s', '/c', commandLine
  ], { ...options, windowsHide: true });
}

function quoteWindowsCommandArgument(value) {
  const text = String(value);
  if (/^[A-Za-z0-9_./:=@+-]+$/.test(text)) {
    return text;
  }
  return `"${text.replaceAll('"', '""')}"`;
}

function stopProcessTree(pid) {
  if (!pid) {
    return;
  }
  if (process.platform === 'win32') {
    spawnSync('taskkill.exe', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
    return;
  }
  try {
    process.kill(-pid, 'SIGTERM');
  } catch {
    // Process already exited.
  }
}

async function printTail(path, label) {
  if (!existsSync(path)) {
    return;
  }
  const lines = (await readFile(path, 'utf8')).split(/\r?\n/).slice(-120);
  console.error(`--- ${label} (tail) ---`);
  console.error(lines.join('\n'));
}
