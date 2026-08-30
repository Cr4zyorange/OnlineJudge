#!/usr/bin/env node

import { existsSync, readFileSync, realpathSync } from 'node:fs';
import { resolve } from 'node:path';
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
  'docs/开发/D6-DATA-五域数据所有权契约.md'
];

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

function requireFile(rootPath, relativePath) {
  const absolutePath = resolve(rootPath, relativePath);
  if (!existsSync(absolutePath)) throw new Error(`missing ${relativePath}`);
  return readFileSync(absolutePath, 'utf8');
}

function schemaTables(sql) {
  return [...sql.matchAll(/CREATE TABLE(?: IF NOT EXISTS)?\s+`?([A-Za-z0-9_]+)`?/gi)]
    .map((match) => match[1])
    .filter((table) => table !== 'schema_migrations')
    .sort();
}

function primaryKeyFor(sql, table) {
  const escaped = table.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const block = sql.match(new RegExp(`CREATE TABLE(?: IF NOT EXISTS)?\\s+\\x60?${escaped}\\x60?\\s*\\(([\\s\\S]*?)\\);`, 'i'))?.[1];
  const primaryKey = block?.match(/PRIMARY KEY\s*\(([^)]+)\)/i)?.[1];
  return primaryKey?.replaceAll('`', '').replace(/\s/g, '').replaceAll(',', '+') ?? '';
}

function errorsFor(rootPath) {
  const errors = [];
  for (const relativePath of requiredFiles) {
    if (!existsSync(resolve(rootPath, relativePath))) errors.push(`missing ${relativePath}`);
  }
  if (errors.length > 0) return errors;

  const sql = requireFile(rootPath, 'database/mysql/compose-schema.sql');
  const ownership = parseCsv(requireFile(rootPath, 'database/ownership/table-ownership.csv'), 'table-ownership.csv');
  const references = parseCsv(requireFile(rootPath, 'database/ownership/cross-domain-references.csv'), 'cross-domain-references.csv');
  const accounts = parseCsv(requireFile(rootPath, 'database/ownership/schema-account-matrix.csv'), 'schema-account-matrix.csv');
  const serviceLocalTables = parseCsv(requireFile(rootPath, 'database/ownership/service-local-tables.csv'), 'service-local-tables.csv');
  const document = requireFile(rootPath, 'docs/开发/D6-DATA-五域数据所有权契约.md');
  const sourceTables = schemaTables(sql);
  const ownershipByTable = new Map();

  for (const entry of ownership) {
    if (ownershipByTable.has(entry.table)) errors.push(`duplicate ownership row for ${entry.table}`);
    ownershipByTable.set(entry.table, entry);
    if (!(entry.owner in domains)) errors.push(`unknown owner ${entry.owner} for ${entry.table}`);
    if (domains[entry.owner] !== entry.schema) {
      errors.push(`schema mismatch for ${entry.table}: owner ${entry.owner} must use ${domains[entry.owner]}`);
    }
    for (const field of ['primary_key', 'external_ids', 'owner_constraints', 'retained_indexes', 'migration_strategy']) {
      if (!entry[field]) errors.push(`${entry.table} is missing ${field}`);
    }
    const actualPrimaryKey = primaryKeyFor(sql, entry.table);
    if (actualPrimaryKey && actualPrimaryKey !== entry.primary_key) {
      errors.push(`primary key mismatch for ${entry.table}: contract=${entry.primary_key} source=${actualPrimaryKey}`);
    }
  }
  for (const table of sourceTables) {
    if (!ownershipByTable.has(table)) errors.push(`source schema table has no owner: ${table}`);
  }
  for (const table of ownershipByTable.keys()) {
    if (!sourceTables.includes(table)) errors.push(`ownership table is absent from source schema: ${table}`);
  }
  const ownersInCatalog = [...new Set(ownership.map((entry) => entry.owner))].sort();
  if (ownersInCatalog.join('|') !== expectedOwners.join('|')) {
    errors.push(`table catalog owners must be exactly ${expectedOwners.join(', ')}`);
  }

  const tableBlocks = [...sql.matchAll(/CREATE TABLE(?: IF NOT EXISTS)?\s+`?([A-Za-z0-9_]+)`?\s*\(([\s\S]*?)\);/gi)];
  for (const [, table, block] of tableBlocks) {
    for (const reference of block.matchAll(/REFERENCES\s+`?([A-Za-z0-9_]+)`?/gi)) {
      const sourceOwner = ownershipByTable.get(table)?.owner;
      const targetOwner = ownershipByTable.get(reference[1])?.owner;
      if (sourceOwner && targetOwner && sourceOwner !== targetOwner) {
        errors.push(`cross-domain foreign key is forbidden: ${table} -> ${reference[1]}`);
      }
    }
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
    if (!['OUTBOX', 'INBOX', 'PROJECTION'].includes(entry.kind)) errors.push(`unknown service-local kind ${entry.kind} for ${key}`);
    localKinds.set(`${entry.owner}:${entry.kind}`, entry);
  }
  for (const owner of expectedOwners) {
    for (const kind of ['OUTBOX', 'INBOX']) {
      if (!localKinds.has(`${owner}:${kind}`)) errors.push(`${owner} must own its local ${kind}`);
    }
  }

  for (const entry of references) {
    const consumer = ownershipByTable.get(entry.consumer_table);
    if (!consumer) errors.push(`cross-domain reference consumer table is unknown: ${entry.consumer_table}`);
    else if (consumer.owner !== entry.consumer) errors.push(`cross-domain reference consumer owner mismatch: ${entry.consumer_table}`);
    if (!(entry.target_owner in domains)) errors.push(`cross-domain reference target owner is unknown: ${entry.target_owner}`);
    if (entry.consumer === entry.target_owner) errors.push(`cross-domain reference must not describe an owner-local relation: ${entry.consumer_table}`);
    if (!entry.replacement || /(?:SQL|JOIN|REPOSITORY|FOREIGN KEY)/i.test(entry.replacement)) {
      errors.push(`cross-domain reference has an invalid replacement for ${entry.consumer_table}.${entry.column}`);
    }
    if (!entry.contract.startsWith('contracts/v2/')) {
      errors.push(`cross-domain reference must use a v2 contract for ${entry.consumer_table}.${entry.column}`);
    }
    if (entry.consistency !== 'eventual') errors.push(`cross-domain reference must declare eventual consistency: ${entry.consumer_table}.${entry.column}`);
  }

  if (!document.includes('#338') || !document.includes('#341') || !document.includes('禁止跨 Schema')) {
    errors.push('D6 ownership document must link #338, #341, and the cross-schema prohibition');
  }
  return errors;
}

export function verifyDataOwnershipContract({ rootPath = defaultRoot } = {}) {
  const resolvedRoot = resolve(rootPath);
  const errors = errorsFor(resolvedRoot);
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
    serviceLocalTableCount: serviceLocalTables.length
  };
}

if (process.argv[1] && realpathSync(process.argv[1]) === realpathSync(fileURLToPath(import.meta.url))) {
  try {
    const summary = verifyDataOwnershipContract();
    console.log(`data ownership contract passed: ${summary.tableCount} tables, ${summary.accountCount} accounts, ${summary.crossDomainReferenceCount} cross-domain references, ${summary.serviceLocalTableCount} service-local tables`);
  } catch (error) {
    console.error(`data ownership contract failed: ${error.message}`);
    process.exitCode = 1;
  }
}
