# Database migration policy

The backend migration directory intentionally contains **one consolidated baseline**:
`src/main/resources/db/migration/B8__current_schema.sql`.

- **New, empty PostgreSQL databases:** Flyway runs B8 and creates the final schema.
  Local-profile demo accounts are created by Java bootstrap, not by SQL.
- **Databases already upgraded to V8:** the original V1–V8 files have been
  removed from the current branch, but the existing
  `flyway_schema_history` records **must not be deleted or edited**. The
  application ignores *missing* historical migrations while retaining
  validation of the schema through `spring.jpa.hibernate.ddl-auto=validate`.
  Back up the database before applying any deployment.
- **Databases still below V8:** do not start this consolidated version against
  them. First run the **last historical release that contains V1–V8**, allowing
  its Flyway migrations to finish. Then deploy this version. The Git tag or
  historical commit `93209cade099b55c16ae945c7c0354df0ad62d89` retains the
  old scripts. Do not use `flyway clean`, recreate the schema, or insert fake
  Flyway history entries in a real database.

The single baseline does **not** replace incremental migrations for future
schema changes. New changes must receive a new version (V9, V10, etc.);
otherwise existing installations will not receive the changes.

The old `src/test/resources/test/demo_users.sql` is a test-only data fixture,
not a Flyway migration.
