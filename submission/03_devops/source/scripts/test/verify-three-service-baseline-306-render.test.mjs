import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

test('the three-service context, fencing and deployment diagrams have the required semantics and really render', () => {
  const renderDirectory = mkdtempSync(join(tmpdir(), 'onlinejudge-three-service-baseline-306-mermaid-'));
  const diagrams = [
    {
      source: 'docs/diagrams/arch/issue306-three-service-context.mmd',
      output: 'context.svg',
      expected: [/Course -->\|authorization API\| Assessment/, /Course -->\|authorization API\| Grade/, /Assessment -->\|source-grade events\| Grade/, /Grade -->\|publication\/review facts\| Course/]
    },
    {
      source: 'docs/diagrams/arch/issue306-assessment-worker-fencing.mmd',
      output: 'fencing.svg',
      expected: [/claim increments generation/, /fenced final write: task, generation, owner, live lease/, /stale worker writes zero rows/]
    },
    {
      source: 'docs/diagrams/arch/issue306-three-service-deployment.mmd',
      output: 'deployment.svg',
      expected: [/AssessmentWorker\[Assessment Worker\]/, /four schemas \/ four accounts/, /RabbitMQ --> AssessmentWorker\[Assessment Worker\]/, /Gateway --> Course/]
    }
  ];
  try {
    for (const diagram of diagrams) {
      const contents = readFileSync(join(repoRoot, diagram.source), 'utf8');
      for (const expected of diagram.expected) assert.match(contents, expected);
    }
    const render = spawnSync(
      process.execPath,
      [
        join(repoRoot, 'scripts/dev/render-mermaid.mjs'),
        ...diagrams.flatMap((diagram) => [join(repoRoot, diagram.source), join(renderDirectory, diagram.output)])
      ],
      { cwd: repoRoot, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
    );
    assert.equal(render.status, 0, `${render.stdout}\n${render.stderr}`);
    for (const diagram of diagrams) assert.match(readFileSync(join(renderDirectory, diagram.output), 'utf8'), /<svg[\s>]/);
  } finally {
    rmSync(renderDirectory, { recursive: true, force: true });
  }
});
