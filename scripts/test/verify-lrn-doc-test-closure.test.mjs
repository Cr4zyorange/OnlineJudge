import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const manifestUrl = new URL('docs/diagrams/lrn/manifest.json', root);
const expectedScenes = [
  'LRN-SC-01',
  'LRN-SC-02',
  'LRN-SC-03',
  'LRN-SC-04',
  'LRN-SC-05'
];
const expectedLayers = ['requirements', 'overview', 'detail'];

async function read(relativePath) {
  return readFile(new URL(relativePath, root), 'utf8');
}

test('LRN closure manifest maps every confirmed scenario to three renderable diagram layers', async () => {
  const manifest = JSON.parse(await readFile(manifestUrl, 'utf8'));

  assert.equal(manifest.formalUseCase, 'UC-LRN-01');
  assert.deepEqual(manifest.scenarios.map((scene) => scene.id), expectedScenes);
  for (const scene of manifest.scenarios) {
    assert.deepEqual(expectedLayers.every((layer) => Boolean(scene[layer])), true);
    for (const layer of expectedLayers) {
      const diagram = scene[layer];
      const source = await read(diagram.source);
      const asset = await read(diagram.asset);
      assert.match(source, /(?:sequenceDiagram|flowchart|stateDiagram-v2)/);
      assert.match(asset, /<svg[\s>]/);
    }
  }
});

test('LRN module documents expose the scenario catalogue, public subflows and diagram ids', async () => {
  const documents = await Promise.all([
    read('docs/最终提交/软件需求规格说明书.md'),
    read('docs/最终提交/软件概要设计说明书.md'),
    read('docs/最终提交/软件详细设计说明书.md')
  ]);
  const combined = documents.join('\n');

  for (const sceneId of expectedScenes) {
    assert.match(combined, new RegExp(sceneId));
  }
  for (const subflowId of ['LRN-SUB-01', 'LRN-SUB-02', 'LRN-SUB-03', 'LRN-SUB-04', 'LRN-SUB-05']) {
    assert.match(combined, new RegExp(subflowId));
  }
  assert.match(combined, /候选独立场景/);
  assert.doesNotMatch(combined, /UC-LRN-0[2-9]/);
});

test('LRN closure includes real cross-module integration, shared-runner E2E and current baseline evidence', async () => {
  const integrationTest = await read('backend/src/test/java/com/onlinejudge/integration/LrnCrossModuleEventIntegrationTest.java');
  const e2eTest = await read('frontend/tests/e2e/lrn/lrn-business-closure.spec.ts');
  const notificationE2eTest = await read('frontend/tests/e2e/lrn/notification-read-on-open.spec.ts');
  const baseline = await read('output/test/issue-262/README.md');
  const environment = await read('output/test/issue-262/environment.txt');
  const tst = await read('docs/过程/测试/TST-DOC-04 LRN 学习过程与通知提醒测试文档.md');

  assert.match(integrationTest, /LAB_EXPERIMENT_PUBLISHED/);
  assert.match(integrationTest, /HOMEWORK_PUBLISHED/);
  assert.match(integrationTest, /GRADE_PUBLISHED/);
  assert.match(e2eTest, /from '\.\.\/fixtures'/);
  assert.match(e2eTest, /@lrn/);
  assert.match(e2eTest, /beforeGradeNotificationIds/);
  assert.match(e2eTest, /describe\.configure\(\{ timeout: 60_000 \}\)/);
  assert.match(notificationE2eTest, /describe\.configure\(\{ timeout: 60_000 \}\)/);
  assert.match(e2eTest, /test\.info\(\)\.outputPath/);
  assert.match(notificationE2eTest, /test\.info\(\)\.outputPath/);
  assert.doesNotMatch(`${e2eTest}\n${notificationE2eTest}`, /output\/test\/issue-262\/evidence/);
  assert.match(baseline, /8f8e4fc70341c701c25786f12efbffaeca2a3c5f/);
  assert.match(baseline, /758afd98ba2caad5a00fb6e12413c48f0156b2fb/);
  assert.match(baseline, /PASS|FAIL|BLOCKED/);
  assert.match(environment, /^execution_sha=[0-9a-f]{40}$/m);
  assert.doesNotMatch(environment, /^execution_sha=758afd98ba2caad5a00fb6e12413c48f0156b2fb$/m);
  assert.match(tst, /45\/45 PASS/);
  assert.match(tst, /115\/115 PASS/);
  assert.match(tst, /LRN Playwright.*4\/4 PASS/);
});

test('requirements-layer LRN SSDs expose only actor-to-system interactions', async () => {
  const manifest = JSON.parse(await readFile(manifestUrl, 'utf8'));
  for (const scene of manifest.scenarios) {
    const source = await read(scene.requirements.source);
    assert.doesNotMatch(source, /System\s*-+>>\s*System/);
  }
});
