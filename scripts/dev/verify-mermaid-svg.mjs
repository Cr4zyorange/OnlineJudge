#!/usr/bin/env node

import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '../..');
const requireFromFrontend = createRequire(join(repoRoot, 'frontend/package.json'));
const { JSDOM } = requireFromFrontend('jsdom');

const args = process.argv.slice(2);
if (args.length !== 2) {
  console.error('用法：node scripts/dev/verify-mermaid-svg.mjs <committedSvg> <renderedSvg>');
  process.exit(1);
}

function normalizeText(value) {
  return value.replace(/\s+/g, ' ').trim();
}

function isInsideMetadata(element) {
  for (let current = element; current; current = current.parentElement) {
    if (current.localName === 'defs' || current.localName === 'style') {
      return true;
    }
  }
  return false;
}

const semanticAttributeNames = [
  'data-et',
  'data-id',
  'data-type',
  'data-from',
  'data-to',
  'marker-start',
  'marker-end',
  'name',
];

function semanticAttributes(element) {
  return Object.fromEntries(
    semanticAttributeNames
      .filter((name) => element.hasAttribute(name))
      .map((name) => [name, element.getAttribute(name)])
  );
}

function semanticEntries(path) {
  const source = readFileSync(path, 'utf8');
  const dom = new JSDOM(source, { contentType: 'image/svg+xml' });
  const root = dom.window.document.documentElement;
  if (root.localName !== 'svg') {
    throw new Error(`${path} is not an SVG document`);
  }
  if (root.getAttribute('id') !== 'my-svg') {
    throw new Error(`${path} does not use the expected Mermaid SVG id`);
  }

  return [root, ...root.querySelectorAll('*')]
    .filter((element) => !isInsideMetadata(element))
    .map((element) => ({
      tag: element.localName,
      classes: [...element.classList].sort(),
      attributes: semanticAttributes(element),
      text:
        element.localName === 'text' || element.localName === 'foreignObject'
          ? normalizeText(element.textContent || '')
          : '',
    }));
}

function verifySemanticMatch(committedPath, renderedPath) {
  const committed = semanticEntries(committedPath);
  const rendered = semanticEntries(renderedPath);
  const entryCount = Math.max(committed.length, rendered.length);

  for (let index = 0; index < entryCount; index += 1) {
    const expected = committed[index];
    const actual = rendered[index];
    if (JSON.stringify(expected) !== JSON.stringify(actual)) {
      throw new Error(
        `semantic SVG mismatch at element ${index}: committed=${JSON.stringify(expected)} rendered=${JSON.stringify(actual)}`
      );
    }
  }
}

try {
  verifySemanticMatch(args[0], args[1]);
  console.log(`OK  semantic SVG match: ${args[0]}`);
} catch (error) {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
}
