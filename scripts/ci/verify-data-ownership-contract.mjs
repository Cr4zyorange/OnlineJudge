#!/usr/bin/env node

import { existsSync, lstatSync, readFileSync, realpathSync } from 'node:fs';
import { isAbsolute, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)));

const domains = {
  IDENTITY: 'oj_identity',
  COURSE: 'oj_course',
  ASSESSMENT: 'oj_assessment',
  GRADE: 'oj_grade',
  LEARNING: 'oj_learning'
};
const expectedOwners = Object.keys(domains).sort();
const requiredFiles = [
  'database/mysql/compose-schema.sql',
  'database/ownership/table-ownership.csv',
  'database/ownership/cross-domain-references.csv',
  'database/ownership/schema-account-matrix.csv',
  'database/ownership/service-local-tables.csv',
  'database/migrations/identity/DB-IDENTITY-01-identity-user-session.sql',
  'database/migrations/identity/DB-IDENTITY-02-service-token-idempotency.sql',
  'docs/开发/D6-DATA-五域数据所有权契约.md'
];
const ownershipFields = [
  'table', 'owner', 'schema', 'primary_key', 'external_ids', 'owner_constraints', 'retained_indexes', 'migration_strategy'
];
const referenceFields = ['consumer', 'consumer_table', 'column', 'target', 'replacement', 'contract', 'consistency'];
const accountFields = ['owner', 'schema', 'account', 'allow', 'deny', 'ddl'];
const localTableFields = ['owner', 'schema', 'table', 'kind', 'lifecycle', 'implementation_issue'];

function parseCsv(text, label) {
  const lines = text.trim().split(/\r?\n/).filter((line) => line && !line.startsWith('#'));
  if (lines.length < 2) throw new Error(`${label} must contain a header and at least one row`);
  const header = lines[0].split(',');
  return lines.slice(1).map((line, index) => {
    const values = line.split(',');
    if (values.length !== header.length) {
      throw new Error(`${label}:${index + 2} has ${values.length} columns; expected ${header.length}`);
    }
    return Object.fromEntries(header.map((name, column) => [name, values[column]]));
  });
}

function requireFields(entries, required, label, errors) {
  for (const field of required) {
    if (!entries.every((entry) => Object.hasOwn(entry, field))) errors.push(`${label} is missing required column ${field}`);
  }
}

function requireFile(rootPath, relativePath) {
  const absolutePath = resolve(rootPath, relativePath);
  if (!existsSync(absolutePath)) throw new Error(`missing ${relativePath}`);
  return readFileSync(absolutePath, 'utf8');
}

function schemaDefinitions(sql) {
  const definitions = new Map();
  const tableBlocks = sql.matchAll(/CREATE TABLE(?: IF NOT EXISTS)?\s+`?([A-Za-z0-9_]+)`?\s*\(([\s\S]*?)\);/gi);
  for (const [, table, body] of tableBlocks) {
    if (table === 'schema_migrations') continue;
    const columns = new Set();
    for (const line of body.split(/\r?\n/)) {
      const candidate = line.match(/^\s*`?([A-Za-z0-9_]+)`?\s+/)?.[1];
      if (candidate && !['PRIMARY', 'UNIQUE', 'CONSTRAINT', 'FOREIGN', 'KEY', 'INDEX'].includes(candidate.toUpperCase())) {
        columns.add(candidate);
      }
    }
    definitions.set(table, { body, columns });
  }
  return definitions;
}

function primaryKeyFor(definitions, table) {
  const primaryKey = definitions.get(table)?.body.match(/PRIMARY KEY\s*\(([^)]+)\)/i)?.[1];
  return primaryKey?.replaceAll('`', '').replace(/\s/g, '').replaceAll(',', '+') ?? '';
}

function parseExternalId(entry, declaration, errors) {
  const [column, target, ...rest] = declaration.split('->');
  const targetOwnerSeparator = target?.indexOf('.') ?? -1;
  if (!column || !target || rest.length > 0 || targetOwnerSeparator <= 0 || targetOwnerSeparator === target.length - 1) {
    errors.push(`invalid external_ids declaration: ${entry.table}.${declaration}`);
    return undefined;
  }
  const targetOwner = target.slice(0, targetOwnerSeparator);
  return {
    consumer: entry.owner,
    consumer_table: entry.table,
    column,
    target,
    targetOwner,
    targetRef: target.slice(targetOwnerSeparator + 1)
  };
}

