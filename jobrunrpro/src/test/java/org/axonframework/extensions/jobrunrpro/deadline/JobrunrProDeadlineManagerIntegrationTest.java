/*
 * Copyright (c) 2010-2024. Axon Framework
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

package org.axonframework.extensions.jobrunrpro.deadline;

import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.extensions.jobrunrpro.util.SimpleActivator;
import org.axonframework.integrationtests.deadline.AbstractDeadlineManagerTestSuite;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.serialization.TestSerializer;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.InMemoryStorageProvider;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;

import java.time.Duration;
import java.util.Objects;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobrunrProDeadlineManagerIntegrationTest extends AbstractDeadlineManagerTestSuite {

    private BackgroundJobServer backgroundJobServer;

    @AfterEach
    void cleanUp() {
        if (!Objects.isNull(backgroundJobServer)) {
            backgroundJobServer.stop();
            backgroundJobServer = null;
        }
    }

    @Override
    public DeadlineManager buildDeadlineManager(Configuration configuration) {
        StorageProvider storageProvider = new InMemoryStorageProvider();
        JobScheduler scheduler = new JobScheduler(storageProvider);
        JobRunrProDeadlineManager manager = JobRunrProDeadlineManager
                .proBuilder()
                .jobScheduler(scheduler)
                .storageProvider(storageProvider)
                .scopeAwareProvider(new ConfigurationScopeAwareProvider(configuration))
                .serializer(TestSerializer.JACKSON.getSerializer())
                .transactionManager(NoTransactionManager.INSTANCE)
                .spanFactory(configuration.spanFactory())
                .build();
        JobRunrPro.configure()
                  .useJobActivator(new SimpleActivator<>(spy(manager)))
                  .useStorageProvider(storageProvider)
                  .useBackgroundJobServer(
                          usingStandardBackgroundJobServerConfiguration().andPollInterval(Duration.ofMillis(200))
                  )
                  .initialize();
        backgroundJobServer = JobRunrPro.getBackgroundJobServer();
        return manager;
    }

    @Test
    void shutdownInvokesSchedulerShutdown(@Mock ScopeAwareProvider scopeAwareProvider) {
        StorageProvider storageProvider = new InMemoryStorageProvider();
        JobScheduler scheduler = spy(new JobScheduler(new InMemoryStorageProvider()));
        JobRunrProDeadlineManager testSubject =
                JobRunrProDeadlineManager.proBuilder()
                                         .jobScheduler(scheduler)
                                         .scopeAwareProvider(scopeAwareProvider)
                                         .storageProvider(storageProvider)
                                         .serializer(TestSerializer.JACKSON.getSerializer())
                                         .transactionManager(NoTransactionManager.INSTANCE)
                                         .build();

        testSubject.shutdown();
        verify(scheduler).shutdown();
    }
}
