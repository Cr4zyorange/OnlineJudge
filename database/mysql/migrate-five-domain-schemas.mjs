#!/usr/bin/env node

/**
 * #341's stop-the-world migration control plane.  It deliberately operates
 * only with a database administrator: application accounts are created after
 * the copy and are used only for the allow/deny acceptance probe.
 */
import { createHash, randomUUID } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptPath = fileURLToPath(import.meta.url);
const repositoryRoot = resolve(dirname(scriptPath), '..', '..');
const migrationId = 'V20260831_01__five_domain_data_migration';
const requiredOwners = ['IDENTITY', 'COURSE', 'ASSESSMENT', 'GRADE', 'LEARNING'];
// #337 adds service-owned reliable-delivery state to the same ownership
// ledger.  These are operational tables, not legacy business facts: #341
// recreates their structure in the target and replays the required facts
// below, rather than copying lease, retry, inbox or dead-letter state.
const runtimeTableNames = new Set([
  'assessment_event_inbox',
  'assessment_event_outbox',
  'course_event_outbox',
  'course_membership_reconciliation_checkpoint',
  'grade_event_inbox',
  'grade_event_outbox',
  'learning_course_member_projection',
  'learning_course_membership_watermark',
  'learning_deferred_event',
  'learning_event_dead_letter',
  'learning_event_delivery_attempt',
  'learning_event_inbox',
  'learning_event_reconciliation_request',
]);

function fail(message) {
  throw new Error(`five-domain-migration: ${message}`);
}

function assertIdentifier(value, label) {
  if (!/^[A-Za-z0-9_]{1,64}$/.test(value)) {
    fail(`unsafe ${label} identifier: ${value}`);
  }
  return value;
}

function quoteIdentifier(value) {
  return `\`${assertIdentifier(value, 'SQL')}\``;
}

function quoteLiteral(value) {
  // mysql CLI receives a complete SQL statement, so escape both quote styles
  // MySQL recognizes inside a string literal.  This is relevant for runtime
  // passwords supplied by deployment secrets as well as ordinary metadata.
  return `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`;
}

function sha256(value) {
  return createHash('sha256').update(value).digest('hex');
}

export function parseTabRows(output) {
  return output
    .split(/\r?\n/)
    .filter((line) => line.length > 0)
    .map((line) => line.split('\t'));
}

function parseCsv(filePath) {
  const lines = readFileSync(filePath, 'utf8')
    .split(/\r?\n/)
    .filter((line) => line.trim().length > 0);
  const headers = lines.shift().split(',');
  return lines.map((line) => {
    const fields = line.split(',');
    if (fields.length !== headers.length) {
      fail(`unsupported CSV quoting or malformed row in ${filePath}: ${line}`);
    }
    return Object.fromEntries(headers.map((header, index) => [header, fields[index]]));
  });
}

export function loadFiveDomainPlan(root = repositoryRoot) {
  const allTables = parseCsv(resolve(root, 'database/ownership/table-ownership.csv'));
  const accounts = parseCsv(resolve(root, 'database/ownership/schema-account-matrix.csv'));
  const localTables = parseCsv(resolve(root, 'database/ownership/service-local-tables.csv'));
  const crossDomainReferences = parseCsv(resolve(root, 'database/ownership/cross-domain-references.csv'));
  const schemaByOwner = new Map(accounts.map((account) => [account.owner, account.schema]));
  const accountByOwner = new Map(accounts.map((account) => [account.owner, account]));
  const runtimeTables = allTables.filter((entry) => runtimeTableNames.has(entry.table));
  const tables = allTables.filter((entry) => !runtimeTableNames.has(entry.table));

  if (new Set(allTables.map((entry) => entry.table)).size !== allTables.length) {
    fail('canonical ownership has duplicate table rows');
  }
  if (runtimeTables.length !== runtimeTableNames.size) {
    fail(`canonical ownership must contain ${runtimeTableNames.size} reliable runtime tables, found ${runtimeTables.length}`);
  }
  if (tables.length !== 46 || new Set(tables.map((entry) => entry.table)).size !== 46) {
    fail(`canonical ownership must contain exactly 46 unique tables, found ${tables.length}`);
  }
  if (accounts.length !== 5 || requiredOwners.some((owner) => !schemaByOwner.has(owner))) {
    fail('canonical ownership must contain the five required schema/account owners');
  }
  for (const entry of allTables) {
    if (!schemaByOwner.has(entry.owner) || entry.schema !== schemaByOwner.get(entry.owner)) {
      fail(`ownership row has a non-canonical schema: ${entry.table}`);
    }
    assertIdentifier(entry.table, 'table');
    assertIdentifier(entry.schema, 'schema');
  }
  return {
    allTables, tables, runtimeTables, accounts, localTables, crossDomainReferences, schemaByOwner, accountByOwner,
  };
}

export function buildCopyStatement({ sourceSchema, targetSchema, table, columns }) {
  const quotedColumns = columns.map(quoteIdentifier).join(', ');
  const sourceColumns = columns.map((column) => `s.${quoteIdentifier(column)}`).join(', ');
  const updates = columns.map((column) => `${quoteIdentifier(column)} = VALUES(${quoteIdentifier(column)})`).join(', ');
  return [
    `INSERT INTO ${quoteIdentifier(targetSchema)}.${quoteIdentifier(table)} (${quotedColumns})`,
    `SELECT ${sourceColumns} FROM ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(table)} s`,
    `ON DUPLICATE KEY UPDATE ${updates}`,
  ].join(' ');
}

function parseArguments(argv) {
  const options = {
    action: 'migrate',
    mysql: 'mysql',
    host: '127.0.0.1',
    port: '3306',
    adminPasswordEnv: 'OJ_MYSQL_ADMIN_PASSWORD',
    runtimePasswordEnvPrefix: 'OJ341_RUNTIME_PASSWORD_',
    sourceReadOnlyAck: false,
  };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--help' || argument === '-h') {
      options.help = true;
      continue;
    }
    if (argument === '--skip-permissions') {
      fail('--skip-permissions is not supported: every production migration must provision and prove all five runtime accounts');
    }
    if (argument === '--source-read-only-ack') {
      options.sourceReadOnlyAck = true;
      continue;
    }
    if (!argument.startsWith('--')) {
      fail(`unknown positional argument: ${argument}`);
    }
    const key = argument.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
    if (index + 1 >= argv.length || argv[index + 1].startsWith('--')) {
      fail(`missing value for ${argument}`);
    }
    options[key] = argv[index + 1];
    index += 1;
  }
  return options;
}

