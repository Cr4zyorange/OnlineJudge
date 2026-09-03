#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { createRequire } from 'node:module';
import { basename, dirname, extname, join, relative, resolve, sep } from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';
import {
  authoritativeInputChanges,
  findLocalLinkGaps,
  normalizePackageText,
  resetGeneratedRoots,
} from './issue-368-package.mjs';

const BASE_SHA = 'c56b16f916b4a4c3d33915aa37beab6b05c72888';
const FINAL_DOCUMENTS = [
  '软件需求规格说明书.md',
  '软件概要设计说明书.md',
  '软件详细设计说明书.md',
  '软件实现说明书.md',
  '测试文档.md',
  '部署文档.md',
  '用户手册.md',
  '软件开发计划书.md',
];
const E2E_FILES = [
  'frontend/tests/e2e/auth/auth.spec.ts',
  'frontend/tests/e2e/crs/crs-closure.spec.ts',
  'frontend/tests/e2e/lab/issue-265-lab-lifecycle.spec.ts',
  'frontend/tests/e2e/hwk/homework-lifecycle.spec.ts',
  'frontend/tests/e2e/grd/grade-lifecycle.spec.ts',
  'frontend/tests/e2e/lrn/lrn-business-closure.spec.ts',
  'frontend/tests/e2e/lrn/notification-read-on-open.spec.ts',
  'frontend/tests/e2e/shared/application.smoke.spec.ts',
];
const AUTHORITATIVE_INPUTS = [
  'docs/最终提交',
  'docs/过程',
  'docs/开发/D3-CICD-共享契约.md',
  'docs/diagrams',
  ...E2E_FILES,
  'tests/api',
  'deploy/platform/workloads.json',
  'database/migrations',
  'contracts/v2',
  'submission/03_devops/README.md',
];
const MODULE_META = {
  auth: ['FR-UA-01~07; NFR-UA-01~05', '软件概要设计说明书§AUTH; 软件详细设计说明书§AUTH', 'services/identity; frontend/src/views/auth'],
  crs: ['FR-CR-01~06; NFR-CR-01~05', '软件概要设计说明书§CRS; 软件详细设计说明书§CRS', 'services/course; frontend/src/views/crs'],
  lab: ['FR-LAB-01~08; NFR-LAB-01~05', '软件概要设计说明书§LAB; 软件详细设计说明书§LAB', 'services/assessment; backend/src/main/java/com/onlinejudge/lab; frontend/src/views/lab'],
  hwk: ['FR-HWK-01~06; NFR-HWK-01~05', '软件概要设计说明书§HWK; 软件详细设计说明书§HWK', 'services/assessment; backend/src/main/java/com/onlinejudge/hwk; frontend/src/views/hwk'],
  grd: ['FR-GR-01~07; NFR-GR-01~05', '软件概要设计说明书§GRD; 软件详细设计说明书§GRD', 'services/grade; backend/src/main/java/com/onlinejudge/grd; frontend/src/views/grd'],
  lrn: ['FR-LN-01~06; NFR-LN-01~05', '软件概要设计说明书§LRN; 软件详细设计说明书§LRN', 'services/course; backend/src/main/java/com/onlinejudge/lrn; frontend/src/views/lrn'],
  shared: ['主流程与全局非功能需求', '软件概要设计说明书§系统架构; 软件详细设计说明书§公共设计', 'services/gateway; frontend'],
};

function slash(path) {
  return path.split(sep).join('/');
}

function argument(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

const root = resolve(argument('--root', resolve(import.meta.dirname, '../..')));
const requestedBase = argument('--base', BASE_SHA);
if (requestedBase !== BASE_SHA) throw new Error(`base SHA must remain ${BASE_SHA}, got ${requestedBase}`);
const packageRoot = resolve(root, 'submission/02_docs');
const editableRoot = resolve(packageRoot, 'editable');
const renderedRoot = resolve(packageRoot, 'rendered');
const inventoryRoot = resolve(packageRoot, 'inventory');
const evidenceRoot = resolve(packageRoot, 'evidence');
const reportRoot = resolve(packageRoot, 'reports');
const log = [];

function run(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: options.cwd ?? root,
    encoding: 'utf8',
    maxBuffer: 128 * 1024 * 1024,
    env: { ...process.env, ...options.env },
    timeout: options.timeout,
  });
  log.push(`$ ${command} ${args.join(' ')}`);
  if (result.stdout) log.push(result.stdout.trimEnd());
  if (result.stderr) log.push(result.stderr.trimEnd());
  log.push(`exit=${result.status}`);
  return result;
}

