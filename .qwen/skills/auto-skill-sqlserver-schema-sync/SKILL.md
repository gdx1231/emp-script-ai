---
name: sqlserver-schema-sync
description: Compare two SQL Server databases and sync missing tables and columns from source to target using sqlcmd and a Python script.
source: auto-skill
extracted_at: '2026-06-17T02:31:14.932Z'
---

# SQL Server Schema Sync

Compare two SQL Server databases on the same server and generate SQL to add missing tables and columns to the target database.

## Prerequisites

- `sqlcmd` installed (check with `which sqlcmd`)
- Both databases accessible from the same SQL Server instance
- `python3` available for script generation

## Procedure

### Step 1: Export missing tables metadata

Use `sqlcmd` with cross-database `INFORMATION_SCHEMA` queries to find tables in the source that don't exist in the target:

```bash
sqlcmd -S <server> -U <user> -P '<password>' -d <source_db> -W -h -1 -s"," -o /tmp/create_tables.csv -Q "
SELECT 
    c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE,
    c.CHARACTER_MAXIMUM_LENGTH, c.NUMERIC_PRECISION, c.NUMERIC_SCALE,
    c.IS_NULLABLE, c.COLUMN_DEFAULT, c.ORDINAL_POSITION
FROM <source_db>.INFORMATION_SCHEMA.COLUMNS c
WHERE c.TABLE_NAME IN (
    SELECT b.TABLE_NAME FROM <source_db>.INFORMATION_SCHEMA.TABLES b 
    WHERE b.TABLE_TYPE='BASE TABLE' 
    AND NOT EXISTS (
        SELECT 1 FROM <target_db>.INFORMATION_SCHEMA.TABLES a 
        WHERE a.TABLE_NAME = b.TABLE_NAME AND a.TABLE_TYPE='BASE TABLE'
    )
)
ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION
"
```

### Step 2: Export missing columns metadata

Find columns in shared tables that exist in source but not in target:

```bash
sqlcmd -S <server> -U <user> -P '<password>' -d <source_db> -W -h -1 -s"," -o /tmp/alter_columns.csv -Q "
SELECT 
    b.TABLE_NAME, b.COLUMN_NAME, b.DATA_TYPE,
    b.CHARACTER_MAXIMUM_LENGTH, b.NUMERIC_PRECISION, b.NUMERIC_SCALE,
    b.IS_NULLABLE, b.COLUMN_DEFAULT, b.ORDINAL_POSITION
FROM <source_db>.INFORMATION_SCHEMA.COLUMNS b
WHERE EXISTS (
    SELECT 1 FROM <target_db>.INFORMATION_SCHEMA.TABLES a 
    WHERE a.TABLE_NAME = b.TABLE_NAME AND a.TABLE_TYPE='BASE TABLE'
)
AND NOT EXISTS (
    SELECT 1 FROM <target_db>.INFORMATION_SCHEMA.COLUMNS c 
    WHERE c.TABLE_NAME = b.TABLE_NAME AND c.COLUMN_NAME = b.COLUMN_NAME
)
ORDER BY b.TABLE_NAME, b.ORDINAL_POSITION
"
```

### Step 3: Generate SQL script with Python

Write a Python script that:
1. Parses the CSV output (comma-separated, no header since `-h -1`)
2. Maps SQL Server types correctly:
   - `varchar(-1)` / `nvarchar(-1)` → `varchar(max)` / `nvarchar(max)`
   - `numeric(p,s)` / `decimal(p,s)` → keep precision/scale
   - `ntext`, `text`, `geography`, `money`, `bit`, `datetime` → use as-is
3. Generates `CREATE TABLE` statements wrapped in `IF NOT EXISTS` guards
4. Generates `ALTER TABLE ADD COLUMN` statements wrapped in `IF NOT EXISTS` guards
5. Includes `DEFAULT` constraints from `COLUMN_DEFAULT` column
6. Uses `USE <target_db>` and `GO` batch separators

### Step 4: Execute the generated SQL

```bash
sqlcmd -S <server> -U <user> -P '<password>' -d <target_db> -i /tmp/sync_schema.sql
```

### Step 5: Verify results

Run the same comparison queries again to confirm 0 missing tables and 0 missing columns:

```bash
sqlcmd -S <server> -U <user> -P '<password>' -d <target_db> -W -h -1 -Q "
SELECT COUNT(*) AS missing_tables FROM (
    SELECT b.TABLE_NAME FROM <source_db>.INFORMATION_SCHEMA.TABLES b 
    WHERE b.TABLE_TYPE='BASE TABLE' 
    AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES a WHERE a.TABLE_NAME = b.TABLE_NAME AND a.TABLE_TYPE='BASE TABLE')
) t
"
```

## Key Notes

- Use `-h -1` with sqlcmd to suppress column headers (cleaner CSV parsing)
- Use `-W` to trim trailing spaces
- Use `-s","` for comma delimiter
- The `COLUMN_DEFAULT` values from `INFORMATION_SCHEMA` include parentheses, e.g. `((0))`, `('text')`, `(getdate())` — use them as-is in `DEFAULT` clauses
- SQL Server 2008 compatible: no `STRING_AGG`, use cross-database `INFORMATION_SCHEMA` references
- For large schemas, increase `--max-rows` or remove the default 200-row limit
