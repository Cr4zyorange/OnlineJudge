import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

test('the frozen context, fencing and deployment diagrams have the required semantics and really render', () => {
  const renderDirectory = mkdtempSync(join(tmpdir(), 'onlinejudge-final-architecture-305-mermaid-'));
  const diagrams = [
    {
      source: 'docs/diagrams/arch/issue305-five-service-context.mmd',
      output: 'context.svg',
      expected: [/Course -->\|authorization API\| Assessment/, /Course -->\|authorization API\| Grade/, /Assessment -->\|source-grade events\| Grade/, /Grade -->\|publication\/review events\| Learning/]
    },
    {
      source: 'docs/diagrams/arch/issue305-assessment-worker-fencing.mmd',
      output: 'fencing.svg',
      expected: [/conditional re-claim at next generation/, /stale sandbox output is fenced and discarded/, /conditional final write checks task, generation, owner, live lease/]
    },
    {
      source: 'docs/diagrams/arch/issue305-five-service-deployment.mmd',
      output: 'deployment.svg',
      expected: [/AssessmentWorker\[Assessment Worker\]/, /five schemas \/ five accounts/, /RabbitMQ --> AssessmentWorker\[Assessment Worker\]/, /Course --> ObjectStore/]
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
    for (const diagram of diagrams) {
      assert.match(readFileSync(join(renderDirectory, diagram.output), 'utf8'), /<svg[\s>]/);
    }
  } finally {
    rmSync(renderDirectory, { recursive: true, force: true });
  }
});
