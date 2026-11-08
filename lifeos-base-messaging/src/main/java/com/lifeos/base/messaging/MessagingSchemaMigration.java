package com.lifeos.base.messaging;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

/**
 * Creates and upgrades the library's own tables ({@code outbox_event}, {@code inbox_message}) with a separate
 * Flyway history table, so the library's migration versions never collide with the service's
 * {@code db/migration}. Runs during startup, before the relay starts.
 */
public class MessagingSchemaMigration implements InitializingBean {

    public static final String LOCATION = "classpath:db/lifeos-base/messaging";
    public static final String HISTORY_TABLE = "flyway_schema_history_lifeos_base";

    private final DataSource dataSource;

    public MessagingSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                // The service's own tables already exist in the schema: start our history at version 0.
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
