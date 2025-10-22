package edu.stanford.protege.github.cloneservice.message;

import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectEvent;
import edu.stanford.protege.webprotege.common.ProjectId;

/**
 * Event dispatched when importing project history from a GitHub repository fails.
 *
 * @param projectId The project for which the history import failed
 * @param operationId The correlated operation ID for tracking the operation
 * @param eventId The correlation event ID for tracking the operation
 * @param branchCoordinates The coordinates of the repository branch from which import failed
 * @param errorMessage The error message describing the failure
 */
public record GenerateProjectHistoryFailedEvent(
        ProjectId projectId,
        CreateProjectHistoryOperationId operationId,
        EventId eventId,
        BranchCoordinates branchCoordinates,
        String errorMessage)
        implements ProjectEvent {

    private static final String CHANNEL = "webprotege.events.github.GenerateProjectHistoryFailed";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