function usage() {
  return `usage: node database/mysql/migrate-five-domain-schemas.mjs \\
  --action migrate|verify|replay|cutover|rollback \\
  --admin-user <mysql-admin> --source-schema <legacy-schema> \\
  [--host 127.0.0.1] [--port 3306] [--mysql mysql] \\
  [--admin-password-env OJ_MYSQL_ADMIN_PASSWORD] \\
  [--runtime-password-env-prefix OJ341_RUNTIME_PASSWORD_] \\
  [--source-read-only-ack] [--evidence <json-path>] [--cutover-state <json-path>]

migrate/replay require --source-read-only-ack.  Password values are read only
from environment variables and are never emitted in evidence.`;
}

function createMysql(options, user, password) {
  if (typeof password !== 'string') {
    fail(`environment variable for MySQL account ${user} is not set`);
  }
  return ({ sql, database, allowFailure = false }) => {
    const args = [
      '--protocol=TCP',
      `--host=${options.host}`,
      `--port=${options.port}`,
      `--user=${user}`,
      '--batch',
      '--skip-column-names',
      '--raw',
    ];
    if (database) args.push(`--database=${assertIdentifier(database, 'database')}`);
    args.push('--execute', sql);
    const result = spawnSync(options.mysql, args, {
      encoding: 'utf8',
      env: { ...process.env, MYSQL_PWD: password },
    });
    if (result.error) fail(`cannot run mysql client ${options.mysql}: ${result.error.message}`);
    const response = {
      code: result.status ?? 1,
      stdout: result.stdout ?? '',
      stderr: result.stderr ?? '',
    };
    response.raw = `${response.stdout}${response.stderr}`;
    if (!allowFailure && response.code !== 0) {
      fail(`mysql command failed (${response.code}): ${response.raw.trim()}`);
    }
    return response;
  };
}

function requiredRuntimePasswords(plan, options) {
  const passwords = new Map();
  for (const owner of requiredOwners) {
    const variable = `${options.runtimePasswordEnvPrefix}${owner}`;
    const password = process.env[variable];
    if (typeof password !== 'string' || password.length === 0) {
      fail(`set ${variable} before provisioning or verifying the five runtime accounts`);
    }
    passwords.set(owner, password);
  }
  return passwords;
}

function sourceTableCount(admin, sourceSchema) {
  const result = admin({
    sql: `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ${quoteLiteral(sourceSchema)} AND table_type = 'BASE TABLE' AND table_name <> 'schema_migrations'`,
  });
  const count = Number(result.stdout.trim());
  if (!Number.isInteger(count)) fail(`cannot read source table count for ${sourceSchema}`);
  return count;
}

