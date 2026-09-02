#!/usr/bin/env node

import { createHash } from "node:crypto";
import { readdir, readFile, unlink, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { gzipSync, gunzipSync } from "node:zlib";

function sha256(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

/** Compress one raw JSON document and prove the archive restores byte-for-byte. */
export function archiveRawBytes(source) {
  const input = Buffer.from(source);
  const compressed = gzipSync(input, { level: 9, mtime: 0 });
  const restored = gunzipSync(compressed);
  invariant(restored.equals(input), "gzip round-trip changed raw evidence");
  return {
    compressed,
    restored,
    uncompressedSha256: sha256(input),
    compressedSha256: sha256(compressed),
  };
}

async function listRawJson(directory) {
  const files = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...(await listRawJson(target)));
    } else if (entry.isFile() && entry.name.endsWith(".json") && entry.name !== "raw-manifest.json") {
      files.push(target);
    }
  }
  return files.sort();
}

export async function archiveRawDirectory(rawDirectory) {
  const root = path.resolve(rawDirectory);
  const files = await listRawJson(root);
  invariant(files.length > 0, "raw directory has no uncompressed JSON evidence");
  const entries = [];
  for (const file of files) {
    const source = await readFile(file);
    const archived = archiveRawBytes(source);
    const output = `${file}.gz`;
    await writeFile(output, archived.compressed);
    const verification = archiveRawBytes(await readFile(file));
    invariant(verification.uncompressedSha256 === archived.uncompressedSha256, "raw evidence changed while archiving");
    invariant(gunzipSync(await readFile(output)).equals(source), "written archive failed verification");
    await unlink(file);
    entries.push({
      file: path.relative(root, output),
      uncompressedBytes: source.length,
      compressedBytes: archived.compressed.length,
      uncompressedSha256: archived.uncompressedSha256,
      compressedSha256: archived.compressedSha256,
    });
  }
  const manifest = {
    schemaVersion: 1,
    issue: 307,
    archiveFormat: "gzip",
    entryCount: entries.length,
    entries,
  };
  await writeFile(path.join(root, "raw-manifest.json"), `${JSON.stringify(manifest, null, 2)}\n`, "utf8");
  return manifest;
}

function parseOptions(argumentsList) {
  invariant(argumentsList.length === 2 && argumentsList[0] === "--raw-dir", "usage: --raw-dir DIR");
  return { rawDir: argumentsList[1] };
}

if (process.argv[1] === new URL(import.meta.url).pathname) {
  archiveRawDirectory(parseOptions(process.argv.slice(2)).rawDir)
    .then((manifest) => process.stdout.write(`RAW_ARCHIVE_READY entries=${manifest.entryCount}\n`))
    .catch((error) => {
      process.stderr.write(`issue-307-archive-raw: ${error.message}\n`);
      process.exitCode = 2;
    });
}
