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

package org.axonframework.extensions.jobrunrpro.autoconfig;

import org.axonframework.deadline.DeadlineManager;
import org.axonframework.extensions.jobrunrpro.deadline.JobRunrProDeadlineManager;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableMBeanExport;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AxonJobRunrProAutoConfigurationTest {

    @Test
    void jobRunrDeadlineManagerIsCreated() {
        new ApplicationContextRunner()
                .withPropertyValues("axon.axonserver.enabled=false")
                .withUserConfiguration(DefaultContext.class)
                .run(context -> {
                    DeadlineManager deadlineManager = context.getBean(DeadlineManager.class);
                    assertNotNull(deadlineManager);
                    assertTrue(deadlineManager instanceof JobRunrProDeadlineManager);
                });
    }

    @ContextConfiguration
    @EnableAutoConfiguration
    @EnableMBeanExport(registration = RegistrationPolicy.IGNORE_EXISTING)
    private static class DefaultContext {

        @Bean
        public JobScheduler jobScheduler() {
            return mock(JobScheduler.class);
        }

        @Bean
        public StorageProvider storageProvider() {
            return mock(StorageProvider.class);
        }
    }
}