function sourceFingerprint(admin, plan, sourceSchema) {
  const parts = [];
  for (const entry of plan.allTables) {
    const count = admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(entry.table)}` }).stdout.trim();
    const checksum = parseTabRows(admin({ sql: `CHECKSUM TABLE ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(entry.table)} EXTENDED` }).stdout)[0]?.[1] ?? 'missing';
    parts.push(`${entry.table}:${count}:${checksum}`);
  }
  return sha256(parts.join('\n'));
}

function sourceVersion(admin, sourceSchema) {
  const exists = admin({
    sql: `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ${quoteLiteral(sourceSchema)} AND table_name = 'schema_migrations'`,
  }).stdout.trim();
  if (exists !== '1') return { migrationCount: 0, latestVersion: null };
  const row = parseTabRows(admin({
    sql: `SELECT COUNT(*), COALESCE((SELECT version FROM ${quoteIdentifier(sourceSchema)}.schema_migrations ORDER BY installed_rank DESC LIMIT 1), '') FROM ${quoteIdentifier(sourceSchema)}.schema_migrations`,
  }).stdout)[0] ?? ['0', ''];
  return { migrationCount: Number(row[0]), latestVersion: row[1] || null };
}

function ensureTargetControlTables(admin, plan) {
  for (const owner of requiredOwners) {
    const schema = plan.schemaByOwner.get(owner);
    admin({ sql: `CREATE DATABASE IF NOT EXISTS ${quoteIdentifier(schema)} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci` });
    admin({ sql: `
      CREATE TABLE IF NOT EXISTS ${quoteIdentifier(schema)}.schema_migrations (
        version VARCHAR(255) NOT NULL,
        checksum_sha256 CHAR(64) NOT NULL,
        installed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (version)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    ` });
    admin({ sql: `
      CREATE TABLE IF NOT EXISTS ${quoteIdentifier(schema)}.migration_checkpoints (
        migration_id VARCHAR(255) NOT NULL,
        phase VARCHAR(64) NOT NULL,
        source_schema VARCHAR(64) NOT NULL,
        source_fingerprint CHAR(64) NOT NULL,
        completed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (migration_id, phase)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    ` });
  }
}

function installVersion(admin, plan) {
  const checksum = sha256(readFileSync(scriptPath));
  for (const owner of requiredOwners) {
    const schema = plan.schemaByOwner.get(owner);
    const existing = admin({
      sql: `SELECT checksum_sha256 FROM ${quoteIdentifier(schema)}.schema_migrations WHERE version = ${quoteLiteral(migrationId)}`,
    }).stdout.trim();
    if (existing && existing !== checksum) {
      fail(`checkpoint checksum mismatch in ${schema}; append a new migration instead of mutating ${migrationId}`);
    }
    if (!existing) {
      admin({ sql: `INSERT INTO ${quoteIdentifier(schema)}.schema_migrations (version, checksum_sha256) VALUES (${quoteLiteral(migrationId)}, ${quoteLiteral(checksum)})` });
    }
  }
  return checksum;
}

function checkpoint(admin, plan, phase, sourceSchema, fingerprint) {
  for (const owner of requiredOwners) {
    const schema = plan.schemaByOwner.get(owner);
    admin({ sql: `
      INSERT INTO ${quoteIdentifier(schema)}.migration_checkpoints
        (migration_id, phase, source_schema, source_fingerprint)
      VALUES (${quoteLiteral(migrationId)}, ${quoteLiteral(phase)}, ${quoteLiteral(sourceSchema)}, ${quoteLiteral(fingerprint)})
      ON DUPLICATE KEY UPDATE
        source_schema = VALUES(source_schema),
        source_fingerprint = VALUES(source_fingerprint),
        completed_at = CURRENT_TIMESTAMP
    ` });
  }
}

function sourceColumns(admin, sourceSchema, table) {
  const output = admin({ sql: `
    SELECT column_name
      FROM information_schema.columns
     WHERE table_schema = ${quoteLiteral(sourceSchema)}
       AND table_name = ${quoteLiteral(table)}
     ORDER BY ordinal_position
  ` }).stdout;
  const columns = output.split(/\r?\n/).filter(Boolean);
  if (columns.length === 0) fail(`source table is missing or has no columns: ${sourceSchema}.${table}`);
  return columns;
}

function createAndCopyTables(admin, plan, sourceSchema) {
  for (const entry of plan.tables) {
    const targetSchema = plan.schemaByOwner.get(entry.owner);
    admin({ sql: `CREATE TABLE IF NOT EXISTS ${quoteIdentifier(targetSchema)}.${quoteIdentifier(entry.table)} LIKE ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(entry.table)}` });
    const columns = sourceColumns(admin, sourceSchema, entry.table);
    admin({ sql: buildCopyStatement({ sourceSchema, targetSchema, table: entry.table, columns }) });
  }
}

function ownerByTable(plan) {
  return new Map(plan.tables.map((entry) => [entry.table, entry.owner]));
}

function restoreOwnerInternalForeignKeys(admin, plan, sourceSchema) {
  const rows = parseTabRows(admin({ sql: `
    SELECT k.table_name, k.constraint_name,
           GROUP_CONCAT(k.column_name ORDER BY k.ordinal_position SEPARATOR ','),
           k.referenced_table_name,
           GROUP_CONCAT(k.referenced_column_name ORDER BY k.ordinal_position SEPARATOR ','),
           r.update_rule, r.delete_rule
      FROM information_schema.key_column_usage k
      JOIN information_schema.referential_constraints r
        ON r.constraint_schema = k.constraint_schema
       AND r.table_name = k.table_name
       AND r.constraint_name = k.constraint_name
     WHERE k.constraint_schema = ${quoteLiteral(sourceSchema)}
       AND k.referenced_table_name IS NOT NULL
     GROUP BY k.table_name, k.constraint_name, k.referenced_table_name, r.update_rule, r.delete_rule
     ORDER BY k.table_name, k.constraint_name
  ` }).stdout);
  const owners = ownerByTable(plan);
  let restored = 0;
  for (const [table, constraint, columns, referencedTable, referencedColumns, updateRule, deleteRule] of rows) {
    const owner = owners.get(table);
    const referencedOwner = owners.get(referencedTable);
    if (!owner || !referencedOwner) fail(`source foreign key refers to a non-canonical table: ${table}.${constraint}`);
    if (owner !== referencedOwner) continue;
    const schema = plan.schemaByOwner.get(owner);
    const exists = admin({ sql: `
      SELECT COUNT(*) FROM information_schema.table_constraints
       WHERE constraint_schema = ${quoteLiteral(schema)}
         AND table_name = ${quoteLiteral(table)}
         AND constraint_name = ${quoteLiteral(constraint)}
         AND constraint_type = 'FOREIGN KEY'
    ` }).stdout.trim();
    if (exists === '0') {
      const left = columns.split(',').map(quoteIdentifier).join(', ');
      const right = referencedColumns.split(',').map(quoteIdentifier).join(', ');
      admin({ sql: `ALTER TABLE ${quoteIdentifier(schema)}.${quoteIdentifier(table)} ADD CONSTRAINT ${quoteIdentifier(constraint)} FOREIGN KEY (${left}) REFERENCES ${quoteIdentifier(schema)}.${quoteIdentifier(referencedTable)} (${right}) ON UPDATE ${updateRule} ON DELETE ${deleteRule}` });
      restored += 1;
    }
  }
  return restored;
}

function initializeLocalArtifacts(admin, plan, sourceSchema) {
  // Keep #337's concrete per-service names and schema shape.  A generic
  // event_inbox/event_outbox table would be an unowned sixth contract and
  // would leave the real publishers/consumers without their storage.
  for (const entry of plan.runtimeTables) {
    const targetSchema = plan.schemaByOwner.get(entry.owner);
    admin({ sql: `CREATE TABLE IF NOT EXISTS ${quoteIdentifier(targetSchema)}.${quoteIdentifier(entry.table)} LIKE ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(entry.table)}` });
  }

  const learning = plan.schemaByOwner.get('LEARNING');
  const grade = plan.schemaByOwner.get('GRADE');
  const course = plan.schemaByOwner.get('COURSE');
  const assessment = plan.schemaByOwner.get('ASSESSMENT');

  // Grade's current t_grade_record is the canonical legacy source-grade
  // projection. Replaying it replaces partial copies and is safe before
  // cutover because this command requires a quiescent legacy source.
  admin({ sql: `DELETE FROM ${quoteIdentifier(grade)}.t_grade_record` });
  const gradeColumns = sourceColumns(admin, sourceSchema, 't_grade_record');
  admin({ sql: `INSERT INTO ${quoteIdentifier(grade)}.t_grade_record (${gradeColumns.map(quoteIdentifier).join(', ')}) SELECT ${gradeColumns.map((column) => `s.${quoteIdentifier(column)}`).join(', ')} FROM ${quoteIdentifier(sourceSchema)}.t_grade_record s` });

  // Rebuild the complete Learning roster and its course-level watermark from
  // the quiescent Course source.  One complete snapshot is authoritative even
  // for an empty course; member rows alone never prove readiness.
  admin({ sql: `DELETE FROM ${quoteIdentifier(learning)}.learning_course_member_projection` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(learning)}.learning_course_member_projection
      (course_id, user_id, membership_status, member_version, updated_at)
    SELECT m.course_id, m.user_id,
           CASE WHEN m.join_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'REMOVED' END,
           1, CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course_member m
    ON DUPLICATE KEY UPDATE
      membership_status = VALUES(membership_status),
      member_version = VALUES(member_version),
      updated_at = CURRENT_TIMESTAMP
  ` });
  admin({ sql: `DELETE FROM ${quoteIdentifier(learning)}.learning_course_membership_watermark` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(learning)}.learning_course_membership_watermark
      (course_id, snapshot_version, completed_at, updated_at)
    SELECT c.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course c
    ON DUPLICATE KEY UPDATE snapshot_version = VALUES(snapshot_version), updated_at = CURRENT_TIMESTAMP
  ` });

  // The rebuilt records below are historical facts: use the concrete #337
  // producer/consumer tables, mark them PUBLISHED/APPLIED, and never enqueue
  // them for a second external delivery at cutover.
  admin({ sql: `DELETE FROM ${quoteIdentifier(assessment)}.assessment_event_outbox WHERE event_id LIKE 'legacy-grade-%'` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(assessment)}.assessment_event_outbox
      (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, payload_json, routing_key, delivery_status, attempt_count, next_attempt_at, published_at)
    SELECT CONCAT('legacy-grade-', r.id),
           'assessment.source-grade.changed.v2',
           2,
           'assessment-source-grade',
           CONCAT(r.source_type, ':', COALESCE(r.source_id, 0), ':', r.student_id),
           1,
           'issue-341-migration',
           JSON_OBJECT('courseId', CAST(r.course_id AS CHAR), 'sourceType', r.source_type,
                       'sourceId', CAST(COALESCE(r.source_id, 0) AS CHAR), 'studentId', CAST(r.student_id AS CHAR),
                       'score', CASE WHEN r.grade_status IN ('SCORED', 'ADJUSTED') AND r.raw_score IS NOT NULL THEN r.raw_score ELSE NULL END,
                       'fullScore', i.full_score,
                       'status', CASE WHEN r.grade_status IN ('SCORED', 'ADJUSTED') AND r.raw_score IS NOT NULL THEN 'SCORED' ELSE 'UNGRADED' END,
                       'sourceVersion', 1),
           'onlinejudge.assessment.source-grade.changed.v2',
           'PUBLISHED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.t_grade_record r
      JOIN ${quoteIdentifier(sourceSchema)}.t_grade_item i ON i.id = r.grade_item_id
    ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), delivery_status = 'PUBLISHED',
                            published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
  ` });
  admin({ sql: `DELETE FROM ${quoteIdentifier(grade)}.grade_event_inbox WHERE consumer_name = 'grade' AND event_id LIKE 'legacy-grade-%'` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(grade)}.grade_event_inbox
      (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, processing_status, processed_at)
    SELECT 'grade', CONCAT('legacy-grade-', r.id), 'assessment.source-grade.changed.v2',
           'assessment-source-grade', CONCAT(r.source_type, ':', COALESCE(r.source_id, 0), ':', r.student_id),
           1, 'issue-341-migration', 'APPLIED', CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.t_grade_record r
    ON DUPLICATE KEY UPDATE processing_status = 'APPLIED', processed_at = CURRENT_TIMESTAMP
  ` });

  admin({ sql: `DELETE FROM ${quoteIdentifier(course)}.course_event_outbox WHERE event_id LIKE 'legacy-member-%' OR event_id LIKE 'legacy-roster-%'` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(course)}.course_event_outbox
      (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, payload_json, routing_key, delivery_status, attempt_count, next_attempt_at, published_at)
    SELECT CONCAT('legacy-member-', m.id),
           'course.member.changed.v2',
           2,
           'course-member',
           CONCAT(m.course_id, ':', m.user_id),
           1,
           'issue-341-migration',
           JSON_OBJECT('courseId', CAST(m.course_id AS CHAR), 'userId', CAST(m.user_id AS CHAR),
                       'membershipStatus', CASE WHEN m.join_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'REMOVED' END,
                       'memberVersion', 1),
           'onlinejudge.course.member.changed.v2',
           'PUBLISHED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course_member m
    ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), delivery_status = 'PUBLISHED',
                            published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
  ` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(course)}.course_event_outbox
      (event_id, event_type, payload_version, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, payload_json, routing_key, delivery_status, attempt_count, next_attempt_at, published_at)
    SELECT CONCAT('legacy-roster-', c.id),
           'course.membership.snapshot.v2', 2, 'course-membership-roster', CAST(c.id AS CHAR), 1,
           'issue-341-migration',
           JSON_OBJECT(
             'courseId', CAST(c.id AS CHAR), 'rosterVersion', 1,
             'members', COALESCE((
               SELECT JSON_ARRAYAGG(JSON_OBJECT(
                 'userId', CAST(m.user_id AS CHAR),
                 'membershipStatus', CASE WHEN m.join_status = 'ACTIVE' THEN 'ACTIVE' ELSE 'REMOVED' END,
                 'memberVersion', 1
               ))
                 FROM ${quoteIdentifier(sourceSchema)}.crs_course_member m
                WHERE m.course_id = c.id
             ), JSON_ARRAY())
           ),
           'onlinejudge.course.membership.snapshot.v2',
           'PUBLISHED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course c
    ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), delivery_status = 'PUBLISHED',
                            published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
  ` });
  admin({ sql: `DELETE FROM ${quoteIdentifier(learning)}.learning_event_inbox WHERE consumer_name = 'learning' AND (event_id LIKE 'legacy-member-%' OR event_id LIKE 'legacy-roster-%')` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(learning)}.learning_event_inbox
      (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, processing_status, processed_at)
    SELECT 'learning', CONCAT('legacy-member-', m.id), 'course.member.changed.v2',
           'course-member', CONCAT(m.course_id, ':', m.user_id), 1,
           'issue-341-migration', 'APPLIED', CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course_member m
    ON DUPLICATE KEY UPDATE processing_status = 'APPLIED', processed_at = CURRENT_TIMESTAMP
  ` });
  admin({ sql: `
    INSERT INTO ${quoteIdentifier(learning)}.learning_event_inbox
      (consumer_name, event_id, event_type, aggregate_type, aggregate_id, aggregate_version,
       correlation_id, processing_status, processed_at)
    SELECT 'learning', CONCAT('legacy-roster-', c.id), 'course.membership.snapshot.v2',
           'course-membership-roster', CAST(c.id AS CHAR), 1,
           'issue-341-migration', 'APPLIED', CURRENT_TIMESTAMP
      FROM ${quoteIdentifier(sourceSchema)}.crs_course c
    ON DUPLICATE KEY UPDATE processing_status = 'APPLIED', processed_at = CURRENT_TIMESTAMP
  ` });
}

