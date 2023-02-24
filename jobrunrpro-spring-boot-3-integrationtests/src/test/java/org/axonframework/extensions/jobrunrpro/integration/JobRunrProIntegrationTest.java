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
import org.axonframework.extensions.jobrunrpro.deadline.JobRunrProDeadlineManager;
import org.axonframework.messaging.ScopeDescriptor;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class JobRunrProIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:5");

    private ApplicationContextRunner testApplicationContext;

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
    }

    @Test
    void messageSendViaDeadlineManagerShouldBeCancelable() {
        testApplicationContext
                .run(context -> {
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof JobRunrProDeadlineManager);
                    StorageProvider storageProvider = context.getBean(StorageProvider.class);
                    assertNotNull(storageProvider);
                    setAndCancelDeadlineUsingCancelAllWithinScope(deadlineManager, storageProvider);
                });
    }

    @Test
    void allDeadlinesWithTheSameNameShouldBeCancelable() {
        testApplicationContext
                .run(context -> {
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof JobRunrProDeadlineManager);
                    StorageProvider storageProvider = context.getBean(StorageProvider.class);
                    assertNotNull(storageProvider);
                    setAndCancelDeadlinesUsingCancelAll(deadlineManager, storageProvider);
                });
    }

    private void setAndCancelDeadlineUsingCancelAllWithinScope(
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

    private void setAndCancelDeadlinesUsingCancelAll(
            DeadlineManager deadlineManager,
            StorageProvider storageProvider
    ) {
        String firstJobId = deadlineManager.schedule(
                Instant.now().plusSeconds(100L),
                "some-deadline",
                "payload",
                new TestScopeDescriptor("aggregate-type1", "aggregate-identifier1"));
        String secondJobId = deadlineManager.schedule(
                Instant.now().plusSeconds(100L),
                "some-deadline",
                "payload",
                new TestScopeDescriptor("aggregate-type2", "aggregate-identifier2"));
        List<Job> jobs = List.of(storageProvider.getJobById(UUID.fromString(firstJobId)),
                                 storageProvider.getJobById(UUID.fromString(secondJobId)));
        jobs.forEach(Assertions::assertNotNull);
        jobs.forEach(j -> assertEquals(StateName.SCHEDULED, j.getState()));
        deadlineManager.cancelAll("some-deadline");
        jobs = List.of(storageProvider.getJobById(UUID.fromString(firstJobId)),
                       storageProvider.getJobById(UUID.fromString(secondJobId)));
        jobs.forEach(Assertions::assertNotNull);
        jobs.forEach(j -> assertEquals(StateName.DELETED, j.getState()));
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    public static class DefaultContext {

    }
}