function tupleKey(tuple) {
  return `${tuple.consumer}|${tuple.consumer_table}|${tuple.column}|${tuple.target}`;
}

function describeTuple(tuple) {
  return `${tuple.consumer}.${tuple.consumer_table}.${tuple.column} -> ${tuple.target}`;
}

function resolveJsonPointer(document, fragment) {
  if (!fragment.startsWith('/')) return undefined;
  let current = document;
  for (const segment of fragment.slice(1).split('/')) {
    const decoded = segment.replaceAll('~1', '/').replaceAll('~0', '~');
    if (current === null || typeof current !== 'object' || !Object.hasOwn(current, decoded)) return undefined;
    current = current[decoded];
  }
  return current;
}

function isContainedBy(directory, candidate) {
  const relativeCandidate = relative(directory, candidate);
  return relativeCandidate !== ''
    && relativeCandidate !== '..'
    && !relativeCandidate.startsWith(`..${sep}`)
    && !isAbsolute(relativeCandidate);
}

function canonicalContractDirectory(rootPath, errors) {
  const repositoryRoot = resolve(rootPath);
  let repositoryRootReal;
  try {
    repositoryRootReal = realpathSync(repositoryRoot);
  } catch (error) {
    errors.push(`repository root cannot be resolved: ${error.message}`);
    return undefined;
  }

  const contractsDirectory = resolve(repositoryRoot, 'contracts');
  const canonicalDirectory = resolve(contractsDirectory, 'v2');
  const expectedCanonicalDirectory = resolve(repositoryRootReal, 'contracts', 'v2');
  for (const [kind, directory] of [['ancestor', contractsDirectory], ['root', canonicalDirectory]]) {
    try {
      if (lstatSync(directory).isSymbolicLink()) {
        const displayPath = relative(repositoryRoot, directory).split(sep).join('/');
        errors.push(`canonical contracts/v2 ${kind} must not be a symlink: ${displayPath}`);
        return undefined;
      }
    } catch (error) {
      errors.push(`canonical contracts/v2 directory is unavailable: ${error.message}`);
      return undefined;
    }
  }

  let canonicalDirectoryReal;
  try {
    canonicalDirectoryReal = realpathSync(canonicalDirectory);
  } catch (error) {
    errors.push(`canonical contracts/v2 directory is unavailable: ${error.message}`);
    return undefined;
  }
  if (canonicalDirectoryReal !== expectedCanonicalDirectory) {
    errors.push('canonical contracts/v2 root must resolve to the repository physical directory');
    return undefined;
  }
  return { canonicalDirectory, canonicalDirectoryReal };
}

function canonicalContractArtifact(rootPath, artifact, errors) {
  if (isAbsolute(artifact) || /^[A-Za-z]:[\\/]/.test(artifact)) {
    errors.push(`contract artifact must be relative: ${artifact}`);
    return undefined;
  }
  if (artifact.split(/[\\/]+/).includes('..')) {
    errors.push(`contract artifact cannot contain traversal: ${artifact}`);
    return undefined;
  }

  const canonicalRoot = canonicalContractDirectory(rootPath, errors);
  if (!canonicalRoot) return undefined;

  const absoluteArtifact = resolve(rootPath, artifact);
  if (!isContainedBy(canonicalRoot.canonicalDirectory, absoluteArtifact)) {
    errors.push(`contract artifact escapes canonical contracts/v2: ${artifact}`);
    return undefined;
  }
  if (!existsSync(absoluteArtifact)) {
    errors.push(`contract artifact does not exist: ${artifact}`);
    return undefined;
  }

  let realArtifact;
  try {
    realArtifact = realpathSync(absoluteArtifact);
  } catch (error) {
    errors.push(`contract artifact cannot be resolved: ${artifact} (${error.message})`);
    return undefined;
  }
  if (!isContainedBy(canonicalRoot.canonicalDirectoryReal, realArtifact)) {
    errors.push(`contract artifact escapes canonical contracts/v2: ${artifact}`);
    return undefined;
  }
  return realArtifact;
}