function tableChecksum(admin, sourceSchema, targetSchema, table) {
  const rows = parseTabRows(admin({ sql: `CHECKSUM TABLE ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(table)}, ${quoteIdentifier(targetSchema)}.${quoteIdentifier(table)} EXTENDED` }).stdout);
  return rows.map((row) => ({ table: row[0], checksum: row[1] ?? null }));
}

function primaryKeyColumns(admin, schema, table) {
  return admin({ sql: `
    SELECT GROUP_CONCAT(column_name ORDER BY ordinal_position SEPARATOR ',')
      FROM information_schema.key_column_usage
     WHERE table_schema = ${quoteLiteral(schema)}
       AND table_name = ${quoteLiteral(table)}
       AND constraint_name = 'PRIMARY'
  ` }).stdout.trim().split(',').filter(Boolean);
}

function aggregateForTable(admin, schema, table, columns) {
  const values = columns.map((column) => `COALESCE(CAST(${quoteIdentifier(column)} AS CHAR), '<NULL>')`).join(', ');
  const row = parseTabRows(admin({ sql: `
    SELECT COUNT(*), COALESCE(SUM(CRC32(CONCAT_WS(CHAR(31), ${values}))), 0)
      FROM ${quoteIdentifier(schema)}.${quoteIdentifier(table)}
  ` }).stdout)[0] ?? ['0', '0'];
  return { rows: Number(row[0]), aggregateCrc32: row[1] };
}

