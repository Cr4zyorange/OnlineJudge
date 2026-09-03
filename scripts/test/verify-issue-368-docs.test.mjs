#!/usr/bin/env node

import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import {
  authoritativeInputChanges,
  findLocalLinkGaps,
  normalizePackageText,
  resetGeneratedRoots,
} from '../delivery/issue-368-package.mjs';

const root = resolve(import.meta.dirname, '../..');

test('Issue 368 documentation package satisfies its fail-closed delivery contract', () => {
  const result = spawnSync(process.execPath, ['scripts/delivery/verify-issue-368-docs.mjs', '--root', root], {
    cwd: root,
    encoding: 'utf8',
  });

  assert.equal(result.status, 0, `${result.stdout}\n${result.stderr}`);
  assert.match(result.stdout, /PASS required_files=12 scenarios=24 apis=124\/124 render=115\/115 pdf_pages=545\/545 blocked=3/);
});

test('Issue 368 package helpers preserve evidence and normalize tracked text to LF', () => {
  const fixture = mkdtempSync(join(tmpdir(), 'issue-368-package-'));
  try {
    const editable = join(fixture, 'editable');
    const evidence = join(fixture, 'evidence');
    mkdirSync(editable, { recursive: true });
    mkdirSync(evidence, { recursive: true });
    writeFileSync(join(editable, 'document.md'), 'first\r\nsecond\r\n', 'utf8');
    writeFileSync(join(evidence, 'pdf-page-audit.json'), '{\r\n  "status": "PASS"\r\n}\r\n', 'utf8');
    writeFileSync(join(evidence, 'verification.log'), 'verified\r\n', 'utf8');
    writeFileSync(join(evidence, 'render.log'), 'stale\r\n', 'utf8');

    resetGeneratedRoots([editable, evidence], evidence, ['pdf-page-audit.json', 'verification.log']);
    assert.equal(readFileSync(join(evidence, 'pdf-page-audit.json'), 'utf8'), '{\r\n  "status": "PASS"\r\n}\r\n');
    assert.equal(readFileSync(join(evidence, 'verification.log'), 'utf8'), 'verified\r\n');
    assert.equal(existsSync(join(evidence, 'render.log')), false);

    writeFileSync(join(editable, 'document.md'), 'first\r\nsecond\r\n', 'utf8');
    normalizePackageText(fixture);
    assert.equal(readFileSync(join(editable, 'document.md'), 'utf8'), 'first\nsecond\n');
    assert.equal(readFileSync(join(evidence, 'verification.log'), 'utf8'), 'verified\n');
  } finally {
    rmSync(fixture, { recursive: true, force: true });
  }
});

test('Issue 368 frozen link audit resolves links from the archived document location', () => {
  const fixture = mkdtempSync(join(tmpdir(), 'issue-368-links-'));
  try {
    const finalRoot = join(fixture, 'editable', 'final');
    mkdirSync(finalRoot, { recursive: true });
    writeFileSync(join(finalRoot, '部署文档.md'), '[missing](../开发/D3-CICD-共享契约.md)\n', 'utf8');
    assert.deepEqual(findLocalLinkGaps([join(finalRoot, '部署文档.md')]), [
      '部署文档.md: missing local link ../开发/D3-CICD-共享契约.md',
    ]);
    mkdirSync(join(fixture, 'editable', '开发'), { recursive: true });
    writeFileSync(join(fixture, 'editable', '开发', 'D3-CICD-共享契约.md'), '# contract\n', 'utf8');
    assert.deepEqual(findLocalLinkGaps([join(finalRoot, '部署文档.md')]), []);
  } finally {
    rmSync(fixture, { recursive: true, force: true });
  }
});

test('Issue 368 base guard rejects authoritative input changes after the frozen revision', () => {
  const fixture = mkdtempSync(join(tmpdir(), 'issue-368-base-'));
  try {
    const runGit = (...args) => spawnSync('git', args, { cwd: fixture, encoding: 'utf8' });
    assert.equal(runGit('init').status, 0);
    assert.equal(runGit('config', 'user.email', 'issue-368@example.invalid').status, 0);
    assert.equal(runGit('config', 'user.name', 'Issue 368 Test').status, 0);
    mkdirSync(join(fixture, 'docs'), { recursive: true });
    writeFileSync(join(fixture, 'docs', 'source.md'), 'base\n', 'utf8');
    assert.equal(runGit('add', 'docs/source.md').status, 0);
    assert.equal(runGit('commit', '-m', 'test: frozen base').status, 0);
    const base = runGit('rev-parse', 'HEAD').stdout.trim();
    assert.deepEqual(authoritativeInputChanges(fixture, base, ['docs']), []);
    writeFileSync(join(fixture, 'docs', 'source.md'), 'changed\n', 'utf8');
    assert.deepEqual(authoritativeInputChanges(fixture, base, ['docs']), ['docs/source.md']);
    writeFileSync(join(fixture, 'docs', 'untracked.md'), 'untracked\n', 'utf8');
    assert.deepEqual(authoritativeInputChanges(fixture, base, ['docs']), ['docs/source.md', 'docs/untracked.md']);
  } finally {
    rmSync(fixture, { recursive: true, force: true });
  }
});
