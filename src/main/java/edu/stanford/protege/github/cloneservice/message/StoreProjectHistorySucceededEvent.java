package edu.stanford.protege.github.cloneservice.message;

import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.webprotege.common.*;

/**
 * Event dispatched when project history is successfully imported from a GitHub repository.
 *
 * @param projectId The project for which the history was imported
 * @param operationId The correlated operation ID for tracking the operation
 * @param eventId The correlation event ID for tracking the operation
 * @param branchCoordinates The coordinates of the repository branch from which history was imported
 */
public record StoreProjectHistorySucceededEvent(
        ProjectId projectId,
        CreateProjectHistoryOperationId operationId,
        EventId eventId,
        BranchCoordinates branchCoordinates)
        implements ProjectEvent {

    private static final String CHANNEL = "webprotege.events.github.StoreProjectHistorySucceeded";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