function validateContractReference(rootPath, reference, targetOwner, label, errors) {
  const parts = reference.split('#');
  if (parts.length !== 2 || !parts[0] || !parts[1]) {
    errors.push(`contract must name a canonical JSON pointer: ${label} -> ${reference}`);
    return;
  }
  const [artifact, fragment] = parts;
  if (isAbsolute(artifact) || /^[A-Za-z]:[\\/]/.test(artifact)) {
    errors.push(`contract artifact must be relative: ${artifact}`);
    return;
  }
  if (!artifact.startsWith('contracts/v2/')) {
    errors.push(`contract must be under contracts/v2: ${label} -> ${reference}`);
    return;
  }
  const canonicalArtifact = canonicalContractArtifact(rootPath, artifact, errors);
  if (!canonicalArtifact) return;
  let document;
  try {
    document = JSON.parse(readFileSync(canonicalArtifact, 'utf8'));
  } catch (error) {
    errors.push(`contract artifact is not valid JSON: ${artifact} (${error.message})`);
    return;
  }
  if (resolveJsonPointer(document, fragment) === undefined) errors.push(`contract pointer does not resolve: ${reference}`);
  const ownerName = targetOwner.toLowerCase();
  const matchesOwner = artifact.endsWith(`/openapi/${ownerName}.openapi.json`)
    || fragment.includes(`/components/messages/${ownerName}.`);
  if (!matchesOwner) errors.push(`contract does not belong to ${targetOwner}: ${reference}`);
}

function validateTarget(rootPath, tuple, definitions, ownershipByTable, errors) {
  if (!(tuple.targetOwner in domains)) {
    errors.push(`target owner is unknown: ${describeTuple(tuple)}`);
    return;
  }
  if (tuple.targetRef.startsWith('contracts/v2/')) {
    validateContractReference(rootPath, tuple.targetRef, tuple.targetOwner, describeTuple(tuple), errors);
    return;
  }
  const [targetTable, targetColumn, ...rest] = tuple.targetRef.split('.');
  if (!targetTable || !targetColumn || rest.length > 0) {
    errors.push(`target must be an existing table.column or canonical contract: ${describeTuple(tuple)}`);
    return;
  }
  const targetOwnership = ownershipByTable.get(targetTable);
  if (!targetOwnership) {
    errors.push(`target table does not exist: ${describeTuple(tuple)}`);
    return;
  }
  if (targetOwnership.owner !== tuple.targetOwner) errors.push(`target owner mismatch: ${describeTuple(tuple)}`);
  if (!definitions.get(targetTable)?.columns.has(targetColumn)) errors.push(`target column does not exist: ${targetTable}.${targetColumn}`);
}

