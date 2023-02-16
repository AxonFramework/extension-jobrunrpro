/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.jobrunrpro.integration;

import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.deadline.jobrunr.JobRunrProDeadlineManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.extensions.jobrunrpro.scheduling.JobRunrProEventScheduler;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ScopeDescriptor;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class JobRunrProIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:5");

    private ApplicationContextRunner testApplicationContext;
    private List<Message<?>> publishedMessages;

    @BeforeEach
    void setUp() {
        testApplicationContext = new ApplicationContextRunner()
                .withPropertyValues(
                        "axon.axonserver.enabled=false",
                        "spring.data.mongodb.uri=mongodb://" + MONGO_CONTAINER.getHost() + ':'
                                + MONGO_CONTAINER.getFirstMappedPort() + "/test",
                        "org.jobrunr.background-job-scheduler.enabled=true",
                        "org.jobrunr.background-job-server.enabled=true",
                        "org.jobrunr.database.datasource=mongodb://" + MONGO_CONTAINER.getHost() + ':'
                                + MONGO_CONTAINER.getFirstMappedPort() + "/jobrunr",
                        "org.jobrunr.database.type=mongodb"
                )
                .withUserConfiguration(DefaultContext.class);
        publishedMessages = new CopyOnWriteArrayList<>();
    }

    @Test
    void messageSendViaDeadlineManagerShouldBeCancelable() {
        testApplicationContext
                .run(context -> {
                    context.getBean(JobScheduler.class);
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof JobRunrProDeadlineManager);
                    StorageProvider storageProvider = context.getBean(StorageProvider.class);
                    assertNotNull(storageProvider);
                    setAndCancelDeadline(deadlineManager, storageProvider);
                });
    }

    private void setAndCancelDeadline(
            DeadlineManager deadlineManager,
            StorageProvider storageProvider
    ) {
        ScopeDescriptor descriptor = new TestScopeDescriptor("aggregate-type", "aggregate-identifier");
        String id = deadlineManager.schedule(
                Instant.now().plusSeconds(100L),
                "some-deadline",
                "payload",
                descriptor);
        Job job = storageProvider.getJobById(UUID.fromString(id));
        assertNotNull(job);
        assertEquals(StateName.SCHEDULED, job.getState());
        deadlineManager.cancelAllWithinScope("some-deadline", descriptor);
        job = storageProvider.getJobById(UUID.fromString(id));
        assertNotNull(job);
        assertEquals(StateName.DELETED, job.getState());
    }

    @Test
    void messageSendViaEventSchedulerShouldBeReceived() {
        testApplicationContext
                .run(context -> {
                    EventBus eventBus = context.getBean(EventBus.class);
                    eventBus.subscribe(l -> publishedMessages.addAll(l));
                    context.getBean(JobScheduler.class);
                    EventScheduler eventScheduler = context.getBean(EventScheduler.class);
                    assertNotNull(eventScheduler);
                    assertTrue(eventScheduler instanceof JobRunrProEventScheduler);
                    eventScheduler.schedule(Instant.now(), "payload");
                    receiveMessage();
                });
    }

    private void receiveMessage() {
        await().atMost(Duration.ofSeconds(5L)).until(() -> !publishedMessages.isEmpty());
        Message<?> message = publishedMessages.get(0);
        assertNotNull(message);
        assertEquals("payload", message.getPayload());
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class DefaultContext {

    }
}
