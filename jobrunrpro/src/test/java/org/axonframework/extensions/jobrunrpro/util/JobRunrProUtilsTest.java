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

package org.axonframework.extensions.jobrunrpro.util;

import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.states.DeletedState;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class JobRunrProUtilsTest {

    private StorageProvider storageProvider;
    private JobScheduler scheduler;
    private BackgroundJobServer backgroundJobServer;
    private ActionHandler actionHandler;

    @BeforeEach
    void prepare() {
        actionHandler = new ActionHandler();
        storageProvider = new InMemoryStorageProvider();
        scheduler = new JobScheduler(storageProvider);
        JobRunrPro.configure()
                  .useJobActivator(new SimpleActivator<>(actionHandler))
                  .useStorageProvider(storageProvider)
                  .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5))
                  .initialize();
        backgroundJobServer = JobRunrPro.getBackgroundJobServer();
    }

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(backgroundJobServer)) {
            backgroundJobServer.stop();
            backgroundJobServer = null;
        }
    }

    @Test
    void shouldCallDeleteAndPreventExecution() {
        String label = "label";
        String reason = "reason";
        JobBuilder jobBuilder = JobBuilder.aJob()
                                          .withLabels(label)
                                          .withDetails(() -> actionHandler.add())
                                          .scheduleAt(Instant.MAX);
        JobId id = scheduler.create(jobBuilder);
        Job job = storageProvider.getJobById(id);
        assertNotNull(job);
        assertEquals(StateName.SCHEDULED, job.getState());
        JobRunrProUtils.deleteAllPendingJobsByLabel(scheduler, storageProvider, label, reason);
        job = storageProvider.getJobById(id);
        assertNotNull(job);
        assertEquals(StateName.DELETED, job.getState());
        assertEquals(reason, ((DeletedState) job.getJobStates().get(1)).getReason());
    }

    @Test
    void shouldNotCallDeleteIfJobIsDone() {
        String label = "label";
        String reason = "reason";
        JobBuilder jobBuilder = JobBuilder.aJob()
                                          .withLabels(label)
                                          .withDetails(() -> actionHandler.add())
                                          .scheduleAt(Instant.now());
        JobId id = scheduler.create(jobBuilder);
        await().atMost(Duration.ofSeconds(10L)).untilAsserted(() -> jobSucceeded(id));
        assertEquals(1, actionHandler.currentValue());
        JobRunrProUtils.deleteAllPendingJobsByLabel(scheduler, storageProvider, label, reason);
        Job job = storageProvider.getJobById(id);
        assertNotNull(job);
        assertEquals(StateName.SUCCEEDED, job.getState());
    }

    void jobSucceeded(JobId id) {
        Job job = storageProvider.getJobById(id);
        assertNotNull(job);
        assertEquals(StateName.SUCCEEDED, job.getState());
    }

    private static class ActionHandler {

        private final AtomicInteger counter = new AtomicInteger(0);

        void add() {
            counter.incrementAndGet();
        }

        int currentValue() {
            return counter.get();
        }
    }
}