function missingPrimaryKeyCount(admin, leftSchema, rightSchema, table, primaryKey) {
  const joins = primaryKey.map((column) => `r.${quoteIdentifier(column)} = l.${quoteIdentifier(column)}`).join(' AND ');
  return Number(admin({ sql: `
    SELECT COUNT(*)
      FROM ${quoteIdentifier(leftSchema)}.${quoteIdentifier(table)} l
     WHERE NOT EXISTS (
       SELECT 1 FROM ${quoteIdentifier(rightSchema)}.${quoteIdentifier(table)} r WHERE ${joins}
     )
  ` }).stdout.trim());
}

function verifyLogicalReferences(admin, plan) {
  const checks = [];
  for (const reference of plan.crossDomainReferences) {
    const target = reference.target.split('.');
    if (target.length !== 3) continue; // API/event contracts deliberately have no physical target table.
    const [targetOwner, targetTable, targetColumn] = target;
    if (!plan.schemaByOwner.has(targetOwner)) continue;
    const sourceSchema = plan.schemaByOwner.get(reference.consumer);
    const targetSchema = plan.schemaByOwner.get(targetOwner);
    const count = Number(admin({ sql: `
      SELECT COUNT(*)
        FROM ${quoteIdentifier(sourceSchema)}.${quoteIdentifier(reference.consumer_table)} s
        LEFT JOIN ${quoteIdentifier(targetSchema)}.${quoteIdentifier(targetTable)} t
          ON t.${quoteIdentifier(targetColumn)} = s.${quoteIdentifier(reference.column)}
       WHERE s.${quoteIdentifier(reference.column)} IS NOT NULL
         AND t.${quoteIdentifier(targetColumn)} IS NULL
    ` }).stdout.trim());
    checks.push({ reference: `${reference.consumer_table}.${reference.column}`, orphanCount: count });
  }
  return checks;
}

function verifyForeignKeys(admin, plan) {
  const owners = ownerByTable(plan);
  const checks = [];
  for (const entry of plan.tables) {
    const schema = plan.schemaByOwner.get(entry.owner);
    const rows = parseTabRows(admin({ sql: `
      SELECT referenced_table_schema, referenced_table_name
        FROM information_schema.key_column_usage
       WHERE table_schema = ${quoteLiteral(schema)}
         AND table_name = ${quoteLiteral(entry.table)}
         AND referenced_table_name IS NOT NULL
    ` }).stdout);
    for (const [referencedSchema, referencedTable] of rows) {
      checks.push({
        table: entry.table,
        referencedTable,
        accepted: owners.get(referencedTable) === entry.owner && referencedSchema === schema,
      });
    }
  }
  return checks;
}

function verifyProjectionCounts(admin, plan, sourceSchema) {
  const learning = plan.schemaByOwner.get('LEARNING');
  const grade = plan.schemaByOwner.get('GRADE');
  const assessment = plan.schemaByOwner.get('ASSESSMENT');
  const course = plan.schemaByOwner.get('COURSE');
  const sourceCourses = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.crs_course` }).stdout.trim());
  const targetCourses = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(learning)}.learning_course_membership_watermark` }).stdout.trim());
  const sourceRecords = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.t_grade_record` }).stdout.trim());
  const targetRecords = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(grade)}.t_grade_record` }).stdout.trim());
  const gradeReplay = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(grade)}.grade_event_inbox WHERE consumer_name = 'grade' AND event_type = 'assessment.source-grade.changed.v2'` }).stdout.trim());
  const learningReplay = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(learning)}.learning_event_inbox WHERE consumer_name = 'learning' AND event_type = 'course.member.changed.v2'` }).stdout.trim());
  const sourceMembers = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.crs_course_member` }).stdout.trim());
  const targetMembers = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(learning)}.learning_course_member_projection` }).stdout.trim());
  const invalidGradePayloads = Number(admin({ sql: `
    SELECT COUNT(*)
      FROM ${quoteIdentifier(assessment)}.assessment_event_outbox
     WHERE event_type = 'assessment.source-grade.changed.v2'
       AND (
         COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.courseId')), 'MISSING') <> 'STRING'
         OR COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.sourceId')), 'MISSING') <> 'STRING'
         OR COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.studentId')), 'MISSING') <> 'STRING'
         OR JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.sourceType')) NOT IN ('LAB', 'HWK')
         OR JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.status')) NOT IN ('SCORED', 'UNGRADED')
         OR COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.fullScore')), 'MISSING') NOT IN ('INTEGER', 'DOUBLE', 'DECIMAL')
         OR CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.fullScore')) AS DECIMAL(12, 2)) <= 0
         OR (JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.status')) = 'SCORED'
             AND COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.score')), 'MISSING') NOT IN ('INTEGER', 'DOUBLE', 'DECIMAL'))
         OR (JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.status')) = 'UNGRADED'
             AND COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.score')), 'MISSING') <> 'NULL')
       )
  ` }).stdout.trim());
  const invalidMemberPayloads = Number(admin({ sql: `
    SELECT COUNT(*)
      FROM ${quoteIdentifier(course)}.course_event_outbox
     WHERE event_type = 'course.member.changed.v2'
       AND (
         COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.courseId')), 'MISSING') <> 'STRING'
         OR COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.userId')), 'MISSING') <> 'STRING'
         OR JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.membershipStatus')) NOT IN ('ACTIVE', 'REMOVED')
         OR COALESCE(JSON_TYPE(JSON_EXTRACT(payload_json, '$.memberVersion')), 'MISSING') <> 'INTEGER'
         OR CAST(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.memberVersion')) AS UNSIGNED) < 1
       )
  ` }).stdout.trim());
  const gradePayloadDiagnostics = parseTabRows(admin({ sql: `
    SELECT event_id,
           JSON_TYPE(JSON_EXTRACT(payload_json, '$.courseId')),
           JSON_TYPE(JSON_EXTRACT(payload_json, '$.sourceId')),
           JSON_TYPE(JSON_EXTRACT(payload_json, '$.studentId')),
           JSON_TYPE(JSON_EXTRACT(payload_json, '$.score')),
           JSON_TYPE(JSON_EXTRACT(payload_json, '$.fullScore')),
           JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.status'))
      FROM ${quoteIdentifier(assessment)}.assessment_event_outbox
     WHERE event_type = 'assessment.source-grade.changed.v2'
     ORDER BY event_id
  ` }).stdout).map(([eventId, courseIdType, sourceIdType, studentIdType, scoreType, fullScoreType, status]) => ({
    eventId, courseIdType, sourceIdType, studentIdType, scoreType, fullScoreType, status,
  }));
  const localArtifacts = [];
  for (const owner of requiredOwners) {
    const schema = plan.schemaByOwner.get(owner);
    const expectedTables = plan.runtimeTables.filter((entry) => entry.owner === owner).map((entry) => entry.table);
    const count = expectedTables.length === 0 ? 0 : Number(admin({ sql: `SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ${quoteLiteral(schema)} AND table_name IN (${expectedTables.map(quoteLiteral).join(', ')})` }).stdout.trim());
    localArtifacts.push({ owner, tablesPresent: count, expectedTables: expectedTables.length });
  }
  return {
    gradeSourceProjection: {
      sourceRecords, targetRecords, replayedInbox: gradeReplay,
      invalidPayloads: invalidGradePayloads, payloadDiagnostics: gradePayloadDiagnostics,
    },
    learningCourseProjection: { sourceCourses, targetCourses, sourceMembers, targetMembers, replayedInbox: learningReplay, invalidPayloads: invalidMemberPayloads },
    localArtifacts,
  };
}

