package com.lifeos.base.messaging;

import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxRelay;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox, relay and inbox for a service with a PostgreSQL DataSource and Kafka.
 *
 * <p>The relay uses its own String/String producer (acks=all, idempotent) built from {@code spring.kafka.*},
 * so the service is free to configure its own producers differently.
 */
@AutoConfiguration(after = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        KafkaAutoConfiguration.class,
        JacksonAutoConfiguration.class
})
@ConditionalOnSingleCandidate(DataSource.class)
@EnableConfigurationProperties(LifeOsMessagingProperties.class)
public class LifeOsMessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventEnvelopeCodec eventEnvelopeCodec(JsonMapper jsonMapper) {
        return new EventEnvelopeCodec(jsonMapper);
    }

    /**
     * Runs after the service's own Flyway migration: on an empty database the service's Flyway must see an
     * empty schema, otherwise it refuses to start ("non-empty schema without schema history table").
     */
    @Bean
    @ConditionalOnProperty(prefix = "lifeos.messaging.schema", name = "migrate", matchIfMissing = true)
    public MessagingSchemaMigration lifeOsMessagingSchemaMigration(
            DataSource dataSource, ObjectProvider<FlywayMigrationInitializer> serviceFlyway) {
        serviceFlyway.orderedStream().forEach(initializer -> { });
        return new MessagingSchemaMigration(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public Inbox inbox(DataSource dataSource, PlatformTransactionManager transactionManager,
                       EventEnvelopeCodec codec, Clock clock, LifeOsMessagingProperties properties,
                       Environment environment, ObjectProvider<MessagingSchemaMigration> migration) {
        migration.ifAvailable(m -> { }); // tables exist before first use
        String consumer = properties.inbox().consumer() != null
                ? properties.inbox().consumer()
                : environment.getProperty("spring.application.name");
        return new Inbox(JdbcClient.create(dataSource), new TransactionTemplate(transactionManager), codec, clock,
                consumer);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lifeos.messaging.outbox", name = "enabled", matchIfMissing = true)
    public Outbox outbox(DataSource dataSource, JsonMapper jsonMapper, Clock clock,
                         ObjectProvider<MessagingSchemaMigration> migration) {
        migration.ifAvailable(m -> { });
        return new Outbox(JdbcClient.create(dataSource), jsonMapper, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "lifeos.messaging.outbox", name = "enabled", matchIfMissing = true)
    public DefaultKafkaProducerFactory<String, String> lifeOsOutboxProducerFactory(
            KafkaProperties kafkaProperties, ObjectProvider<KafkaConnectionDetails> connectionDetails) {
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties());
        KafkaConnectionDetails details = connectionDetails.getIfAvailable();
        if (details != null) {
            config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, details.getProducer().getBootstrapServers());
            if (details.getProducer().getSecurityProtocol() != null) {
                config.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, details.getProducer().getSecurityProtocol());
            }
        }
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lifeos.messaging.outbox", name = "enabled", matchIfMissing = true)
    public OutboxRelay outboxRelay(DataSource dataSource, PlatformTransactionManager transactionManager,
                                   DefaultKafkaProducerFactory<String, String> lifeOsOutboxProducerFactory,
                                   EventEnvelopeCodec codec, JsonMapper jsonMapper, Clock clock,
                                   LifeOsMessagingProperties properties) {
        LifeOsMessagingProperties.Outbox outbox = properties.outbox();
        if (outbox.topic() == null || outbox.topic().isBlank()) {
            throw new IllegalStateException("lifeos.messaging.outbox.topic is required when the outbox is enabled "
                    + "(e.g. lifeos.strategy.events.v1); set lifeos.messaging.outbox.enabled=false if the "
                    + "service publishes no events");
        }
        return new OutboxRelay(JdbcClient.create(dataSource), new TransactionTemplate(transactionManager),
                new KafkaTemplate<>(lifeOsOutboxProducerFactory), codec, jsonMapper, clock, outbox.topic(),
                outbox.batchSize(), outbox.sendTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessagingScheduler lifeOsMessagingScheduler(ObjectProvider<OutboxRelay> relay, Inbox inbox,
                                                       LifeOsMessagingProperties properties) {
        return new MessagingScheduler(relay.getIfAvailable(), inbox, properties);
    }
}
