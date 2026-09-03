import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { gzipSync } from "node:zlib";

import { archiveRawBytes } from "../issue-307-archive-raw.mjs";
import { readJson } from "../issue-307.mjs";

test("raw evidence archive is lossless and records both checksums", () => {
  const source = Buffer.from('{"issue":307,"requests":[{"ok":false}]}\n', "utf8");
  const archived = archiveRawBytes(source);
  assert.deepEqual(archived.restored, source);
  assert.match(archived.uncompressedSha256, /^[0-9a-f]{64}$/);
  assert.match(archived.compressedSha256, /^[0-9a-f]{64}$/);
  assert.ok(archived.compressed.length > 0);
});

test("aggregator reader accepts a gzip-compressed raw evidence document", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "issue-307-archive-test-"));
  const file = path.join(directory, "round.json.gz");
  await writeFile(file, gzipSync(Buffer.from('{"issue":307,"requests":[]}\n', "utf8")));
  assert.deepEqual(await readJson(file), { issue: 307, requests: [] });
});
