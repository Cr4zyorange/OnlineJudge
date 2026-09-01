#!/usr/bin/env node
import { randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import { closeSync, existsSync, openSync } from 'node:fs';
import { chmod, copyFile, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { createConnection, createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const backendDir = join(repositoryRoot, 'backend');
const frontendDir = join(repositoryRoot, 'frontend');
const backendJar = join(backendDir, 'target', 'onlinejudge-backend-0.1.0-SNAPSHOT.jar');
const commandSuffix = process.platform === 'win32' ? '.cmd' : '';
const javaCommand = process.platform === 'win32' ? 'java.exe' : 'java';
const dockerCommand = process.platform === 'win32' ? 'docker.exe' : 'docker';
const rabbitImage = 'rabbitmq:4.1-management';
const rabbitUsername = 'onlinejudge_e2e';
const e2eTargets = [
  'tests/e2e/auth',
  'tests/e2e/crs',
  'tests/e2e/grd',
  'tests/e2e/hwk',
  'tests/e2e/lab',
  'tests/e2e/lrn/lrn-business-closure.spec.ts',
  'tests/e2e/lrn/notification-read-on-open.spec.ts',
  'tests/e2e/shared'
];
const artifactDir = resolve(process.env.E2E_ARTIFACT_DIR
  || join(repositoryRoot, 'ci-artifacts', 'browser-e2e-gate'));

const children = [];
let tempDir = '';
let grdProofDir = '';
let backendLogPath = '';
let frontendLogPath = '';
let backendUrl = '';
let baseUrl = '';
let rabbitPort = 0;
let rabbitContainerName = '';
let rabbitPassword = '';
let failed = false;

try {
  await mkdir(artifactDir, { recursive: true });
  tempDir = await mkdtemp(join(tmpdir(), 'onlinejudge-lrn-e2e-'));
  grdProofDir = await mkdtemp(join(tmpdir(), 'onlinejudge-grd-e2e.'));
  const databasePath = join(tempDir, 'onlinejudge');
  backendLogPath = join(tempDir, 'backend.log');
  frontendLogPath = join(tempDir, 'frontend.log');

  rabbitPort = await choosePort(process.env.E2E_BUSINESS_RABBITMQ_PORT);
  const backendPort = await choosePort(process.env.E2E_BUSINESS_BACKEND_PORT, new Set([rabbitPort]));
  const frontendPort = await choosePort(
    process.env.E2E_BUSINESS_FRONTEND_PORT,
    new Set([rabbitPort, backendPort])
  );
  backendUrl = `http://127.0.0.1:${backendPort}`;
  baseUrl = `http://127.0.0.1:${frontendPort}`;

  rabbitContainerName = `onlinejudge-e2e-rabbit-${randomBytes(8).toString('hex')}`;
  rabbitPassword = randomBytes(24).toString('base64url');
  await run(dockerCommand, [
    'run', '--detach', '--rm', '--name', rabbitContainerName,
    '--publish', `127.0.0.1:${rabbitPort}:5672`,
    '--env', `RABBITMQ_DEFAULT_USER=${rabbitUsername}`,
    '--env', `RABBITMQ_DEFAULT_PASS=${rabbitPassword}`,
    rabbitImage
  ], repositoryRoot);
  await waitForRabbit(rabbitPort);

  await run(`mvn${commandSuffix}`, ['-q', '-DskipTests', 'package'], backendDir);
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
      ONLINEJUDGE_EVALUATION_SANDBOX_MODE: 'fake',
      ONLINEJUDGE_RELIABILITY_RABBITMQ_ENABLED: 'true',
      ONLINEJUDGE_RELIABILITY_PUBLISHER_ENABLED: 'true',
      ONLINEJUDGE_RELIABILITY_PUBLISHER_FIXED_DELAY_MS: '250',
      SPRING_RABBITMQ_HOST: '127.0.0.1',
      SPRING_RABBITMQ_PORT: String(rabbitPort),
      SPRING_RABBITMQ_USERNAME: rabbitUsername,
      SPRING_RABBITMQ_PASSWORD: rabbitPassword,
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
  await waitForLogin(backendUrl, 'admin001', 'Admin001@pass');

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
  const lrnProofPath = join(tempDir, 'disposable-proof.json');
  const grdProofPath = join(grdProofDir, 'disposable-proof');
  await writeFile(lrnProofPath, `${JSON.stringify({
    token,
    baseUrl,
    backendUrl,
    backendPid: backend.pid,
    frontendPid: frontend.pid,
    databasePath
  }, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  await writeFile(grdProofPath, `${token}\n${baseUrl}\n${backend.pid}\n`, {
    encoding: 'utf8',
    mode: 0o600
  });
  if (process.platform !== 'win32') {
    await chmod(lrnProofPath, 0o600);
    await chmod(grdProofPath, 0o600);
  }

  await run(`npm${commandSuffix}`, [
    'run', 'test:e2e', '--', ...e2eTargets, '--workers=1'
  ], frontendDir, {
    ...process.env,
    E2E_BASE_URL: baseUrl,
    E2E_TEACHER_ACCOUNT: 'teacher001',
    E2E_TEACHER_PASSWORD: 'Teacher001@pass',
    E2E_STUDENT_ACCOUNT: 'student001',
    E2E_STUDENT_PASSWORD: 'Student001@pass',
    E2E_ADMIN_ACCOUNT: 'admin001',
    E2E_ADMIN_PASSWORD: 'Admin001@pass',
    E2E_LRN_DISPOSABLE_PROOF_FILE: lrnProofPath,
    E2E_LRN_DISPOSABLE_TOKEN: token,
    E2E_GRD_DISPOSABLE_PROOF_FILE: grdProofPath,
    E2E_GRD_DISPOSABLE_TOKEN: token
  });
} catch (error) {
  failed = true;
  console.error(`run-business-e2e-disposable: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
} finally {
  for (const child of children.reverse()) {
    stopProcessTree(child.pid);
  }
  await retainRabbitLog();
  removeRabbitContainer();
  await retainArtifacts();
  await removeTempDirectory(tempDir, 'onlinejudge-lrn-e2e-');
  await removeTempDirectory(grdProofDir, 'onlinejudge-grd-e2e.');
  if (!failed) {
    console.log('run-business-e2e-disposable: PASS; isolated services and database removed');
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

async function waitForLogin(url, account, password) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      const response = await fetch(`${url}/api/v1/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ account, password }),
        signal: AbortSignal.timeout(1_000)
      });
      if (response.ok) {
        return;
      }
    } catch {
      // Demo seed initialization is still running.
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }
  throw new Error(`seeded account ${account} did not become ready within 30 seconds`);
}

async function waitForRabbit(port) {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    try {
      await new Promise((resolveProbe, rejectProbe) => {
        const socket = createConnection({ host: '127.0.0.1', port });
        socket.setTimeout(1_000);
        socket.once('connect', () => {
          socket.end();
          resolveProbe(undefined);
        });
        socket.once('error', rejectProbe);
        socket.once('timeout', () => {
          socket.destroy();
          rejectProbe(new Error('RabbitMQ connection timed out'));
        });
      });
      return;
    } catch {
      // The disposable broker is still booting.
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }
  throw new Error('isolated RabbitMQ did not become ready within 30 seconds');
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

async function retainArtifacts() {
  await mkdir(artifactDir, { recursive: true });
  await Promise.all([
    retainFile(backendLogPath, 'backend.log'),
    retainFile(frontendLogPath, 'frontend.log')
  ]);
  await writeFile(join(artifactDir, 'runner-summary.json'), `${JSON.stringify({
    status: failed ? 'failed' : 'passed',
    backendUrl,
    baseUrl,
    rabbitPort,
    targets: e2eTargets,
    timestamp: new Date().toISOString()
  }, null, 2)}\n`);
}

async function retainRabbitLog() {
  if (!rabbitContainerName) {
    return;
  }
  const result = spawnSync(dockerCommand, ['logs', rabbitContainerName], {
    encoding: 'utf8',
    windowsHide: true
  });
  const output = `${result.stdout || ''}${result.stderr || ''}`;
  if (output) {
    await writeFile(join(artifactDir, 'rabbitmq.log'), output);
  }
}

function removeRabbitContainer() {
  if (!rabbitContainerName) {
    return;
  }
  spawnSync(dockerCommand, ['rm', '--force', rabbitContainerName], {
    stdio: 'ignore',
    windowsHide: true
  });
}

async function retainFile(source, name) {
  if (source && existsSync(source)) {
    await copyFile(source, join(artifactDir, name));
  }
}

async function removeTempDirectory(path, requiredPrefix) {
  if (!path) {
    return;
  }
  const safeTempRoot = resolve(tmpdir());
  if (dirname(resolve(path)) !== safeTempRoot || !basename(path).startsWith(requiredPrefix)) {
    throw new Error(`refusing to remove unexpected temp path: ${path}`);
  }
  await rm(path, { recursive: true, force: true });
}
