package edu.stanford.protege.github.cloneservice.message;

import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectEvent;
import edu.stanford.protege.webprotege.common.ProjectId;

/**
 * Event dispatched when cloning a GitHub repository fails.
 *
 * @param projectId The project for which the repository clone failed
 * @param operationId The correlated operation ID for tracking the operation
 * @param eventId The correlated event ID for tracking the operation
 * @param branchCoordinates The coordinates of the repository branch that failed to clone
 * @param errorMessage The error message describing the failure
 */
public record CloneRepositoryFailedEvent(
        ProjectId projectId,
        CreateProjectHistoryOperationId operationId,
        EventId eventId,
        BranchCoordinates branchCoordinates,
        String errorMessage)
        implements ProjectEvent {

    private static final String CHANNEL = "webprotege.events.github.CloneRepositoryFailed";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
