package edu.stanford.protege.github.cloneservice.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.stanford.protege.webprotege.common.Event;
import edu.stanford.protege.webprotege.common.EventId;
import edu.stanford.protege.webprotege.common.ProjectId;

import static edu.stanford.protege.github.cloneservice.message.CommitProcessingStartedEvent.CHANNEL;

@JsonTypeName(CHANNEL)
public record CommitProcessingStartedEvent(@JsonProperty("eventId") EventId eventId,
                                           @JsonProperty("operationId") CreateProjectHistoryOperationId opId,
                                           @JsonProperty("projectId") ProjectId projectId,
                                           @JsonProperty("commitHash") String commitHash) implements Event {

    public static final String CHANNEL = "webprotege.events.github.CommitProcessingStarted";

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
