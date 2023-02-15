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

package org.axonframework.extensions.jobrunrpro.deadline;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.TestSerializer;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.JobSearchRequest;
import org.jobrunr.storage.Page;
import org.jobrunr.storage.PageRequest;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRunrProDeadlineManagerBuilderTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private JobRunrProDeadlineManager.Builder builder;
    private final JobScheduler jobScheduler = mock(JobScheduler.class);

    private final StorageProvider storageProvider = mock(StorageProvider.class);
    private final TransactionManager transactionManager = mock(TransactionManager.class);
    private final ScopeAwareProvider scopeAwareProvider = mock(ScopeAwareProvider.class);

    private final Page<?> page = mock(Page.class);

    @BeforeEach
    void newBuilder() {
        builder = JobRunrProDeadlineManager.proBuilder();
        doReturn(page).when(storageProvider).getJobs(any(JobSearchRequest.class), any(PageRequest.class));
    }

    @Test
    void whenAllPropertiesAreSetCreatesManager() {
        JobRunrProDeadlineManager manager =
                builder.scopeAwareProvider(scopeAwareProvider)
                       .transactionManager(transactionManager)
                       .jobScheduler(jobScheduler)
                       .storageProvider(storageProvider)
                       .serializer(TestSerializer.JACKSON.getSerializer())
                       .build();

        assertNotNull(manager);
    }

    @Test
    void validateNeedsAllPropertiesSet() {
        builder.jobScheduler(jobScheduler)
               .transactionManager(transactionManager);
        assertThrows(AxonConfigurationException.class, () -> builder.build());
    }

    @Test
    void whenSettingSchedulerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.jobScheduler(null));
    }

    @Test
    void whenSettingstorageProviderWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.storageProvider(null));
    }

    @Test
    void whenSettingTransactionManagerWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.transactionManager(null));
    }

    @Test
    void whenSettingScopeAwareProviderWithNullThrowError() {
        assertThrows(AxonConfigurationException.class, () -> builder.scopeAwareProvider(null));
    }

    @Test
    void cancelImplemented() {
        JobRunrProDeadlineManager manager =
                builder.scopeAwareProvider(scopeAwareProvider)
                       .transactionManager(transactionManager)
                       .jobScheduler(jobScheduler)
                       .storageProvider(storageProvider)
                       .serializer(TestSerializer.JACKSON.getSerializer())
                       .build();
        assertDoesNotThrow(() -> manager.cancelAll(TEST_DEADLINE_NAME));
    }

    @Test
    void cancelAllWithinScopeImplemented() {
        JobRunrProDeadlineManager manager =
                builder.scopeAwareProvider(scopeAwareProvider)
                       .transactionManager(transactionManager)
                       .jobScheduler(jobScheduler)
                       .storageProvider(storageProvider)
                       .serializer(TestSerializer.JACKSON.getSerializer())
                       .build();
        ScopeDescriptor descriptor = new TestScopeDescriptor("aggregate-type", "aggregate-identifier");
        assertDoesNotThrow(() -> manager.cancelAllWithinScope(TEST_DEADLINE_NAME, descriptor));
    }
}
