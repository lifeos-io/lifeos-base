package com.lifeos.base.messaging;

import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.base.messaging.outbox.OutboxPublishException;
import com.lifeos.base.messaging.outbox.OutboxRelay;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Runs the outbox relay and the hourly clean-up on a single background thread of its own, so the service does
 * not need {@code @EnableScheduling}. Starts after the context is ready and stops before the DataSource and
 * the Kafka producer are closed.
 */
public class MessagingScheduler implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MessagingScheduler.class);
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);

    private final OutboxRelay relay;
    private final Inbox inbox;
    private final LifeOsMessagingProperties properties;
    private ScheduledExecutorService executor;

    /** @param relay {@code null} when the outbox is disabled; the loop also needs {@code outbox.relay-enabled} */
    public MessagingScheduler(OutboxRelay relay, Inbox inbox, LifeOsMessagingProperties properties) {
        this.relay = relay;
        this.inbox = inbox;
        this.properties = properties;
    }

    @Override
    public synchronized void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
                .name("lifeos-messaging")
                .daemon(true)
                .factory());
        if (relay != null && properties.outbox().relayEnabled()) {
            executor.schedule(this::relayLoop, 0, TimeUnit.MILLISECONDS);
        }
        executor.scheduleWithFixedDelay(this::cleanup, CLEANUP_INTERVAL.toMillis(), CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void relayLoop() {
        Duration next = properties.outbox().pollInterval();
        try {
            int published = relay.relayOnce();
            if (published == properties.outbox().batchSize()) {
                next = Duration.ZERO; // a full batch: more are probably waiting
            }
        } catch (OutboxPublishException e) {
            next = properties.outbox().failureBackoff();
        } catch (RuntimeException e) {
            log.error("Outbox relay run failed", e);
            next = properties.outbox().failureBackoff();
        }
        ScheduledExecutorService current = executor;
        if (current != null && !current.isShutdown()) {
            current.schedule(this::relayLoop, next.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void cleanup() {
        try {
            if (relay != null) {
                relay.purgePublishedBefore(properties.outbox().retention());
            }
            inbox.purgeProcessedBefore(properties.inbox().retention());
        } catch (RuntimeException e) {
            log.warn("Messaging clean-up failed; it will run again in {}", CLEANUP_INTERVAL, e);
        }
    }

    @Override
    public synchronized void stop() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(properties.outbox().sendTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return executor != null;
    }
}
