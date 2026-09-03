#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import test from 'node:test';

const root = resolve(import.meta.dirname, '../..');

test('Issue 368 documentation package satisfies its fail-closed delivery contract', () => {
  const result = spawnSync(process.execPath, ['scripts/delivery/verify-issue-368-docs.mjs', '--root', root], {
    cwd: root,
    encoding: 'utf8',
  });

  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /PASS required_files=12 scenarios=24 apis=124\/124 render=115\/115 pdf_pages=545\/545 blocked=3/);
});
