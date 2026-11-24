package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Flyway Java migration: Enforces globally unique case-insensitive usernames,
 * emails, organization codes, and slugs.
 * On PostgreSQL 16: Executes native expression functional indexes on LOWER(username), LOWER(email), etc.
 * On H2: Executes standard index creation to preserve cross-database compatibility in test suites.
 */
public class V14__globally_unique_case_insensitive_usernames extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        String dbName = connection.getMetaData().getDatabaseProductName();

        try (Statement statement = connection.createStatement()) {
            if ("PostgreSQL".equalsIgnoreCase(dbName)) {
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users (LOWER(username))");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email_lower ON users (LOWER(email))");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orgs_code_upper ON organizations (UPPER(org_code))");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orgs_slug_lower ON organizations (LOWER(slug))");
            } else {
                // H2 / embedded test compatibility
                statement.execute("CREATE INDEX IF NOT EXISTS idx_users_username_lower ON users(username)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users(email)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_orgs_code_upper ON organizations(org_code)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_orgs_slug_lower ON organizations(slug)");
            }
        }
    }
}
