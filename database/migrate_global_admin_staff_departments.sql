-- Upgrade an existing campus_voice database to a global admin and department-bound staff.
-- Safe to run when users.department_id already exists and the legacy check is present or absent.
USE campus_voice;

SET @department_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'department_id'
);
SET @ddl = IF(@department_column_exists = 0,
    'ALTER TABLE users ADD COLUMN department_id INT NULL AFTER role',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

ALTER TABLE users MODIFY COLUMN password_hash VARCHAR(255) NULL;

SET @legacy_check_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'users'
      AND constraint_name = 'chk_admin_department' AND constraint_type = 'CHECK'
);
SET @ddl = IF(@legacy_check_exists > 0,
    'ALTER TABLE users DROP CHECK chk_admin_department',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

SET @department_fk_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'users'
      AND constraint_name = 'fk_user_department' AND constraint_type = 'FOREIGN KEY'
);
SET @ddl = IF(@department_fk_exists = 0,
    CONCAT('ALTER TABLE users ADD CONSTRAINT fk_user_department FOREIGN KEY (department_id) ',
           'REFERENCES departments(department_id) ON DELETE RESTRICT ON UPDATE CASCADE'),
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

-- Global admins and students are not department-bound. Staff without a saved department
-- must complete first-time setup again; their previous password hash is cleared.
UPDATE users SET department_id = NULL WHERE role IN ('ADMIN', 'STUDENT');
UPDATE users SET password_hash = NULL WHERE role = 'STAFF' AND department_id IS NULL;

-- Remove legacy assignments that target an admin, an unconfigured staff account, or staff
-- from a different department. The global admin can reassign these complaints after migration.
UPDATE complaints c
LEFT JOIN users assignee ON assignee.user_id = c.assigned_to
SET c.assigned_to = NULL
WHERE c.assigned_to IS NOT NULL
  AND (assignee.role <> 'STAFF' OR assignee.department_id IS NULL
       OR assignee.department_id <> c.department_id);

-- Staff may remain unassigned until first-time setup saves their password and department.
SET @scope_check_exists = (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'users'
      AND constraint_name = 'chk_user_department_scope' AND constraint_type = 'CHECK'
);
SET @ddl = IF(@scope_check_exists > 0,
    'ALTER TABLE users DROP CHECK chk_user_department_scope',
    'SELECT 1');
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;

ALTER TABLE users
    ADD CONSTRAINT chk_user_department_scope CHECK (
        (role IN ('STUDENT','ADMIN') AND department_id IS NULL AND password_hash IS NOT NULL)
        OR (role = 'STAFF' AND (
            (department_id IS NULL AND password_hash IS NULL)
            OR (department_id IS NOT NULL AND password_hash IS NOT NULL)
        ))
    );
