package edu.stanford.protege.github.cloneservice.message;

import edu.stanford.protege.webprotege.common.ProjectEvent;
import javax.annotation.Nonnull;

/**
 * Base interface for all events related to creating project history from a GitHub repository.
 */
public interface CreateProjectHistoryEvent extends ProjectEvent {

    /**
     * Returns the unique operation ID associated with this GitHub repository clone operation.
     *
     * <p>This ID is generated when the operation starts and is used to track the progress
     * and completion of the repository cloning and history extraction process.</p>
     *
     * @return the operation ID for this GitHub repository clone operation, never null
     */
    @Nonnull
    CreateProjectHistoryOperationId operationId();
}
