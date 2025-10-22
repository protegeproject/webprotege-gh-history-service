package edu.stanford.protege.github.cloneservice.message;

import static edu.stanford.protege.github.cloneservice.message.CreateProjectHistorySucceededEvent.CHANNEL;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.stanford.protege.webprotege.common.BlobLocation;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectId;

/**
 * Event emitted when a GitHub repository clone operation completes successfully.
 *
 * @param eventId The correlation event ID for tracking the operation
 * @param operationId The correlated operation ID for tracking the operation
 * @param projectId The project for which the repository clone failed
 * @param documentLocation The location where the project history is stored
 */
@JsonTypeName(CHANNEL)
public record CreateProjectHistorySucceededEvent(
        @JsonProperty("eventId") EventId eventId,
        @JsonProperty("operationId") CreateProjectHistoryOperationId operationId,
        @JsonProperty("projectId") ProjectId projectId,
        @JsonProperty("documentLocation") BlobLocation documentLocation)
        implements CreateProjectHistoryCompletionEvent {

    public static final String CHANNEL = "webprotege.events.github.CreateProjectHistorySucceeded";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
