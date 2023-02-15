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

package org.axonframework.extensions.jobrunrpro.scheduling;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.jobrunr.JobRunrEventScheduler;
import org.axonframework.extensions.jobrunrpro.deadline.JobRunrProDeadlineManager;
import org.axonframework.serialization.Serializer;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobProId;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.jobrunr.LabelUtils.getLabel;
import static org.axonframework.extensions.jobrunrpro.util.JobRunrProUtils.deleteAllPendingJobsByLabel;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * EventScheduler implementation that delegates scheduling and triggering to a JobRunr JobScheduler. In addition to the
 * {@link JobRunrEventScheduler} it's possible to cancel all jobs with a certain name.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class JobRunrProEventScheduler extends JobRunrEventScheduler {

    private static final Logger logger = getLogger(JobRunrProDeadlineManager.class);
    protected static final String DELETE_REASON = "Deleted via Axon JobRunrProEventScheduler API";
    protected final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final String jobName;

    /**
     * Instantiate a Builder to be able to create a {@link JobRunrProEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link JobScheduler}, {@link StorageProvider}, {@link Serializer} and {@link EventBus} are <b>hard
     * requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link JobRunrProDeadlineManager}
     */
    public static JobRunrProEventScheduler.Builder proBuilder() {
        return new JobRunrProEventScheduler.Builder();
    }

    /**
     * Instantiate a {@link JobRunrEventScheduler} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link JobScheduler}, {@link Serializer} and {@link EventBus} are not {@code null}, and will
     * throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link JobRunrEventScheduler} instance
     */
    protected JobRunrProEventScheduler(Builder builder) {
        super(builder.asNonProBuilder());
        this.jobScheduler = builder.jobScheduler;
        this.storageProvider = builder.storageProvider;
        this.jobName = builder.jobName;
    }

    /**
     * Cancels all the events scheduled with the current {@code jobname}. This method has no impact on events which have
     * already been triggered.
     */
    public void cancelAll() {
        deleteAllPendingJobsByLabel(jobScheduler, storageProvider, getLabel(jobName), DELETE_REASON);
    }

    /**
     * Cancels all the events scheduled with the given {@code formerJobName}. This method has no impact on events which
     * have already been triggered.
     *
     * @param formerJobName a {@link String} with a name used before
     */
    public void cancelAll(String formerJobName) {
        deleteAllPendingJobsByLabel(jobScheduler, storageProvider, getLabel(formerJobName), DELETE_REASON);
    }

    /**
     * Builder class to instantiate a {@link JobRunrProEventScheduler}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link JobScheduler}, {@link StorageProvider}, {@link Serializer} and {@link EventBus} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public static class Builder {

        private JobScheduler jobScheduler;
        private StorageProvider storageProvider;

        private String jobName = "AxonScheduledEvent";
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private EventBus eventBus;

        /**
         * Sets the {@link JobScheduler} used for scheduling and triggering purposes of the events.
         *
         * @param jobScheduler a {@link JobScheduler} used for scheduling and triggering purposes of the events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder jobScheduler(JobScheduler jobScheduler) {
            assertNonNull(jobScheduler, "JobScheduler may not be null");
            this.jobScheduler = jobScheduler;
            return this;
        }

        /**
         * Sets the {@link StorageProvider} used for the cancel all methods. Those will return an error when called
         * while the storage provider has not been set.
         *
         * @param storageProvider a {@link StorageProvider} used for scheduling and triggering purposes of the
         *                        deadlines
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder storageProvider(StorageProvider storageProvider) {
            assertNonNull(storageProvider, "StorageProvider may not be null");
            this.storageProvider = storageProvider;
            return this;
        }

        /**
         * Sets the {@link String} used for creating the scheduled jobs in JobRunr. Defaults to
         * {@code AxonScheduledEvent}.
         *
         * @param jobName a {@link JobScheduler} used for naming the job
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder jobName(String jobName) {
            assertNonNull(jobName, "JobName may not be null");
            this.jobName = jobName;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload} and the
         * {@link org.axonframework.messaging.MetaData}.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload}, and
         *                   {@link org.axonframework.messaging.MetaData}.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them on event publication. Defaults
         * to a {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them on event
         *                           publication
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link EventBus} used to publish events on once the schedule has been met.
         *
         * @param eventBus a {@link EventBus} used to publish events on once the schedule has been met
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder eventBus(EventBus eventBus) {
            assertNonNull(eventBus, "EventBus may not be null");
            this.eventBus = eventBus;
            return this;
        }

        /**
         * Creates a non-pro builder to easily call the constructor of {@link JobRunrEventScheduler}.
         *
         * @return a {@link JobRunrEventScheduler.Builder} instance using the set properties.
         */
        public JobRunrEventScheduler.Builder asNonProBuilder() {
            return JobRunrEventScheduler.builder()
                                        .jobScheduler(jobScheduler)
                                        .jobName(jobName)
                                        .serializer(serializer)
                                        .eventBus(eventBus)
                                        .transactionManager(transactionManager);
        }

        /**
         * Initializes a {@link JobRunrEventScheduler} as specified through this Builder.
         *
         * @return a {@link JobRunrEventScheduler} as specified through this Builder
         */
        public JobRunrProEventScheduler build() {
            return new JobRunrProEventScheduler(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided.");
            assertNonNull(storageProvider, "The StorageProvider is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
            assertNonNull(eventBus, "The EventBus is a hard requirement and should be provided.");
        }
    }
}
