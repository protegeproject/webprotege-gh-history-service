package edu.stanford.protege.github.cloneservice.message;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectEvent;
import edu.stanford.protege.webprotege.common.ProjectId;

/**
 * Event dispatched when a GitHub repository is successfully cloned.
 *
 * @param projectId The project for which the repository was cloned
 * @param operationId The correlated operation ID for tracking the operation
 * @param eventId The correlation event ID for tracking the operation
 * @param branchCoordinates The coordinates of the successfully cloned repository branch
 */
public record CloneRepositorySucceededEvent(
        ProjectId projectId,
        CreateProjectHistoryOperationId operationId,
        EventId eventId,
        BranchCoordinates branchCoordinates)
        implements ProjectEvent {

    private static final String CHANNEL = "webprotege.events.github.CloneRepositorySucceeded";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
