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
import org.axonframework.common.transaction.NoTransactionManager;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.jobrunr.DeadlineDetails;
import org.axonframework.deadline.jobrunr.JobRunrDeadlineManager;
import org.axonframework.deadline.quartz.QuartzDeadlineManager;
import org.axonframework.messaging.ScopeAwareProvider;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.tracing.NoOpSpanFactory;
import org.axonframework.tracing.Span;
import org.axonframework.tracing.SpanFactory;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobProId;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.deadline.jobrunr.LabelUtils.getCombinedLabel;
import static org.axonframework.deadline.jobrunr.LabelUtils.getLabel;
import static org.axonframework.extensions.jobrunrpro.util.JobRunrProUtils.deleteAllPendingJobsByLabel;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Implementation of {@link DeadlineManager} that delegates scheduling and triggering to a JobRunrPro
 * {@link JobScheduler}. It also uses the {@link StorageProvider} to implement the {@link #cancelAll(String)} and
 * {@link #cancelAllWithinScope(String, ScopeDescriptor)} methods.
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public class JobRunrProDeadlineManager extends JobRunrDeadlineManager {

    private static final Logger logger = getLogger(JobRunrProDeadlineManager.class);
    protected final JobScheduler jobScheduler;
    private final StorageProvider storageProvider;
    private final Serializer serializer;
    private final SpanFactory spanFactory;

    /**
     * Instantiate a Builder to be able to create a {@link JobRunrProDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager}.
     * <p>
     * The {@link SpanFactory} is defaulted to a {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link StorageProvider}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard
     * requirements</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link JobRunrProDeadlineManager}
     */
    public static Builder proBuilder() {
        return new Builder();
    }

    /**
     * Instantiate a {@link JobRunrProDeadlineManager} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link ScopeAwareProvider}, {@link JobScheduler} and {@link Serializer} are not
     * {@code null}, and will throw an {@link AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link QuartzDeadlineManager.Builder} used to instantiate a {@link QuartzDeadlineManager}
     *                instance
     */
    protected JobRunrProDeadlineManager(Builder builder) {
        super(builder.asNonProBuilder());
        builder.validate();
        this.jobScheduler = builder.jobScheduler;
        this.storageProvider = builder.storageProvider;
        this.serializer = builder.serializer;
        this.spanFactory = builder.spanFactory;
    }

    @Override
    protected void createJob(JobBuilder job) {
        JobProId id = jobScheduler.create(job);
        logger.debug("Job with id: [{}] was successfully created.", id);
    }

    @Override
    protected String getSpanClassName() {
        return "JobRunrProDeadlineManager";
    }

    @Override
    public void cancelAll(@Nonnull String deadlineName) {
        Span span = spanFactory.createInternalSpan(() -> getSpanClassName() + ".cancelAll(" + deadlineName + ")");
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> deleteAll(getLabel(deadlineName))));
    }

    @Override
    public void cancelAllWithinScope(@Nonnull String deadlineName, @Nonnull ScopeDescriptor scope) {
        Span span = spanFactory.createInternalSpan(
                () -> getSpanClassName() + ".cancelAllWithinScope(" + deadlineName + ")");
        runOnPrepareCommitOrNow(span.wrapRunnable(() -> deleteAll(getCombinedLabel(serializer, deadlineName, scope))));
    }

    private void deleteAll(String label) {
        deleteAllPendingJobsByLabel(jobScheduler, storageProvider, label, DELETE_REASON);
    }

    /**
     * Builder class to instantiate a {@link JobRunrProDeadlineManager}.
     * <p>
     * The {@link TransactionManager} is defaulted to a {@link NoTransactionManager} and the {@link SpanFactory}
     * defaults to a {@link NoOpSpanFactory}.
     * <p>
     * The {@link JobScheduler}, {@link StorageProvider}, {@link ScopeAwareProvider} and {@link Serializer} are <b>hard
     * requirements</b> and as such should be provided.
     */
    public static class Builder {

        private JobScheduler jobScheduler;
        private StorageProvider storageProvider;
        private ScopeAwareProvider scopeAwareProvider;
        private Serializer serializer;
        private TransactionManager transactionManager = NoTransactionManager.INSTANCE;
        private SpanFactory spanFactory = NoOpSpanFactory.INSTANCE;

        /**
         * Sets the {@link JobScheduler} used for scheduling and triggering purposes of the deadlines.
         *
         * @param jobScheduler a {@link JobScheduler} used for scheduling and triggering purposes of the deadlines
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
         * Sets the {@link ScopeAwareProvider} which is capable of providing a stream of
         * {@link org.axonframework.messaging.Scope} instances for a given {@link ScopeDescriptor}. Used to return the
         * right Scope to trigger a deadline in.
         *
         * @param scopeAwareProvider a {@link ScopeAwareProvider} used to find the right
         *                           {@link org.axonframework.messaging.Scope} to trigger a deadline in
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder scopeAwareProvider(ScopeAwareProvider scopeAwareProvider) {
            assertNonNull(scopeAwareProvider, "ScopeAwareProvider may not be null");
            this.scopeAwareProvider = scopeAwareProvider;
            return this;
        }

        /**
         * Sets the {@link Serializer} used to de-/serialize the {@code payload},
         * {@link org.axonframework.messaging.MetaData} and the {@link ScopeDescriptor} into the {@link DeadlineDetails}
         * as well as the whole {@link DeadlineDetails} itself.
         *
         * @param serializer a {@link Serializer} used to de-/serialize the {@code payload},
         *                   {@link org.axonframework.messaging.MetaData} and the {@link ScopeDescriptor} into the
         *                   {@link DeadlineDetails}, as well as the whole {@link DeadlineDetails} itself.
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Sets the {@link TransactionManager} used to build transactions and ties them to deadline. Defaults to a
         * {@link NoTransactionManager}.
         *
         * @param transactionManager a {@link TransactionManager} used to build transactions and ties them to deadline
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder transactionManager(TransactionManager transactionManager) {
            assertNonNull(transactionManager, "TransactionManager may not be null");
            this.transactionManager = transactionManager;
            return this;
        }

        /**
         * Sets the {@link SpanFactory} implementation to use for providing tracing capabilities. Defaults to a
         * {@link NoOpSpanFactory} by default, which provides no tracing capabilities.
         *
         * @param spanFactory The {@link SpanFactory} implementation
         * @return The current Builder instance, for fluent interfacing.
         */
        public Builder spanFactory(@Nonnull SpanFactory spanFactory) {
            assertNonNull(spanFactory, "SpanFactory may not be null");
            this.spanFactory = spanFactory;
            return this;
        }

        /**
         * Creates a non-pro builder to easily call the constructor of {@link JobRunrDeadlineManager}.
         *
         * @return a {@link JobRunrDeadlineManager.Builder} instance using the set properties.
         */
        public JobRunrDeadlineManager.Builder asNonProBuilder() {
            return JobRunrDeadlineManager.builder()
                                         .jobScheduler(jobScheduler)
                                         .scopeAwareProvider(scopeAwareProvider)
                                         .serializer(serializer)
                                         .transactionManager(transactionManager)
                                         .spanFactory(spanFactory);
        }

        /**
         * Initializes a {@link JobRunrProDeadlineManager} as specified through this Builder.
         *
         * @return a {@link JobRunrProDeadlineManager} as specified through this Builder
         */
        public JobRunrProDeadlineManager build() {
            return new JobRunrProDeadlineManager(this);
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() throws AxonConfigurationException {
            assertNonNull(scopeAwareProvider, "The ScopeAwareProvider is a hard requirement and should be provided.");
            assertNonNull(storageProvider, "The StorageProvider is a hard requirement and should be provided.");
            assertNonNull(jobScheduler, "The JobScheduler is a hard requirement and should be provided.");
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided.");
        }
    }
}
