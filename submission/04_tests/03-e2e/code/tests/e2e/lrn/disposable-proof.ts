import { timingSafeEqual } from 'node:crypto';
import { readFileSync, realpathSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, isAbsolute, relative } from 'node:path';
import { verifyThreeServiceDisposableProof } from '../three-service-disposable-proof';

type DisposableProof = {
  token: string;
  baseUrl: string;
  backendUrl: string;
  backendPid: number;
  frontendPid: number;
  databasePath: string;
};

export function verifyLrnDisposableProof(): boolean {
  if (verifyThreeServiceDisposableProof()) {
    return true;
  }
  const proofFile = process.env.E2E_LRN_DISPOSABLE_PROOF_FILE?.trim();
  const suppliedToken = process.env.E2E_LRN_DISPOSABLE_TOKEN?.trim();
  const baseUrl = process.env.E2E_BASE_URL?.trim();
  if (!proofFile || !suppliedToken || !baseUrl || !/^[0-9a-f]{64}$/.test(suppliedToken)) {
    return false;
  }

  try {
    const proofPath = realpathSync(proofFile);
    const tempRoot = realpathSync(tmpdir());
    const relativeProof = relative(tempRoot, proofPath);
    const proofDir = dirname(proofPath);
    if (relativeProof.startsWith('..') || isAbsolute(relativeProof)
      || basename(proofPath) !== 'disposable-proof.json'
      || !basename(proofDir).startsWith('onlinejudge-lrn-e2e-')) {
      return false;
    }

    const proofStat = statSync(proofPath);
    if (process.platform !== 'win32' && ((proofStat.mode & 0o077) !== 0
      || (typeof process.getuid === 'function' && proofStat.uid !== process.getuid()))) {
      return false;
    }

    const proof = JSON.parse(readFileSync(proofPath, 'utf8')) as DisposableProof;
    const supplied = Buffer.from(suppliedToken, 'utf8');
    const stored = Buffer.from(proof.token || '', 'utf8');
    if (stored.length !== supplied.length || !timingSafeEqual(stored, supplied)) {
      return false;
    }
    if (proof.baseUrl !== baseUrl
      || !/^http:\/\/127\.0\.0\.1:\d+$/.test(proof.baseUrl)
      || !/^http:\/\/127\.0\.0\.1:\d+$/.test(proof.backendUrl)
      || new URL(proof.baseUrl).port === new URL(proof.backendUrl).port) {
      return false;
    }

    const relativeDatabase = relative(proofDir, realpathSync(dirname(proof.databasePath)));
    if (relativeDatabase.startsWith('..') || isAbsolute(relativeDatabase)) {
      return false;
    }
    for (const pid of [proof.backendPid, proof.frontendPid]) {
      if (!Number.isSafeInteger(pid) || pid <= 0) {
        return false;
      }
      process.kill(pid, 0);
    }
    return true;
  } catch {
    return false;
  }
}