function provisionRuntimeUsers(admin, plan, options) {
  const passwords = requiredRuntimePasswords(plan, options);
  for (const owner of requiredOwners) {
    const account = plan.accountByOwner.get(owner);
    const password = passwords.get(owner);
    const schema = account.schema;
    assertIdentifier(account.account, 'runtime account');
    admin({ sql: `CREATE USER IF NOT EXISTS ${quoteLiteral(account.account)}@'%' IDENTIFIED BY ${quoteLiteral(password)}` });
    admin({ sql: `ALTER USER ${quoteLiteral(account.account)}@'%' IDENTIFIED BY ${quoteLiteral(password)}` });
    admin({ sql: `REVOKE ALL PRIVILEGES, GRANT OPTION FROM ${quoteLiteral(account.account)}@'%'` });
    admin({ sql: `GRANT SELECT, INSERT, UPDATE, DELETE ON ${quoteIdentifier(schema)}.* TO ${quoteLiteral(account.account)}@'%'` });
  }
  return passwords;
}

function verifyPermissions(admin, plan, options, passwords) {
  const probes = [];
  for (const owner of requiredOwners) {
    const account = plan.accountByOwner.get(owner);
    const ownTable = plan.tables.find((entry) => entry.owner === owner)?.table;
    const mysql = createMysql(options, account.account, passwords.get(owner));
    const own = mysql({ sql: `SELECT 1 FROM ${quoteIdentifier(account.schema)}.${quoteIdentifier(ownTable)} LIMIT 0`, allowFailure: true });
    probes.push({ owner, kind: 'own_select', expected: 'allow', passed: own.code === 0, raw: own.raw.trim() });
    const dmlProbeId = `issue341-permission-${owner.toLowerCase()}-${Date.now()}`;
    // migration_checkpoints is in every owner schema and exists before
    // application runtime.  It gives the 45-probe matrix a genuine DML target
    // without fabricating generic #337 inbox tables.
    const insert = mysql({ sql: `INSERT INTO ${quoteIdentifier(account.schema)}.migration_checkpoints (migration_id, phase, source_schema, source_fingerprint) VALUES (${quoteLiteral(dmlProbeId)}, 'PROBE', ${quoteLiteral(options.sourceSchema)}, REPEAT('0', 64))`, allowFailure: true });
    probes.push({ owner, kind: 'own_insert', expected: 'allow', passed: insert.code === 0, raw: insert.raw.trim() });
    const update = mysql({ sql: `UPDATE ${quoteIdentifier(account.schema)}.migration_checkpoints SET phase = 'PROBE_UPDATED' WHERE migration_id = ${quoteLiteral(dmlProbeId)}`, allowFailure: true });
    probes.push({ owner, kind: 'own_update', expected: 'allow', passed: update.code === 0, raw: update.raw.trim() });
    const remove = mysql({ sql: `DELETE FROM ${quoteIdentifier(account.schema)}.migration_checkpoints WHERE migration_id = ${quoteLiteral(dmlProbeId)}`, allowFailure: true });
    probes.push({ owner, kind: 'own_delete', expected: 'allow', passed: remove.code === 0, raw: remove.raw.trim() });
    for (const foreignOwner of requiredOwners.filter((candidate) => candidate !== owner)) {
      const foreignSchema = plan.schemaByOwner.get(foreignOwner);
      const foreignTable = plan.tables.find((entry) => entry.owner === foreignOwner)?.table;
      const denied = mysql({ sql: `SELECT 1 FROM ${quoteIdentifier(foreignSchema)}.${quoteIdentifier(foreignTable)} LIMIT 0`, allowFailure: true });
      probes.push({ owner, kind: `foreign_select:${foreignOwner}`, expected: 'deny', passed: denied.code !== 0, raw: denied.raw.trim() });
    }
    const ddlProbeTable = `__issue341_ddl_probe_${owner.toLowerCase()}_${Date.now()}`;
    const ddl = mysql({ sql: `CREATE TABLE ${quoteIdentifier(account.schema)}.${quoteIdentifier(ddlProbeTable)} (id INT)`, allowFailure: true });
    if (ddl.code === 0) {
      // A misconfigured account must make verification fail, but cleanup is
      // still required so a later run cannot mistake "already exists" for a
      // permission denial.
      admin({ sql: `DROP TABLE IF EXISTS ${quoteIdentifier(account.schema)}.${quoteIdentifier(ddlProbeTable)}` });
    }
    probes.push({
      owner,
      kind: 'own_ddl',
      expected: 'deny',
      passed: ddl.code !== 0 && isMysqlAuthorizationDenied(ddl.raw),
      raw: ddl.raw.trim(),
    });
  }
  return probes;
}

