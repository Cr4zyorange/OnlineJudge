import { timingSafeEqual } from 'node:crypto';
import { existsSync, readFileSync, realpathSync, statSync } from 'node:fs';
import { basename, isAbsolute, relative } from 'node:path';

type ThreeServiceProof = {
  token: string;
  baseUrl: string;
  projectName: string;
  workloads: number;
  contextPath: string;
  evidenceDir: string;
};

export function verifyThreeServiceDisposableProof(): boolean {
  const proofFile = process.env.E2E_THREE_SERVICE_PROOF_FILE?.trim();
  const suppliedToken = process.env.E2E_THREE_SERVICE_TOKEN?.trim();
  const baseUrl = process.env.E2E_BASE_URL?.trim();
  if (!proofFile || !suppliedToken || !baseUrl || !/^[0-9a-f]{64}$/.test(suppliedToken)) {
    return false;
  }

  try {
    const proofPath = realpathSync(proofFile);
    if (basename(proofPath) !== 'disposable-proof.json') {
      return false;
    }
    const proofStat = statSync(proofPath);
    if (process.platform !== 'win32' && ((proofStat.mode & 0o077) !== 0
      || (typeof process.getuid === 'function' && proofStat.uid !== process.getuid()))) {
      return false;
    }

    const proof = JSON.parse(readFileSync(proofPath, 'utf8')) as ThreeServiceProof;
    const supplied = Buffer.from(suppliedToken, 'utf8');
    const stored = Buffer.from(proof.token || '', 'utf8');
    if (stored.length !== supplied.length || !timingSafeEqual(stored, supplied)) {
      return false;
    }
    if (proof.baseUrl !== baseUrl || !/^http:\/\/127\.0\.0\.1:\d+$/.test(baseUrl)
      || proof.workloads !== 9 || !/^oj318-[a-z0-9-]+$/i.test(proof.projectName || '')) {
      return false;
    }

    const evidenceDir = realpathSync(proof.evidenceDir);
    const contextPath = realpathSync(proof.contextPath);
    const relativeContext = relative(evidenceDir, contextPath);
    if (relativeContext.startsWith('..') || isAbsolute(relativeContext)
      || !existsSync(contextPath)) {
      return false;
    }
    const context = JSON.parse(readFileSync(contextPath, 'utf8')) as Partial<ThreeServiceProof>;
    return context.baseUrl === baseUrl
      && context.workloads === 9
      && context.projectName === proof.projectName;
  } catch {
    return false;
  }
}