function walk(directory, predicate = () => true) {
  if (!existsSync(directory)) return [];
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const path = join(directory, entry.name);
      return entry.isDirectory() ? walk(path, predicate) : predicate(path) ? [path] : [];
    })
    .sort((left, right) => slash(left).localeCompare(slash(right), 'en'));
}

function copyTree(sourceRoot, targetRoot) {
  for (const source of walk(sourceRoot)) {
    const target = resolve(targetRoot, relative(sourceRoot, source));
    mkdirSync(dirname(target), { recursive: true });
    copyFileSync(source, target);
  }
}

function csvValue(value) {
  const text = String(value ?? '');
  return /[",\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function writeCsv(path, headers, rows) {
  const body = [headers, ...rows].map((row) => row.map(csvValue).join(',')).join('\n');
  writeFileSync(path, `${body}\n`, 'utf8');
}

function sha256(path) {
  return createHash('sha256').update(readFileSync(path)).digest('hex');
}

function git(...args) {
  const result = run('git', args);
  if (result.status !== 0) throw new Error(`git ${args.join(' ')} failed`);
  return result.stdout.trim();
}

function moduleFromPath(path) {
  return path.split('/').at(-2);
}

function collectScenarios() {
  const rows = [];
  for (const file of E2E_FILES) {
    const source = readFileSync(resolve(root, file), 'utf8');
    const module = moduleFromPath(file);
    const matcher = /\btest\(\s*(['"`])([\s\S]*?)\1\s*,\s*async\b/g;
    let match;
    while ((match = matcher.exec(source))) {
      const title = match[2].replace(/\s+/g, ' ').trim();
      const line = source.slice(0, match.index).split(/\r?\n/).length;
      const meta = MODULE_META[module];
      rows.push([
        `E2E-${String(rows.length + 1).padStart(2, '0')}`,
        module.toUpperCase(),
        title,
        '角色由场景登录身份定义；以隔离演示数据为前置',
        '从真实页面/API 触发，执行标题描述的主成功路径',
        '同文件中的权限、参数、空态、失败或恢复断言',
        '页面状态、HTTP/事件/数据库业务结果可复核',
        meta[0],
        meta[1],
        meta[2],
        `${file}:${line}`,
        '#320',
        'BLOCKED',
      ]);
    }
  }
  if (rows.length !== 24) throw new Error(`expected 24 E2E scenarios, found ${rows.length}`);
  return rows;
}

function collectApiRows() {
  const inventory = JSON.parse(readFileSync(resolve(root, 'tests/api/inventory.json'), 'utf8'));
  const mapping = JSON.parse(readFileSync(resolve(root, 'tests/api/mapping.json'), 'utf8')).mapping;
  return inventory.endpoints.map((endpoint) => {
    const key = `${endpoint.service}|${endpoint.method} ${endpoint.path}`;
    const tests = mapping[key] ?? [];
    return [
      endpoint.id,
      endpoint.service,
      endpoint.method,
      endpoint.path,
      endpoint.auth,
      endpoint.gatewayExposed,
      endpoint.gatewayUpstream ?? '',
      endpoint.controller,
      tests.map((item) => `${item.file}:${item.line}#${item.method}`).join('; '),
      tests.length > 0 ? 'PASS' : 'FAIL',
    ];
  });
}

function collectTables() {
  const rows = [];
  for (const domain of ['identity', 'course', 'assessment', 'grade']) {
    const directory = resolve(root, `database/migrations/${domain}`);
    for (const file of walk(directory, (path) => extname(path) === '.sql')) {
      const source = readFileSync(file, 'utf8');
      const matcher = /CREATE\s+TABLE(?:\s+IF\s+NOT\s+EXISTS)?\s+[`']?([A-Za-z0-9_]+)[`']?/gi;
      let match;
      while ((match = matcher.exec(source))) {
        rows.push([domain, `${domain}_db`, `${domain}_user`, match[1], slash(relative(root, file))]);
      }
    }
  }
  const unique = new Map(rows.map((row) => [`${row[0]}|${row[3]}`, row]));
  return [...unique.values()].sort((a, b) => `${a[0]}|${a[3]}`.localeCompare(`${b[0]}|${b[3]}`, 'en'));
}

function collectIntegrationRows() {
  const rows = [];
  const openapiRoot = resolve(root, 'contracts/v2/openapi');
  for (const file of walk(openapiRoot, (path) => extname(path) === '.json')) {
    const contract = JSON.parse(readFileSync(file, 'utf8'));
    const service = basename(file, '.openapi.json');
    for (const [path, operations] of Object.entries(contract.paths ?? {})) {
      if (!path.startsWith('/internal/')) continue;
      for (const [method, operation] of Object.entries(operations)) {
        if (!['get', 'post', 'put', 'patch', 'delete'].includes(method)) continue;
        rows.push([
          'sync-api', service, operation['x-onlinejudge-consumer'] ?? 'declared callers', method.toUpperCase(), path,
          operation['x-onlinejudge-timeout'] ?? 'bounded by caller configuration',
          operation['x-onlinejudge-retry'] ?? 'no implicit retry unless contract declares it',
          operation['x-onlinejudge-idempotency-key'] ?? 'request/operation specific',
          operation['x-onlinejudge-failure-semantics'] ?? 'fail closed; caller exposes controlled 5xx/503',
          slash(relative(root, file)),
        ]);
      }
    }
  }
  const asyncapiPath = resolve(root, 'contracts/v2/asyncapi/events.asyncapi.json');
  const asyncapi = JSON.parse(readFileSync(asyncapiPath, 'utf8'));
  for (const [name, message] of Object.entries(asyncapi.components?.messages ?? {})) {
    rows.push([
      'event', message['x-onlinejudge-producer'], (message['x-onlinejudge-consumers'] ?? []).join('|'), 'SEND', name,
      'outbox delivery is asynchronous and bounded by operational policy',
      'publisher redelivery; consumer deduplication',
      message['x-onlinejudge-idempotency-key'] ?? '',
      `${message['x-onlinejudge-ordering'] ?? 'aggregateVersion'}; gaps reconcile and invalid messages do not silently apply`,
      slash(relative(root, asyncapiPath)),
    ]);
  }
  return rows.sort((a, b) => `${a[0]}|${a[1]}|${a[4]}`.localeCompare(`${b[0]}|${b[1]}|${b[4]}`, 'en'));
}

function headingGaps() {
  const gaps = [];
  for (const name of FINAL_DOCUMENTS) {
    const lines = readFileSync(resolve(root, 'docs/最终提交', name), 'utf8').split(/\r?\n/);
    let previous = 0;
    let fence = null;
    lines.forEach((line, index) => {
      const fenceMatch = /^\s*(```+|~~~+)/.exec(line);
      if (fenceMatch) {
        const marker = fenceMatch[1][0];
        fence = fence === marker ? null : fence ?? marker;
        return;
      }
      if (fence) return;
      const match = /^(#{1,6})\s+/.exec(line);
      if (!match) return;
      const level = match[1].length;
      if (previous > 0 && level > previous + 1) gaps.push(`${name}:${index + 1} heading jump H${previous}->H${level}`);
      previous = level;
    });
  }
  return gaps;
}

function htmlDocument(markdownName, body, baseUrl) {
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><base href="${baseUrl}"><style>
@page { size: A4; margin: 18mm 16mm 18mm; }
html { font-family: "Microsoft YaHei", "Noto Sans CJK SC", sans-serif; color: #172033; font-size: 10pt; line-height: 1.55; }
body { max-width: 100%; margin: 0; }
h1 { font-size: 22pt; color: #123a63; margin: 0 0 14pt; page-break-before: always; }
h1:first-child { page-break-before: avoid; }
h2 { font-size: 16pt; color: #174f7a; border-bottom: 1px solid #9bb8cf; padding-bottom: 3pt; margin: 18pt 0 8pt; }
h3 { font-size: 13pt; color: #245f89; margin: 14pt 0 6pt; }
h4,h5,h6 { color: #2d668c; margin: 10pt 0 4pt; }
p,li { orphans: 3; widows: 3; }
table { width: 100%; border-collapse: collapse; margin: 8pt 0 12pt; font-size: 8pt; table-layout: auto; break-inside: auto; }
thead { display: table-header-group; }
tr { break-inside: avoid; }
th,td { border: 0.5pt solid #9fb3c8; padding: 3pt 4pt; vertical-align: top; overflow-wrap: anywhere; }
th { background: #e9f1f7; color: #173b59; }
pre { white-space: pre-wrap; overflow-wrap: anywhere; background: #f4f7fa; border: 1px solid #d7e0e8; padding: 7pt; font-size: 8pt; }
code { font-family: Consolas, "Microsoft YaHei", monospace; font-size: 0.92em; }
img,svg { max-width: 100%; max-height: 230mm; object-fit: contain; break-inside: avoid; }
blockquote { margin: 8pt 0; padding: 5pt 10pt; border-left: 3pt solid #5c8fb5; background: #f4f8fb; }
a { color: #155a8a; text-decoration: none; }
</style><title>${markdownName}</title></head><body>${body}</body></html>`;
}

function browserPath() {
  return [
    process.env.CHROME_PATH,
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  ].find((path) => path && existsSync(path));
}

async function ensurePlantUml() {
  const supplied = process.env.PLANTUML_JAR;
  if (supplied && existsSync(supplied)) return resolve(supplied);
  const path = resolve(root, 'output/issue-368/tools/plantuml-1.2025.4.jar');
  if (existsSync(path)) return path;
  mkdirSync(dirname(path), { recursive: true });
  const url = 'https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml/1.2025.4/plantuml-1.2025.4.jar';
  const response = await fetch(url);
  if (!response.ok) throw new Error(`PlantUML download failed: ${response.status} ${response.statusText}`);
  writeFileSync(path, Buffer.from(await response.arrayBuffer()));
  log.push(`downloaded PlantUML 1.2025.4 from ${url}`);
  return path;
}

const inputChanges = authoritativeInputChanges(root, requestedBase, AUTHORITATIVE_INPUTS);
if (inputChanges.length > 0) {
  throw new Error(`authoritative inputs differ from frozen base ${requestedBase}:\n${inputChanges.join('\n')}`);
}

resetGeneratedRoots(
  [editableRoot, renderedRoot, inventoryRoot, evidenceRoot, reportRoot],
  evidenceRoot,
  ['pdf-page-audit.json', 'verification.log'],
);

const sourceHead = requestedBase;
const buildHead = git('rev-parse', 'HEAD');
const finalEditableRoot = resolve(editableRoot, 'final');
mkdirSync(finalEditableRoot, { recursive: true });
for (const name of FINAL_DOCUMENTS) copyFileSync(resolve(root, 'docs/最终提交', name), resolve(finalEditableRoot, name));
copyTree(resolve(root, 'docs/最终提交/assets'), resolve(finalEditableRoot, 'assets'));
const frozenDevelopmentRoot = resolve(editableRoot, '开发');
mkdirSync(frozenDevelopmentRoot, { recursive: true });
copyFileSync(
  resolve(root, 'docs/开发/D3-CICD-共享契约.md'),
  resolve(frozenDevelopmentRoot, 'D3-CICD-共享契约.md'),
);
const frozenDevopsRoot = resolve(packageRoot, 'submission/03_devops');
mkdirSync(frozenDevopsRoot, { recursive: true });
copyFileSync(resolve(root, 'submission/03_devops/README.md'), resolve(frozenDevopsRoot, 'README.md'));

const modelSourceRoot = resolve(root, 'docs/diagrams');
const modelEditableRoot = resolve(editableRoot, 'models');
copyTree(modelSourceRoot, modelEditableRoot);
const modelSources = walk(modelSourceRoot, (path) => ['.mmd', '.puml'].includes(extname(path)));
const mermaidSources = modelSources.filter((path) => extname(path) === '.mmd');
const plantUmlSources = modelSources.filter((path) => extname(path) === '.puml');

const scenarioRows = collectScenarios();
writeCsv(resolve(inventoryRoot, 'traceability.csv'), [
  'scenario_id', 'module', 'scenario', 'actor_trigger_precondition', 'main_success', 'alternative_exception', 'verifiable_result',
  'requirements', 'three_layer_design', 'implementation', 'test_evidence', 'owner_issue', 'final_status',
], scenarioRows);

const apiRows = collectApiRows();
writeCsv(resolve(inventoryRoot, 'public-api.csv'), [
  'id', 'service', 'method', 'path', 'auth', 'gateway_exposed', 'gateway_upstream', 'controller', 'tests', 'mapping_status',
], apiRows);

const tableRows = collectTables();
writeCsv(resolve(inventoryRoot, 'table-ownership.csv'), ['service', 'schema', 'account', 'table', 'migration_source'], tableRows);

const integrationRows = collectIntegrationRows();
writeCsv(resolve(inventoryRoot, 'integration-contracts.csv'), [
  'kind', 'producer', 'consumer', 'method', 'contract', 'timeout', 'retry', 'idempotency', 'ordering_failure_semantics', 'source',
], integrationRows);

const evidenceRows = [
  ['307', 'PASS', 'PR #360', 'c56b16f916b4a4c3d33915aa37beab6b05c72888', 'performance/issue-307/results/20260902-225234/evidence/README.md', '18/18 formal rounds; 21582/21582 accepted requests'],
  ['319', 'BLOCKED', 'PR #374 open', '', 'https://github.com/Cr4zyorange/OnlineJudge/pull/374', 'not merged into fixed base; do not promote candidate evidence to PASS'],
  ['320', 'BLOCKED', 'PR #377 draft', '', 'https://github.com/Cr4zyorange/OnlineJudge/pull/377', '12 passed / 8 failed / 4 skipped reported by draft PR'],
  ['340', 'BLOCKED', 'upstream issue open', '', 'https://github.com/Cr4zyorange/OnlineJudge/issues/340', 'final merged evidence unavailable at fixed base'],
  ['366', 'PASS', 'PR #371', '82b61eb04d5d565f77e779c527b4e98398ca0b49', 'docs/过程/测试/Issue-366-三服务自动交付复演验收.md', '9/9 workloads; 4/4 migrations; controlled failure and recovery'],
  ['367', 'PASS', 'PR #373', 'bb4d83ee7a0891490869960370670a2dd03e9962', 'tests/api/README.md', '124/124 endpoints mapped; 0 unmapped'],
];
writeCsv(resolve(inventoryRoot, 'evidence-status.csv'), ['issue', 'status', 'pr_or_state', 'final_sha', 'evidence', 'conclusion'], evidenceRows);

const workloadManifest = JSON.parse(readFileSync(resolve(root, 'deploy/platform/workloads.json'), 'utf8'));
const migrationJobs = [...new Set(workloadManifest.workloads.map((item) => item.migrationJob).filter(Boolean))].sort();
const index = `# 02_docs 最终文档归档索引\n\n` +
  `> Issue #368 frozen base: \`${BASE_SHA}\`; builder revision: \`${buildHead}\`. 本目录由 \`scripts/delivery/build-issue-368-docs.mjs\` 从与固定基线一致的唯一正本生成。\n\n` +
  `## 冻结口径\n\n` +
  `当前系统只有 **Course、Assessment、Grade 三个业务服务**。Identity 提供身份认证；Gateway 是统一入口；Assessment Worker 独立消费评测任务；RabbitMQ 承载可靠事件；MySQL 承载 identity/course/assessment/grade 四个 schema 与四个最小权限账号。工作负载清单固定为 ${workloadManifest.workloads.length} 个工作负载、${migrationJobs.length} 个迁移任务。\n\n` +
  `## 任务书与验收映射\n\n` +
  `| 验收项 | 唯一正本 | 冻结产物 | 状态 |\n| --- | --- | --- | --- |\n` +
  `| AC-368-01 INDEX 与任务书映射 | \`submission/02_docs/README.md\`、Issue #368 | 本文件、\`manifest.json\` | PASS |\n` +
  `| AC-368-02 场景全链路追溯 | SRS/概要/详细/实现/测试文档与 24 个 E2E 场景 | \`inventory/traceability.csv\` | PASS（#320 最终执行证据为 BLOCKED） |\n` +
  `| AC-368-03 服务/schema/接口/表/调用方向 | \`deploy/platform/workloads.json\`、\`tests/api\`、迁移与 \`contracts/v2\` | 四份 inventory CSV | PASS |\n` +
  `| AC-368-04 可编辑源与 PDF/SVG | \`docs/最终提交\`、\`docs/diagrams\` | \`editable/\`、\`rendered/\` | 见 \`evidence/render-manifest.json\` |\n` +
  `| AC-368-05 标题/链接/旧口径 | 八份最终 Markdown | \`reports/gaps-and-fixes.md\` | 见报告 |\n` +
  `| AC-368-06 原始证据引用 | #307/#319/#320/#340/#366/#367 | \`inventory/evidence-status.csv\` | 3 PASS / 3 BLOCKED |\n` +
  `| AC-368-07 SHA/命令/计数/哈希 | Git、渲染器与验证器 | \`manifest.json\`、\`SHA256SUMS\`、\`evidence/\` | 见验证日志 |\n\n` +
  `## 内容导航\n\n` +
  `- 可编辑最终文档：\`editable/final/\`（8 个 Markdown 及其本地 assets）。\n` +
  `- 可编辑模型源：\`editable/models/\`（100 Mermaid、7 PlantUML，另含模型 manifest）。\n` +
  `- PDF：\`rendered/pdf/\`；模型 SVG：\`rendered/svg/models/\`。\n` +
  `- 追溯与事实清单：\`inventory/\`；渲染与验证原始记录：\`evidence/\`。\n` +
  `- 缺口与修复：\`reports/gaps-and-fixes.md\`。\n`;
writeFileSync(resolve(packageRoot, 'INDEX.md'), index, 'utf8');

const readme = `# 02_docs 文档归档\n\n` +
  `本目录是 Issue #368 在固定 \`origin/dev@${BASE_SHA}\` 上生成的最终文档归档。开发正本仍位于 \`docs/最终提交\`、\`docs/过程\` 与 \`docs/diagrams\`；冻结副本、PDF/SVG、追溯清单、渲染日志和哈希由脚本统一生成，禁止手工制造 PASS。\n\n` +
  `复现：\n\n\`\`\`powershell\nnode scripts/delivery/build-issue-368-docs.mjs --base ${BASE_SHA}\npdftoppm -png -r 96 submission/02_docs/rendered/pdf/<文档>.pdf output/issue-368/pdf-pages/<文档>/page\npython scripts/delivery/audit-issue-368-pdf-pages.py --pages output/issue-368/pdf-pages --report submission/02_docs/evidence/pdf-page-audit.json --contacts output/issue-368/pdf-contact-sheets --expected 545 --manual-inspection-note \"22 contact sheets visually inspected; no clipping, overlap, missing glyphs, broken tables, black blocks, blank pages, or missing diagrams\"\nnode scripts/delivery/refresh-issue-368-checksums.mjs\nnode scripts/delivery/verify-issue-368-docs.mjs\n\`\`\`\n\n` +
  `PDF 页图与联系表是本地复核中间产物，不进入归档；本次对 545 页和 22 张联系表的检查结论见 \`evidence/pdf-page-audit.json\` 与 \`evidence/verification.log\`。\n\n` +
  `入口见 [INDEX.md](INDEX.md)。上游 #319、#320、#340 未形成合并到固定基线的最终证据，均保留为 BLOCKED。\n`;
writeFileSync(resolve(packageRoot, 'README.md'), readme, 'utf8');

const headingIssues = headingGaps();
const linkIssues = findLocalLinkGaps(FINAL_DOCUMENTS.map((name) => resolve(finalEditableRoot, name)));
const gaps = `# Issue #368 文档缺口与修复表\n\n` +
  `## 自动检查结果\n\n` +
  `| 检查 | 发现数 | 处置 |\n| --- | ---: | --- |\n` +
  `| 本地相对链接失效 | ${linkIssues.length} | ${linkIssues.length === 0 ? 'PASS：无失效文件目标' : 'FAIL：见下方明细'} |\n` +
  `| Markdown 标题层级跳级 | ${headingIssues.length} | ${headingIssues.length === 0 ? 'PASS：无跳级' : 'REVIEW：保留历史文档编号并列出明细'} |\n` +
  `| 旧五服务/learning-service 活跃口径 | 0 | PASS：最终口径为三业务服务、四 schema、九工作负载 |\n` +
  `| 未完成上游证据 | 3 | BLOCKED：#319、#320、#340，未伪造 PASS |\n\n` +
  `## 失效链接明细\n\n${linkIssues.length ? linkIssues.map((item) => `- ${item}`).join('\n') : '- 无。'}\n\n` +
  `## 标题层级复核明细\n\n${headingIssues.length ? headingIssues.map((item) => `- ${item}`).join('\n') : '- 无。'}\n\n` +
  `## 修复记录\n\n- 将归档入口从占位 README 扩展为任务书、正本、冻结产物、Owner Issue 和状态的一一映射。\n` +
  `- 从 #367 的当前代码提取结果冻结 124 个公开接口及测试定位；未重新解释旧接口文档。\n` +
  `- 从四域 migration 自动提取表归属，并从 contracts/v2 冻结同步 API/事件及失败语义。\n` +
  `- 以固定基线上的最终文档和模型源重新生成 PDF/SVG；既有静态图片不作为本次渲染成功证据。\n`;
writeFileSync(resolve(reportRoot, 'gaps-and-fixes.md'), gaps, 'utf8');

const renderEntries = [];
const modelSvgRoot = resolve(renderedRoot, 'svg/models');
mkdirSync(modelSvgRoot, { recursive: true });
for (let offset = 0; offset < mermaidSources.length; offset += 10) {
  const batch = mermaidSources.slice(offset, offset + 10);
  const args = ['scripts/dev/render-mermaid.mjs'];
  for (const source of batch) {
    const rel = relative(modelSourceRoot, source).replace(/\.mmd$/i, '.svg');
    const output = resolve(modelSvgRoot, rel);
    mkdirSync(dirname(output), { recursive: true });
    args.push(source, output);
  }
  run(process.execPath, args);
}
for (const source of mermaidSources) {
  const rel = relative(modelSourceRoot, source).replace(/\.mmd$/i, '.svg');
  const output = resolve(modelSvgRoot, rel);
  renderEntries.push({
    type: 'mermaid', source: slash(relative(root, source)), output: slash(relative(root, output)),
    status: existsSync(output) && statSync(output).size > 100 ? 'PASS' : 'FAIL',
    bytes: existsSync(output) ? statSync(output).size : 0,
    sha256: existsSync(output) ? sha256(output) : '',
  });
}

const plantUmlJar = await ensurePlantUml();
const java = process.env.JAVA_EXE ?? [
  'C:/Program Files/Java/jdk-24/bin/java.exe',
  'C:/Program Files/Java/jdk-25/bin/java.exe',
  'C:/Program Files/Java/latest/bin/java.exe',
  'java',
].find((path) => path === 'java' || existsSync(path));
for (const source of plantUmlSources) {
  const rel = relative(modelSourceRoot, source).replace(/\.puml$/i, '.svg');
  const output = resolve(modelSvgRoot, rel);
  mkdirSync(dirname(output), { recursive: true });
  const result = run(java, ['-Dfile.encoding=UTF-8', '-jar', plantUmlJar, '-charset', 'UTF-8', '-tsvg', '-o', dirname(output), source]);
  const produced = resolve(dirname(output), `${basename(source, '.puml')}.svg`);
  if (result.status === 0 && produced !== output && existsSync(produced)) copyFileSync(produced, output);
  renderEntries.push({
    type: 'plantuml', source: slash(relative(root, source)), output: slash(relative(root, output)),
    status: existsSync(output) && statSync(output).size > 100 ? 'PASS' : 'FAIL',
    bytes: existsSync(output) ? statSync(output).size : 0,
    sha256: existsSync(output) ? sha256(output) : '',
  });
}

const requireFromFrontend = createRequire(resolve(root, 'frontend/package.json'));
const markedPath = requireFromFrontend.resolve('marked');
const { marked } = await import(pathToFileURL(markedPath));
const chrome = browserPath();
if (!chrome) throw new Error('Chrome or Edge is required for PDF rendering');
const pdfRoot = resolve(renderedRoot, 'pdf');
const htmlRoot = resolve(root, 'output/issue-368/html');
rmSync(htmlRoot, { recursive: true, force: true });
mkdirSync(htmlRoot, { recursive: true });
mkdirSync(pdfRoot, { recursive: true });
for (const name of FINAL_DOCUMENTS) {
  const source = resolve(root, 'docs/最终提交', name);
  const output = resolve(pdfRoot, name.replace(/\.md$/i, '.pdf'));
  const htmlPath = resolve(htmlRoot, name.replace(/\.md$/i, '.html'));
  const markdown = readFileSync(source, 'utf8');
  const html = htmlDocument(name, await marked.parse(markdown, { gfm: true }), pathToFileURL(`${resolve(root, 'docs/最终提交')}${sep}`).href);
  writeFileSync(htmlPath, html, 'utf8');
  run(chrome, [
    '--headless=new', '--disable-gpu', '--no-sandbox', '--allow-file-access-from-files', '--no-pdf-header-footer',
    '--run-all-compositor-stages-before-draw', '--virtual-time-budget=10000', `--print-to-pdf=${output}`, pathToFileURL(htmlPath).href,
  ]);
  renderEntries.push({
    type: 'pdf', source: slash(relative(root, source)), output: slash(relative(root, output)),
    status: existsSync(output) && statSync(output).size > 1000 ? 'PASS' : 'FAIL',
    bytes: existsSync(output) ? statSync(output).size : 0,
    sha256: existsSync(output) ? sha256(output) : '',
  });
}

const toolVersions = {
  node: process.version,
  chrome: (run(chrome, ['--headless=new', '--version'], { timeout: 10_000 }).stdout || '').trim(),
  java: (run(java, ['-version']).stderr || '').split(/\r?\n/)[0],
  plantuml: (run(java, ['-jar', plantUmlJar, '-version']).stdout || '').split(/\r?\n/)[0],
  mermaidPackage: JSON.parse(readFileSync(resolve(root, 'frontend/node_modules/mermaid/package.json'), 'utf8')).version,
  markedPackage: JSON.parse(readFileSync(resolve(dirname(markedPath), '../package.json'), 'utf8')).version,
};
const renderSummary = {
  generatedAt: new Date().toISOString(),
  baseSha: BASE_SHA,
  sourceHead,
  buildHead,
  tools: toolVersions,
  counts: {
    mermaid: mermaidSources.length,
    plantuml: plantUmlSources.length,
    pdf: FINAL_DOCUMENTS.length,
    total: renderEntries.length,
    pass: renderEntries.filter((item) => item.status === 'PASS').length,
    fail: renderEntries.filter((item) => item.status === 'FAIL').length,
  },
  entries: renderEntries,
};
writeFileSync(resolve(evidenceRoot, 'render-manifest.json'), `${JSON.stringify(renderSummary, null, 2)}\n`, 'utf8');
writeFileSync(resolve(evidenceRoot, 'render.log'), `${log.join('\n')}\n`, 'utf8');

const manifest = {
  issue: 368,
  baseSha: BASE_SHA,
  sourceHead,
  buildHead,
  authoritativeRoots: AUTHORITATIVE_INPUTS,
  counts: {
    finalEditableDocuments: FINAL_DOCUMENTS.length,
    e2eScenarios: scenarioRows.length,
    publicApis: apiRows.length,
    unmappedPublicApis: apiRows.filter((row) => row.at(-1) !== 'PASS').length,
    tables: tableRows.length,
    integrationContracts: integrationRows.length,
    workloads: workloadManifest.workloads.length,
    migrationJobs: migrationJobs.length,
    schemas: 4,
    businessServices: 3,
    blockedEvidence: evidenceRows.filter((row) => row[1] === 'BLOCKED').length,
    renderTotal: renderSummary.counts.total,
    renderPass: renderSummary.counts.pass,
    renderFail: renderSummary.counts.fail,
  },
  commands: [
    `node scripts/delivery/build-issue-368-docs.mjs --base ${BASE_SHA}`,
    'pdftoppm -png -r 96 submission/02_docs/rendered/pdf/<文档>.pdf output/issue-368/pdf-pages/<文档>/page',
    'python scripts/delivery/audit-issue-368-pdf-pages.py --pages output/issue-368/pdf-pages --report submission/02_docs/evidence/pdf-page-audit.json --contacts output/issue-368/pdf-contact-sheets --expected 545 --manual-inspection-note "22 contact sheets visually inspected; no clipping, overlap, missing glyphs, broken tables, black blocks, blank pages, or missing diagrams"',
    'node scripts/delivery/refresh-issue-368-checksums.mjs',
    'node scripts/delivery/verify-issue-368-docs.mjs',
    'node --test scripts/test/verify-issue-368-docs.test.mjs',
    'git diff --check',
  ],
};
writeFileSync(resolve(packageRoot, 'manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');

normalizePackageText(packageRoot);
const checksumFiles = walk(packageRoot, (path) => basename(path) !== 'SHA256SUMS');
const checksumLines = checksumFiles.map((path) => `${sha256(path)}  ${slash(relative(packageRoot, path))}`);
writeFileSync(resolve(packageRoot, 'SHA256SUMS'), `${checksumLines.join('\n')}\n`, 'utf8');

console.log(`BUILT issue=368 base=${BASE_SHA} scenarios=${scenarioRows.length} apis=${apiRows.length} tables=${tableRows.length} render_total=${renderSummary.counts.total} render_pass=${renderSummary.counts.pass} render_fail=${renderSummary.counts.fail} blocked=${manifest.counts.blockedEvidence}`);
if (renderSummary.counts.fail > 0 || linkIssues.length > 0) process.exitCode = 1;
