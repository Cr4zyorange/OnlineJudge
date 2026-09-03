import { spawnSync } from 'node:child_process';
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { basename, dirname, extname, resolve } from 'node:path';

export const PACKAGE_TEXT_EXTENSIONS = new Set([
  '.csv', '.json', '.log', '.md', '.mmd', '.puml', '.svg', '.txt', '.xml', '.yaml', '.yml',
]);

function walkFiles(directory) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? walkFiles(path) : [path];
  });
}

export function normalizePackageText(packageRoot) {
  for (const path of walkFiles(packageRoot)) {
    if (!PACKAGE_TEXT_EXTENSIONS.has(extname(path).toLowerCase()) || statSync(path).size > 10_000_000) continue;
    const contents = readFileSync(path, 'utf8');
    const normalized = contents.replace(/\r\n?/g, '\n');
    if (normalized !== contents) writeFileSync(path, normalized, 'utf8');
  }
}

export function resetGeneratedRoots(roots, evidenceRoot, preservedEvidenceNames = []) {
  const preserved = new Map();
  for (const name of preservedEvidenceNames) {
    const path = resolve(evidenceRoot, name);
    if (existsSync(path)) preserved.set(name, readFileSync(path));
  }
  for (const path of roots) {
    rmSync(path, { recursive: true, force: true });
    mkdirSync(path, { recursive: true });
  }
  for (const [name, contents] of preserved) {
    const path = resolve(evidenceRoot, name);
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, contents);
  }
}

export function findLocalLinkGaps(documentPaths) {
  const gaps = [];
  for (const sourcePath of documentPaths) {
    const source = readFileSync(sourcePath, 'utf8');
    const matcher = /!?\[[^\]]*\]\(([^)]+)\)/g;
    let match;
    while ((match = matcher.exec(source))) {
      const target = match[1].trim().replace(/^<|>$/g, '').split('#')[0];
      if (!target || /^(?:https?:|mailto:|data:)/i.test(target)) continue;
      const decoded = decodeURIComponent(target);
      if (!existsSync(resolve(dirname(sourcePath), decoded))) {
        gaps.push(`${basename(sourcePath)}: missing local link ${target}`);
      }
    }
  }
  return gaps;
}

export function authoritativeInputChanges(root, base, paths) {
  const runGit = (...args) => {
    const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
    if (result.status !== 0) throw new Error(result.stderr || `git ${args.join(' ')} failed`);
    return result.stdout;
  };
  const tracked = runGit('diff', '--name-only', base, '--', ...paths).split(/\r?\n/).filter(Boolean);
  const status = runGit('status', '--porcelain=v1', '-z', '--untracked-files=all', '--', ...paths);
  const working = status.split('\0').filter(Boolean).map((entry) => entry.slice(3));
  return [...new Set([...tracked, ...working])].sort((left, right) => left.localeCompare(right, 'en'));
}
