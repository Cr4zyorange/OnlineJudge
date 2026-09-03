#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';
import { normalizePackageText } from './issue-368-package.mjs';

const root = resolve(import.meta.dirname, '../..');
const packageRoot = resolve(root, 'submission/02_docs');
normalizePackageText(packageRoot);

function walkFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? walkFiles(path) : [path];
  });
}

const files = walkFiles(packageRoot)
  .filter((path) => relative(packageRoot, path).replaceAll('\\', '/') !== 'SHA256SUMS')
  .sort((left, right) => left.localeCompare(right, 'en'));
const lines = files.map((path) => {
  const digest = createHash('sha256').update(readFileSync(path)).digest('hex');
  return `${digest}  ${relative(packageRoot, path).replaceAll('\\', '/')}`;
});

writeFileSync(resolve(packageRoot, 'SHA256SUMS'), `${lines.join('\n')}\n`, 'utf8');
console.log(`CHECKSUMS refreshed=${files.length}`);
