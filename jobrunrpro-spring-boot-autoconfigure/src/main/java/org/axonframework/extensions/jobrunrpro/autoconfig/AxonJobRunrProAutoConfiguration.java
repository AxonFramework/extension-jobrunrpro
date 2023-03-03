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

import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.extensions.jobrunrpro.deadline.JobRunrProDeadlineManager;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.serialization.Serializer;
import org.axonframework.springboot.autoconfig.AxonJobRunrAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.axonframework.springboot.autoconfig.AxonTracingAutoConfiguration;
import org.axonframework.tracing.SpanFactory;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto configuration class for the deadline manager using JobRunr Pro.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
@AutoConfiguration
@ConditionalOnBean(JobScheduler.class)
@AutoConfigureBefore(AxonJobRunrAutoConfiguration.class)
@AutoConfigureAfter(value = {AxonServerAutoConfiguration.class, AxonTracingAutoConfiguration.class},
        name = {"org.jobrunr.spring.autoconfigure.JobRunrAutoConfiguration"})
public class AxonJobRunrProAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DeadlineManager deadlineManager(
            JobScheduler jobScheduler,
            StorageProvider storageProvider,
            Configuration configuration,
            @Qualifier("eventSerializer") Serializer serializer,
            TransactionManager transactionManager,
            SpanFactory spanFactory) {
        ScopeAwareProvider scopeAwareProvider = new ConfigurationScopeAwareProvider(configuration);
        return JobRunrProDeadlineManager.proBuilder()
                                        .jobScheduler(jobScheduler)
                                        .storageProvider(storageProvider)
                                        .scopeAwareProvider(scopeAwareProvider)
                                        .serializer(serializer)
                                        .transactionManager(transactionManager)
                                        .spanFactory(spanFactory)
                                        .build();
    }
}
