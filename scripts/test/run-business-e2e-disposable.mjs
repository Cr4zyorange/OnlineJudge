#!/usr/bin/env node
import { spawn, spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import { createServer } from 'node:net';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const platformRunner = join(repositoryRoot, 'scripts', 'platform', 'run_disposable_environment.sh');
const threeServiceRunner = join(repositoryRoot, 'scripts', 'test', 'run-business-e2e-three-service.mjs');
const artifactDir = resolve(process.env.E2E_ARTIFACT_DIR
  || join(repositoryRoot, 'ci-artifacts', 'browser-e2e-gate'));

try {
  await mkdir(artifactDir, { recursive: true });
  const port = await choosePort(process.env.E2E_THREE_SERVICE_GATEWAY_PORT);
  const environment = { ...process.env };
  delete environment.E2E_BASE_URL;
  environment.GATEWAY_HTTP_PORT = String(port);
  environment.IDENTITY_SEED_DATA_ENABLED = 'true';
  environment.E2E_ARTIFACT_DIR = artifactDir;

  const bashCommand = resolveBashCommand();
  configureGitBashPython(environment, bashCommand);
  await run(bashCommand, [
    toBashPath(platformRunner, bashCommand),
    '--git-sha', gitRevision(),
    '--output-dir', toBashPath(artifactDir, bashCommand),
    '--after-ready', toBashPath(process.execPath, bashCommand), toBashPath(threeServiceRunner, bashCommand), '--inside-platform'
  ], environment);
  console.log('run-business-e2e-disposable: PASS; the disposable nine-workload platform was removed');
} catch (error) {
  console.error(`run-business-e2e-disposable: ${error instanceof Error ? error.message : String(error)}`);
  process.exitCode = 1;
}

function resolveBashCommand() {
  if (process.env.E2E_BASH_COMMAND?.trim()) {
    return process.env.E2E_BASH_COMMAND.trim();
  }
  const gitBash = 'C:\\Program Files\\Git\\bin\\bash.exe';
  return process.platform === 'win32' && existsSync(gitBash) ? gitBash : 'bash';
}

function toBashPath(path, bashCommand) {
  if (process.platform !== 'win32' || !/Git\\bin\\bash\.exe$/i.test(bashCommand)) {
    return path;
  }
  return path.replace(/^([A-Za-z]):/, (_, drive) => `/${drive.toLowerCase()}`)
    .replaceAll('\\', '/');
}

function configureGitBashPython(environment, bashCommand) {
  const msysPythonDirectory = 'C:\\msys64\\ucrt64\\bin';
  if (process.platform === 'win32' && /Git\\bin\\bash\.exe$/i.test(bashCommand)
    && existsSync(join(msysPythonDirectory, 'python3.exe'))) {
    environment.Path = `${msysPythonDirectory};${environment.Path || environment.PATH || ''}`;
  }
}

function gitRevision() {
  const result = spawnSync('git', ['rev-parse', 'HEAD'], {
    cwd: repositoryRoot,
    encoding: 'utf8',
    windowsHide: true
  });
  const gitSha = result.status === 0 ? result.stdout.trim() : '';
  if (!/^[0-9a-f]{40}$/.test(gitSha)) {
    throw new Error('could not resolve the current full Git SHA');
  }
  return gitSha;
}

async function choosePort(rawPort) {
  if (rawPort) {
    const port = Number(rawPort);
    if (!Number.isInteger(port) || port < 1000 || port > 65535) {
      throw new Error('E2E_THREE_SERVICE_GATEWAY_PORT must be an integer from 1000 to 65535');
    }
    await assertPortAvailable(port);
    return port;
  }
  return new Promise((resolvePort, rejectPort) => {
    const server = createServer();
    server.once('error', rejectPort);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      server.close((error) => error ? rejectPort(error) : resolvePort(port));
    });
  });
}

async function assertPortAvailable(port) {
  await new Promise((resolvePort, rejectPort) => {
    const server = createServer();
    server.once('error', () => rejectPort(new Error(`gateway port ${port} is already in use`)));
    server.listen(port, '127.0.0.1', () => server.close(resolvePort));
  });
}

async function run(command, args, env) {
  await new Promise((resolveRun, rejectRun) => {
    const child = spawn(command, args, {
      cwd: repositoryRoot,
      env,
      stdio: 'inherit',
      windowsHide: true
    });
    child.once('error', rejectRun);
    child.once('exit', (code, signal) => {
      if (code === 0) {
        resolveRun();
      } else {
        rejectRun(new Error(`${command} exited with ${signal || `code ${code}`}`));
      }
    });
  });
}
