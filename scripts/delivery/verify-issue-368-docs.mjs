#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
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
  'submission/02_docs/evidence/pdf-page-audit.json',
  'submission/02_docs/evidence/verification.log',
  'submission/02_docs/reports/gaps-and-fixes.md',
  'submission/02_docs/SHA256SUMS',
];

const missing = required.filter((path) => !existsSync(resolve(root, path)));
if (missing.length > 0) {
  for (const path of missing) console.error(`MISSING ${path}`);
  process.exit(1);
}

const readJson = (path) => JSON.parse(readFileSync(resolve(root, path), 'utf8'));
const manifest = readJson('submission/02_docs/manifest.json');
const render = readJson('submission/02_docs/evidence/render-manifest.json');
const pdfAudit = readJson('submission/02_docs/evidence/pdf-page-audit.json');
const failures = [];
const expect = (condition, message) => { if (!condition) failures.push(message); };

expect(manifest.issue === 368, 'manifest.issue must be 368');
expect(manifest.baseSha === 'c56b16f916b4a4c3d33915aa37beab6b05c72888', 'manifest.baseSha does not match the fixed origin/dev base');
expect(manifest.counts.finalEditableDocuments === 8, 'expected 8 editable final documents');
expect(manifest.counts.e2eScenarios === 24, 'expected 24 traced E2E scenarios');
expect(manifest.counts.publicApis === 124 && manifest.counts.unmappedPublicApis === 0, 'expected 124/124 public APIs mapped');
expect(manifest.counts.businessServices === 3, 'expected exactly 3 business services');
expect(manifest.counts.schemas === 4, 'expected exactly 4 schemas/accounts');
expect(manifest.counts.workloads === 9 && manifest.counts.migrationJobs === 4, 'expected 9 workloads and 4 migration jobs');
expect(manifest.counts.blockedEvidence === 3, 'expected exactly 3 BLOCKED upstream evidence rows');
expect(render.counts.mermaid === 100, 'expected 100 Mermaid renders');
expect(render.counts.plantuml === 7, 'expected 7 PlantUML renders');
expect(render.counts.pdf === 8, 'expected 8 PDF renders');
expect(render.counts.total === 115 && render.counts.pass === 115 && render.counts.fail === 0, 'expected render total/pass/fail = 115/115/0');
expect(render.entries.every((entry) => entry.status === 'PASS' && existsSync(resolve(root, entry.source)) && existsSync(resolve(root, entry.output))), 'every render entry must have existing source/output and PASS');
expect(pdfAudit.status === 'PASS', 'PDF page audit must be PASS');
expect(pdfAudit.expectedPages === 545 && pdfAudit.actualPages === 545, 'expected PDF page audit = 545/545 pages');
expect(pdfAudit.nearBlankPages.length === 0, 'PDF page audit found near-blank pages');
expect(pdfAudit.edgeContentPages.length === 0, 'PDF page audit found content touching a page edge');
expect(pdfAudit.contactSheets.length === 22, 'expected 22 visually inspected PDF contact sheets');
expect(pdfAudit.manualInspection?.status === 'PASS' && pdfAudit.manualInspection?.pages === 545 && pdfAudit.manualInspection?.contactSheets === 22, 'manual PDF inspection must cover 545 pages and 22 contact sheets');

const evidence = readFileSync(resolve(root, 'submission/02_docs/inventory/evidence-status.csv'), 'utf8');
for (const issue of ['319', '320', '340']) expect(new RegExp(`^${issue},BLOCKED,`, 'm').test(evidence), `issue #${issue} must remain BLOCKED`);
for (const issue of ['307', '366', '367']) expect(new RegExp(`^${issue},PASS,`, 'm').test(evidence), `issue #${issue} must be PASS with merged evidence`);

const index = readFileSync(resolve(root, 'submission/02_docs/INDEX.md'), 'utf8');
for (const term of ['Course、Assessment、Grade 三个业务服务', 'Identity', 'Gateway', 'Assessment Worker', 'RabbitMQ', 'MySQL', '四个 schema', '9 个工作负载']) {
  expect(index.includes(term), `INDEX missing canonical topology term: ${term}`);
}

const checksumPath = resolve(root, 'submission/02_docs/SHA256SUMS');
const checksumLines = readFileSync(checksumPath, 'utf8').trim().split(/\r?\n/);
const checksummed = new Set();
for (const line of checksumLines) {
  const match = /^([a-f0-9]{64})  (.+)$/.exec(line);
  expect(Boolean(match), `invalid SHA256SUMS line: ${line}`);
  if (!match) continue;
  const path = resolve(dirname(checksumPath), match[2]);
  checksummed.add(match[2]);
  expect(existsSync(path), `checksummed file missing: ${match[2]}`);
  if (existsSync(path)) {
    const actual = createHash('sha256').update(readFileSync(path)).digest('hex');
    expect(actual === match[1], `checksum mismatch: ${match[2]}`);
  }
}

function walkFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name);
    return entry.isDirectory() ? walkFiles(path) : [path];
  });
}

const packageRoot = resolve(root, 'submission/02_docs');
const packageFiles = walkFiles(packageRoot)
  .map((path) => relative(packageRoot, path).replaceAll('\\', '/'))
  .filter((path) => path !== 'SHA256SUMS');
for (const path of packageFiles) expect(checksummed.has(path), `package file missing from SHA256SUMS: ${path}`);
for (const path of checksummed) expect(packageFiles.includes(path), `SHA256SUMS contains an unexpected path: ${path}`);

const sensitivePatterns = [
  /gh[pousr]_[A-Za-z0-9]{20,}/,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /(?:token|cookie|authorization)\s*[:=]\s*(?:bearer\s+)?[A-Za-z0-9._~+\/-]{24,}/i,
];
const textExtensions = new Set(['.csv', '.json', '.log', '.md', '.mmd', '.puml', '.txt', '.xml', '.yaml', '.yml']);
for (const path of packageFiles) {
  const absolute = resolve(packageRoot, path);
  const extension = /\.[^.]+$/.exec(path)?.[0]?.toLowerCase();
  if (!textExtensions.has(extension) || statSync(absolute).size > 10_000_000) continue;
  const contents = readFileSync(absolute, 'utf8');
  for (const pattern of sensitivePatterns) expect(!pattern.test(contents), `possible sensitive value in ${path}: ${pattern}`);
}

const canonicalDocs = walkFiles(resolve(packageRoot, 'editable/final')).filter((path) => path.endsWith('.md'));
for (const path of canonicalDocs) {
  const lines = readFileSync(path, 'utf8').split(/\r?\n/);
  lines.forEach((line, index) => {
    const hasRetiredTopology = /五个业务服务|5\s*个业务服务|十个工作负载|10\s*个工作负载|独立\s*Learning\s*服务/i.test(line);
    const isExplicitNegation = /旧|历史|拒绝|无|不得|不再|删除|移除|残留/.test(line);
    expect(!hasRetiredTopology || isExplicitNegation, `retired topology presented as active in ${relative(root, path)}:${index + 1}`);
  });
}

if (failures.length > 0) {
  for (const failure of failures) console.error(`FAIL ${failure}`);
  process.exit(1);
}

console.log(`PASS required_files=${required.length} scenarios=24 apis=124/124 render=115/115 pdf_pages=545/545 blocked=3`);
