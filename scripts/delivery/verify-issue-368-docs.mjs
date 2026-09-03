#!/usr/bin/env node

import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

function repositoryRoot() {
  const rootIndex = process.argv.indexOf('--root');
  return rootIndex >= 0 ? resolve(process.argv[rootIndex + 1]) : resolve(import.meta.dirname, '../..');
}

const root = repositoryRoot();
const required = [
  'submission/02_docs/INDEX.md',
  'submission/02_docs/manifest.json',
  'submission/02_docs/inventory/traceability.csv',
  'submission/02_docs/inventory/public-api.csv',
  'submission/02_docs/inventory/table-ownership.csv',
  'submission/02_docs/inventory/integration-contracts.csv',
  'submission/02_docs/inventory/evidence-status.csv',
  'submission/02_docs/evidence/render-manifest.json',
  'submission/02_docs/reports/gaps-and-fixes.md',
  'submission/02_docs/SHA256SUMS',
];

const missing = required.filter((path) => !existsSync(resolve(root, path)));
if (missing.length > 0) {
  for (const path of missing) console.error(`MISSING ${path}`);
  process.exit(1);
}

console.log(`PASS required_files=${required.length}`);