function errorsFor(rootPath) {
  const errors = [];
  for (const relativePath of requiredFiles) {
    if (!existsSync(resolve(rootPath, relativePath))) errors.push(`missing ${relativePath}`);
  }
  if (errors.length > 0) return { errors, expectedReferenceCount: 0 };

  const sql = requireFile(rootPath, 'database/mysql/compose-schema.sql');
  const ownership = parseCsv(requireFile(rootPath, 'database/ownership/table-ownership.csv'), 'table-ownership.csv');
  const references = parseCsv(requireFile(rootPath, 'database/ownership/cross-domain-references.csv'), 'cross-domain-references.csv');
  const accounts = parseCsv(requireFile(rootPath, 'database/ownership/schema-account-matrix.csv'), 'schema-account-matrix.csv');
  const serviceLocalTables = parseCsv(requireFile(rootPath, 'database/ownership/service-local-tables.csv'), 'service-local-tables.csv');
  const identitySchemaMigration = requireFile(rootPath, 'database/migrations/identity/DB-IDENTITY-01-identity-user-session.sql');
  const identityIdempotencyMigration = requireFile(rootPath, 'database/migrations/identity/DB-IDENTITY-02-service-token-idempotency.sql');
  const document = requireFile(rootPath, 'docs/开发/D6-DATA-五域数据所有权契约.md');
  requireFields(ownership, ownershipFields, 'table-ownership.csv', errors);
  requireFields(references, referenceFields, 'cross-domain-references.csv', errors);
  requireFields(accounts, accountFields, 'schema-account-matrix.csv', errors);
  requireFields(serviceLocalTables, localTableFields, 'service-local-tables.csv', errors);

  const definitions = schemaDefinitions(sql);
  const sourceTables = [...definitions.keys()].sort();
  const ownershipByTable = new Map();
  for (const entry of ownership) {
    if (ownershipByTable.has(entry.table)) errors.push(`duplicate ownership row for ${entry.table}`);
    ownershipByTable.set(entry.table, entry);
    if (!(entry.owner in domains)) errors.push(`unknown owner ${entry.owner} for ${entry.table}`);
    if (domains[entry.owner] !== entry.schema) errors.push(`schema mismatch for ${entry.table}: owner ${entry.owner} must use ${domains[entry.owner]}`);
    for (const field of ownershipFields.slice(3)) {
      if (!entry[field]) errors.push(`${entry.table} is missing ${field}`);
    }
    const actualPrimaryKey = primaryKeyFor(definitions, entry.table);
    if (actualPrimaryKey && actualPrimaryKey !== entry.primary_key) {
      errors.push(`primary key mismatch for ${entry.table}: contract=${entry.primary_key} source=${actualPrimaryKey}`);
    }
  }
  for (const table of sourceTables) {
    if (!ownershipByTable.has(table)) errors.push(`source schema table has no owner: ${table}`);
  }
  for (const table of ownershipByTable.keys()) {
    if (!definitions.has(table)) errors.push(`ownership table is absent from source schema: ${table}`);
  }
  if ([...new Set(ownership.map((entry) => entry.owner))].sort().join('|') !== expectedOwners.join('|')) {
    errors.push(`table catalog owners must be exactly ${expectedOwners.join(', ')}`);
  }

  for (const [table, definition] of definitions) {
    for (const reference of definition.body.matchAll(/REFERENCES\s+`?([A-Za-z0-9_]+)`?/gi)) {
      const sourceOwner = ownershipByTable.get(table)?.owner;
      const targetOwner = ownershipByTable.get(reference[1])?.owner;
      if (sourceOwner && targetOwner && sourceOwner !== targetOwner) {
        errors.push(`cross-domain foreign key is forbidden: ${table} -> ${reference[1]}`);
      }
    }
  }

  const expectedReferences = new Map();
  for (const entry of ownership) {
    if (entry.external_ids === '-') continue;
    for (const declaration of entry.external_ids.split(';')) {
      const tuple = parseExternalId(entry, declaration, errors);
      if (!tuple) continue;
      if (!definitions.get(tuple.consumer_table)?.columns.has(tuple.column)) {
        errors.push(`source column does not exist: ${tuple.consumer_table}.${tuple.column}`);
      }
      validateTarget(rootPath, tuple, definitions, ownershipByTable, errors);
      const key = tupleKey(tuple);
      if (expectedReferences.has(key)) errors.push(`duplicate external_ids declaration: ${describeTuple(tuple)}`);
      expectedReferences.set(key, tuple);
    }
  }

  const ledgerReferences = new Map();
  for (const entry of references) {
    const targetOwnerSeparator = entry.target.indexOf('.');
    const tuple = {
      consumer: entry.consumer,
      consumer_table: entry.consumer_table,
      column: entry.column,
      target: entry.target,
      targetOwner: targetOwnerSeparator > 0 ? entry.target.slice(0, targetOwnerSeparator) : '',
      targetRef: targetOwnerSeparator > 0 ? entry.target.slice(targetOwnerSeparator + 1) : ''
    };
    const consumer = ownershipByTable.get(tuple.consumer_table);
    if (!consumer) errors.push(`ledger consumer table does not exist: ${tuple.consumer_table}`);
    else if (consumer.owner !== tuple.consumer) errors.push(`ledger consumer owner mismatch: ${tuple.consumer_table}`);
    if (consumer && !definitions.get(tuple.consumer_table)?.columns.has(tuple.column)) {
      errors.push(`source column does not exist: ${tuple.consumer_table}.${tuple.column}`);
    }
    validateTarget(rootPath, tuple, definitions, ownershipByTable, errors);
    if (!entry.replacement || /(?:SQL|JOIN|REPOSITORY|FOREIGN KEY)/i.test(entry.replacement)) {
      errors.push(`cross-domain reference has an invalid replacement for ${tuple.consumer_table}.${tuple.column}`);
    }
    validateContractReference(rootPath, entry.contract, tuple.targetOwner, describeTuple(tuple), errors);
    if (entry.consistency !== 'eventual') errors.push(`cross-domain reference must declare eventual consistency: ${tuple.consumer_table}.${tuple.column}`);
    const key = tupleKey(tuple);
    if (ledgerReferences.has(key)) errors.push(`duplicate ledger mapping: ${describeTuple(tuple)}`);
    ledgerReferences.set(key, tuple);
    if (!expectedReferences.has(key)) errors.push(`unexpected ledger mapping: ${describeTuple(tuple)}`);
  }
  for (const [, tuple] of expectedReferences) {
    if (!ledgerReferences.has(tupleKey(tuple))) errors.push(`missing ledger mapping: ${describeTuple(tuple)}`);
  }

  const accountByOwner = new Map();
  for (const entry of accounts) {
    if (accountByOwner.has(entry.owner)) errors.push(`duplicate account row for ${entry.owner}`);
    accountByOwner.set(entry.owner, entry);
    if (domains[entry.owner] !== entry.schema) errors.push(`account schema mismatch for ${entry.owner}`);
    if (entry.account !== `${entry.schema}_rw`) errors.push(`account must be schema-scoped for ${entry.owner}`);
    if (entry.allow !== 'SELECT|INSERT|UPDATE|DELETE') errors.push(`account ${entry.account} must have only runtime DML allow list`);
    const expectedDeny = Object.values(domains).filter((schema) => schema !== entry.schema).sort().join('|');
    if (entry.deny !== expectedDeny) errors.push(`account ${entry.account} must deny every foreign schema: ${expectedDeny}`);
    if (entry.ddl !== 'DENY') errors.push(`account ${entry.account} must deny DDL`);
  }
  if ([...accountByOwner.keys()].sort().join('|') !== expectedOwners.join('|')) {
    errors.push(`account matrix owners must be exactly ${expectedOwners.join(', ')}`);
  }

  const localKeys = new Set();
  const localKinds = new Map();
  for (const entry of serviceLocalTables) {
    const key = `${entry.schema}.${entry.table}`;
    if (localKeys.has(key)) errors.push(`duplicate service-local table ${key}`);
    localKeys.add(key);
    if (domains[entry.owner] !== entry.schema) errors.push(`service-local schema mismatch for ${key}`);
    if (!['OUTBOX', 'INBOX', 'PROJECTION', 'IDEMPOTENCY'].includes(entry.kind)) errors.push(`unknown service-local kind ${entry.kind} for ${key}`);
    localKinds.set(`${entry.owner}:${entry.kind}`, entry);
  }
  for (const owner of expectedOwners) {
    const requiredKinds = owner === 'IDENTITY' ? ['OUTBOX', 'IDEMPOTENCY'] : ['OUTBOX', 'INBOX'];
    for (const kind of requiredKinds) {
      if (!localKinds.has(`${owner}:${kind}`)) errors.push(`${owner} must own its local ${kind}`);
    }
  }

  const identityOutbox = localKinds.get('IDENTITY:OUTBOX');
  const identityIdempotency = localKinds.get('IDENTITY:IDEMPOTENCY');
  if (!identityOutbox
    || identityOutbox.table !== 't_identity_outbox_event'
    || identityOutbox.lifecycle !== 'implemented'
    || identityOutbox.implementation_issue !== '#311') {
    errors.push('Identity must record the delivered t_identity_outbox_event from #311');
  }
  if (!identityIdempotency
    || identityIdempotency.table !== 't_identity_service_token_idempotency'
    || identityIdempotency.lifecycle !== 'implemented'
    || identityIdempotency.implementation_issue !== '#311') {
    errors.push('Identity must record its delivered idempotency table instead of a planned event inbox');
  }
  if (localKinds.has('IDENTITY:INBOX')) {
    errors.push('Identity must not declare an event inbox until it actually consumes a v2 event');
  }
  if (!/CREATE TABLE IF NOT EXISTS t_identity_outbox_event\s*\(/i.test(identitySchemaMigration)) {
    errors.push('#311 Identity schema migration must create t_identity_outbox_event');
  }
  if (!/CREATE TABLE IF NOT EXISTS t_identity_service_token_idempotency\s*\(/i.test(identityIdempotencyMigration)) {
    errors.push('#311 Identity idempotency migration must create t_identity_service_token_idempotency');
  }
  try {
    const asyncApi = JSON.parse(requireFile(rootPath, 'contracts/v2/asyncapi/events.asyncapi.json'));
    const identityEvent = asyncApi.components?.messages?.['identity.security-version.changed.v2'];
    if (identityEvent?.['x-onlinejudge-producer'] !== 'identity') {
      errors.push('identity.security-version.changed.v2 must be produced by Identity');
    }
    if (!Array.isArray(identityEvent?.['x-onlinejudge-consumers'])
      || identityEvent['x-onlinejudge-consumers'].includes('identity')) {
      errors.push('Identity must not consume its own security-version event without an implemented inbox');
    }
  } catch (error) {
    errors.push(`cannot read Identity v2 event contract: ${error.message}`);
  }

  if (!document.includes('#338') || !document.includes('#341') || !document.includes('#311') || !document.includes('禁止跨 Schema')) {
    errors.push('D6 ownership document must link #311, #338, #341, and the cross-schema prohibition');
  }
  return { errors, expectedReferenceCount: expectedReferences.size };
}

export function verifyDataOwnershipContract({ rootPath = defaultRoot } = {}) {
  const resolvedRoot = resolve(rootPath);
  const { errors, expectedReferenceCount } = errorsFor(resolvedRoot);
  if (errors.length > 0) throw new Error(errors.join('\n'));
  const ownership = parseCsv(requireFile(resolvedRoot, 'database/ownership/table-ownership.csv'), 'table-ownership.csv');
  const references = parseCsv(requireFile(resolvedRoot, 'database/ownership/cross-domain-references.csv'), 'cross-domain-references.csv');
  const accounts = parseCsv(requireFile(resolvedRoot, 'database/ownership/schema-account-matrix.csv'), 'schema-account-matrix.csv');
  const serviceLocalTables = parseCsv(requireFile(resolvedRoot, 'database/ownership/service-local-tables.csv'), 'service-local-tables.csv');
  return {
    tableCount: ownership.length,
    owners: [...new Set(ownership.map((entry) => entry.owner))].sort(),
    schemas: [...new Set(ownership.map((entry) => entry.schema))].sort(),
    accountCount: accounts.length,
    crossDomainReferenceCount: references.length,
    expectedReferenceCount,
    serviceLocalTableCount: serviceLocalTables.length,
    identityRuntimeTables: {
      outbox: serviceLocalTables.find((entry) => entry.owner === 'IDENTITY' && entry.kind === 'OUTBOX')?.table,
      idempotency: serviceLocalTables.find((entry) => entry.owner === 'IDENTITY' && entry.kind === 'IDEMPOTENCY')?.table
    }
  };
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
  try {
    const summary = verifyDataOwnershipContract();
    console.log(`data ownership contract passed: ${summary.tableCount} tables, ${summary.accountCount} accounts, ${summary.crossDomainReferenceCount}/${summary.expectedReferenceCount} cross-domain references, ${summary.serviceLocalTableCount} service-local tables`);
  } catch (error) {
    console.error(`data ownership contract failed: ${error.message}`);
    process.exitCode = 1;
  }
}
