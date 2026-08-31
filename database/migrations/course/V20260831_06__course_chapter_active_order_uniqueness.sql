-- #312 review: CRS-SC-02 requires a 409 for a sibling sort-order conflict.
-- The application check gives callers the documented error, and this unique
-- index makes the invariant database-safe under concurrent writes.  Active
-- siblings are keyed by (course_id, ordering_parent, active_order); soft
-- deleted rows carry a NULL active_order and never collide, and the generated
-- ordering_parent turns NULL (root) parents into a concrete key value.
SET @oj312_schema = DATABASE();

-- Deterministic one-time normalization of any legacy duplicate sibling
-- orders: keep the relative (sort_order, id) order and renumber the active
-- siblings of every parent to 0..n-1 before the unique index is added.
DROP TEMPORARY TABLE IF EXISTS oj312_chapter_order_fix;
CREATE TEMPORARY TABLE oj312_chapter_order_fix AS
SELECT id,
       ROW_NUMBER() OVER (PARTITION BY course_id, COALESCE(parent_id, -1) ORDER BY sort_order, id) - 1 AS next_order
  FROM crs_chapter
 WHERE is_deleted = 0;

UPDATE crs_chapter c
  JOIN oj312_chapter_order_fix f ON f.id = c.id
   SET c.sort_order = f.next_order
 WHERE c.sort_order <> f.next_order;

DROP TEMPORARY TABLE IF EXISTS oj312_chapter_order_fix;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_chapter' AND column_name = 'ordering_parent') = 0,
    'ALTER TABLE crs_chapter ADD COLUMN ordering_parent BIGINT GENERATED ALWAYS AS (COALESCE(parent_id, -1)) VIRTUAL',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
      WHERE table_schema = @oj312_schema AND table_name = 'crs_chapter' AND column_name = 'active_order') = 0,
    'ALTER TABLE crs_chapter ADD COLUMN active_order BIGINT GENERATED ALWAYS AS (CASE WHEN is_deleted = 0 THEN sort_order ELSE NULL END) VIRTUAL',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;

SET @oj312_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
      WHERE table_schema = @oj312_schema AND table_name = 'crs_chapter' AND index_name = 'uq_crs_chapter_active_order') = 0,
    'CREATE UNIQUE INDEX uq_crs_chapter_active_order ON crs_chapter(course_id, ordering_parent, active_order)',
    'SELECT 1'
);
PREPARE oj312_statement FROM @oj312_sql;
EXECUTE oj312_statement;
DEALLOCATE PREPARE oj312_statement;
