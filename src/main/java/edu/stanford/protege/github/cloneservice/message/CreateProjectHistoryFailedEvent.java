package edu.stanford.protege.github.cloneservice.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectId;

/**
 * Event emitted when a GitHub repository clone operation fails.
 *
 * @param eventId      The correlation event ID for tracking the operation
 * @param operationId  The correlated operation ID for tracking the operation
 * @param projectId    The project for which the repository clone failed
 * @param errorMessage The error message describing the failure
 */
public record CreateProjectHistoryFailedEvent(
        @JsonProperty("eventId") EventId eventId,
        @JsonProperty("operationId") CreateProjectHistoryOperationId operationId,
        @JsonProperty("projectId") ProjectId projectId,
        @JsonProperty("errorMessage") String errorMessage)
        implements CreateProjectHistoryCompletionEvent {

    private static final String CHANNEL = "webprotege.events.github.CreateProjectHistoryFailed";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
