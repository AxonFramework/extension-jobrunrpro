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

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.JobSearchRequest;
import org.jobrunr.storage.StorageProvider;

import java.util.List;

import static org.jobrunr.storage.JobSearchRequestBuilder.aJobSearchRequest;
import static org.jobrunr.storage.PageRequest.ascOnUpdatedAt;

/**
 * Utility methods specifically for JobRunr Pro
 *
 * @author Gerard Klijs
 * @since 4.8.0
 */
public abstract class JobRunrProUtils {

    /**
     * Will delete the pending jobs, in batches of 1000, with a filter on the given label.
     *
     * @param jobScheduler    a {@link JobScheduler} instance
     * @param storageProvider a {@link StorageProvider} instance
     * @param label           the {@link String} with the label that should be used to filter on
     * @param deleteReason    a {@link String} which adds context as to why the job was deleted
     */
    public static void deleteAllPendingJobsByLabel(
            JobScheduler jobScheduler,
            StorageProvider storageProvider,
            String label,
            String deleteReason
    ) {
        JobSearchRequest request = aJobSearchRequest()
                .withLabel(label)
                .withStateName(StateName.SCHEDULED)
                .build();
        List<Job> jobs = getFirstThousandJobs(storageProvider, request);
        while (!jobs.isEmpty()) {
            jobs.forEach(job -> jobScheduler.delete(job.getId(), deleteReason));
            jobs = getFirstThousandJobs(storageProvider, request);
        }
    }

    private static List<Job> getFirstThousandJobs(StorageProvider storageProvider, JobSearchRequest request) {
        return storageProvider.getJobs(request, ascOnUpdatedAt(1000)).getItems();
    }

    private JobRunrProUtils() {
        //prevent instantiation
    }
}