function isMysqlAuthorizationDenied(raw) {
  return /ERROR 1142|command denied/i.test(raw);
}

function verifyAll(admin, plan, options, sourceSchema, passwords) {
  const tables = [];
  const failures = [];
  for (const entry of plan.tables) {
    const targetSchema = plan.schemaByOwner.get(entry.owner);
    const sourceColumnsForTable = sourceColumns(admin, sourceSchema, entry.table);
    const sourcePk = primaryKeyColumns(admin, sourceSchema, entry.table);
    const targetPk = primaryKeyColumns(admin, targetSchema, entry.table);
    const sourceAggregate = aggregateForTable(admin, sourceSchema, entry.table, sourceColumnsForTable);
    const targetAggregate = aggregateForTable(admin, targetSchema, entry.table, sourceColumnsForTable);
    const sourceOnlyPk = missingPrimaryKeyCount(admin, sourceSchema, targetSchema, entry.table, sourcePk);
    const targetOnlyPk = missingPrimaryKeyCount(admin, targetSchema, sourceSchema, entry.table, sourcePk);
    const checksum = tableChecksum(admin, sourceSchema, targetSchema, entry.table);
    const sourceChecksum = checksum.find((value) => value.table === `${sourceSchema}.${entry.table}`)?.checksum;
    const targetChecksum = checksum.find((value) => value.table === `${targetSchema}.${entry.table}`)?.checksum;
    const passed = sourceAggregate.rows === targetAggregate.rows
      && sourceAggregate.aggregateCrc32 === targetAggregate.aggregateCrc32
      && sourcePk.join(',') === targetPk.join(',')
      && sourceOnlyPk === 0 && targetOnlyPk === 0
      && sourceChecksum === targetChecksum;
    const row = {
      table: entry.table,
      owner: entry.owner,
      source: { ...sourceAggregate, checksum: sourceChecksum },
      target: { ...targetAggregate, checksum: targetChecksum },
      primaryKey: sourcePk,
      sourceOnlyPk,
      targetOnlyPk,
      passed,
    };
    tables.push(row);
    if (!passed) failures.push(`table validation failed: ${entry.table}`);
  }
  const logicalReferences = verifyLogicalReferences(admin, plan);
  for (const check of logicalReferences) if (check.orphanCount !== 0) failures.push(`orphan logical reference: ${check.reference}=${check.orphanCount}`);
  const foreignKeys = verifyForeignKeys(admin, plan);
  for (const check of foreignKeys) if (!check.accepted) failures.push(`cross-domain foreign key remains: ${check.table}->${check.referencedTable}`);
  const projections = verifyProjectionCounts(admin, plan, sourceSchema);
  if (projections.gradeSourceProjection.sourceRecords !== projections.gradeSourceProjection.targetRecords) failures.push('grade source projection count mismatch');
  if (projections.gradeSourceProjection.sourceRecords !== projections.gradeSourceProjection.replayedInbox) failures.push('grade source projection replay mismatch');
  if (projections.gradeSourceProjection.invalidPayloads !== 0) failures.push('grade source replay payload contract mismatch');
  if (projections.learningCourseProjection.sourceCourses !== projections.learningCourseProjection.targetCourses) failures.push('learning course projection count mismatch');
  if (projections.learningCourseProjection.sourceMembers !== projections.learningCourseProjection.targetMembers) failures.push('learning member projection count mismatch');
  if (projections.learningCourseProjection.sourceMembers !== projections.learningCourseProjection.replayedInbox) failures.push('learning inbox replay mismatch');
  if (projections.learningCourseProjection.invalidPayloads !== 0) failures.push('learning member replay payload contract mismatch');
  for (const artifact of projections.localArtifacts) {
    if (artifact.tablesPresent !== artifact.expectedTables) {
      failures.push(`missing local reliable tables: ${artifact.owner}`);
    }
  }
  const permissions = verifyPermissions(admin, plan, options, passwords);
  if (permissions.length !== requiredOwners.length * 9) {
    failures.push('permission evidence must contain 45 complete allow/deny probes');
  }
  for (const probe of permissions) if (!probe.passed) failures.push(`permission validation failed: ${probe.owner}.${probe.kind}`);
  return { passed: failures.length === 0, failures, tables, logicalReferences, foreignKeys, projections, permissions };
}

function writeEvidence(path, evidence) {
  if (!path) return;
  const absolute = resolve(path);
  mkdirSync(dirname(absolute), { recursive: true });
  writeFileSync(absolute, `${JSON.stringify(evidence, null, 2)}\n`, 'utf8');
}

// Traffic-state files are deployment control-plane inputs.  They must be
// publishable from an empty evidence workspace, and readers must never see a
// truncated state after the migration command has claimed a switch happened.
export function writeControlState(path, state) {
  const absolute = resolve(path);
  const parent = dirname(absolute);
  mkdirSync(parent, { recursive: true });
  const temporary = resolve(parent, `.${migrationId}.${process.pid}.${randomUUID()}.tmp`);
  writeFileSync(temporary, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
  renameSync(temporary, absolute);
}

function currentGitSha() {
  try {
    return execFileSync('git', ['-C', repositoryRoot, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
  } catch {
    return null;
  }
}

function sourceRecoveryProbe(admin, sourceSchema) {
  const users = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.t_auth_user` }).stdout.trim());
  const courses = Number(admin({ sql: `SELECT COUNT(*) FROM ${quoteIdentifier(sourceSchema)}.crs_course` }).stdout.trim());
  return { legacySchemaReachable: true, users, courses };
}

export async function main(argv = process.argv.slice(2)) {
  const options = parseArguments(argv);
  if (options.help) {
    process.stdout.write(`${usage()}\n`);
    return { help: true };
  }
  if (!['migrate', 'verify', 'replay', 'cutover', 'rollback'].includes(options.action)) {
    fail(`unsupported --action ${options.action}`);
  }
  if (!options.adminUser || !options.sourceSchema) {
    fail('--admin-user and --source-schema are required');
  }
  assertIdentifier(options.sourceSchema, 'source schema');
  if (!existsSync(options.mysql) && !options.mysql.includes('/')) {
    // spawnSync gives a clearer platform-specific error for PATH lookup; no-op here.
  }
  const adminPassword = process.env[options.adminPasswordEnv];
  if (typeof adminPassword !== 'string' || adminPassword.length === 0) {
    fail(`set ${options.adminPasswordEnv} for the disposable MySQL administrator`);
  }
  const plan = loadFiveDomainPlan();
  // Every action that could claim migration success, including a rollback
  // recovery, must prove the same five-account ownership boundary.  Keeping
  // this gate ahead of action dispatch prevents a rollback/no-op shortcut
  // from writing a convincing-looking PASS with zero runtime probes.
  const passwords = requiredRuntimePasswords(plan, options);
  const admin = createMysql(options, options.adminUser, adminPassword);
  const base = {
    issue: 341,
    action: options.action,
    environment: { mysql: options.mysql, host: options.host, port: Number(options.port), sourceSchema: options.sourceSchema },
    baseSha: process.env.OJ_BASE_SHA ?? null,
    testedSha: currentGitSha(),
    sourceVersion: sourceVersion(admin, options.sourceSchema),
    startedAt: new Date().toISOString(),
  };
  if (sourceTableCount(admin, options.sourceSchema) !== plan.allTables.length) {
    fail(`source schema ${options.sourceSchema} does not contain the 46 business tables and ${plan.runtimeTables.length} reliable runtime tables`);
  }

  if (options.action === 'rollback') {
    if (!options.cutoverState) fail('--cutover-state is required for rollback evidence');
    const verification = verifyAll(admin, plan, options, options.sourceSchema, passwords);
    if (!verification.passed) {
      writeEvidence(options.evidence, {
        ...base, result: 'FAIL', verification, finishedAt: new Date().toISOString(),
      });
      fail(verification.failures.join('; '));
    }
    const prior = existsSync(options.cutoverState) ? JSON.parse(readFileSync(options.cutoverState, 'utf8')) : null;
    const recovery = sourceRecoveryProbe(admin, options.sourceSchema);
    const state = {
      topology: 'LEGACY_MONOLITH',
      migrationId,
      sourceSchema: options.sourceSchema,
      rollbackAt: new Date().toISOString(),
      priorTopology: prior?.topology ?? null,
      recovery,
    };
    writeControlState(options.cutoverState, state);
    const evidence = { ...base, result: 'PASS', verification, rollback: state, finishedAt: new Date().toISOString() };
    writeEvidence(options.evidence, evidence);
    process.stdout.write(`${JSON.stringify(evidence)}\n`);
    return evidence;
  }

  if ((options.action === 'migrate' || options.action === 'replay') && !options.sourceReadOnlyAck) {
    fail(`${options.action} requires --source-read-only-ack; do not copy a live writable legacy schema`);
  }
  const beforeFingerprint = sourceFingerprint(admin, plan, options.sourceSchema);

  if (options.action === 'migrate') {
    ensureTargetControlTables(admin, plan);
    const checksum = installVersion(admin, plan);
    createAndCopyTables(admin, plan, options.sourceSchema);
    checkpoint(admin, plan, 'SCHEMA_AND_DATA_COPIED', options.sourceSchema, beforeFingerprint);
    const restoredForeignKeys = restoreOwnerInternalForeignKeys(admin, plan, options.sourceSchema);
    initializeLocalArtifacts(admin, plan, options.sourceSchema);
    checkpoint(admin, plan, 'LOCAL_ARTIFACTS_INITIALIZED', options.sourceSchema, beforeFingerprint);
    const afterFingerprint = sourceFingerprint(admin, plan, options.sourceSchema);
    if (afterFingerprint !== beforeFingerprint) {
      fail('legacy source changed during migration; no traffic switch is allowed, fix the source and rerun from checkpoints');
    }
    provisionRuntimeUsers(admin, plan, options);
    const verification = verifyAll(admin, plan, options, options.sourceSchema, passwords);
    if (!verification.passed) {
      writeEvidence(options.evidence, { ...base, result: 'FAIL', migrationId, sourceFingerprint: afterFingerprint, verification, finishedAt: new Date().toISOString() });
      fail(verification.failures.join('; '));
    }
    checkpoint(admin, plan, 'VERIFIED', options.sourceSchema, afterFingerprint);
    const evidence = {
      ...base, result: 'PASS', migrationId, migrationChecksum: checksum, sourceFingerprint: afterFingerprint,
      restoredForeignKeys, verification, finishedAt: new Date().toISOString(),
    };
    writeEvidence(options.evidence, evidence);
    process.stdout.write(`${JSON.stringify(evidence)}\n`);
    return evidence;
  }

  if (options.action === 'replay') {
    initializeLocalArtifacts(admin, plan, options.sourceSchema);
    const afterFingerprint = sourceFingerprint(admin, plan, options.sourceSchema);
    if (afterFingerprint !== beforeFingerprint) fail('legacy source changed during projection replay');
    const verification = verifyAll(admin, plan, options, options.sourceSchema, passwords);
    if (!verification.passed) {
      writeEvidence(options.evidence, { ...base, result: 'FAIL', sourceFingerprint: afterFingerprint, verification, finishedAt: new Date().toISOString() });
      fail(verification.failures.join('; '));
    }
    checkpoint(admin, plan, 'PROJECTIONS_REPLAYED', options.sourceSchema, afterFingerprint);
    const evidence = { ...base, result: 'PASS', sourceFingerprint: afterFingerprint, verification, finishedAt: new Date().toISOString() };
    writeEvidence(options.evidence, evidence);
    process.stdout.write(`${JSON.stringify(evidence)}\n`);
    return evidence;
  }

  const verification = verifyAll(admin, plan, options, options.sourceSchema, passwords);
  if (!verification.passed) {
    writeEvidence(options.evidence, { ...base, result: 'FAIL', verification, finishedAt: new Date().toISOString() });
    fail(verification.failures.join('; '));
  }
  if (options.action === 'cutover') {
    if (!options.cutoverState) fail('--cutover-state is required for cutover evidence');
    const state = {
      topology: 'FIVE_DOMAIN', migrationId, sourceSchema: options.sourceSchema,
      targetSchemas: Object.fromEntries(plan.schemaByOwner), cutoverAt: new Date().toISOString(),
    };
    writeControlState(options.cutoverState, state);
    const evidence = { ...base, result: 'PASS', verification, cutover: state, finishedAt: new Date().toISOString() };
    writeEvidence(options.evidence, evidence);
    process.stdout.write(`${JSON.stringify(evidence)}\n`);
    return evidence;
  }
  const evidence = { ...base, result: 'PASS', verification, finishedAt: new Date().toISOString() };
  writeEvidence(options.evidence, evidence);
  process.stdout.write(`${JSON.stringify(evidence)}\n`);
  return evidence;
}

if (process.argv[1] && resolve(process.argv[1]) === scriptPath) {
  main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  });
}
