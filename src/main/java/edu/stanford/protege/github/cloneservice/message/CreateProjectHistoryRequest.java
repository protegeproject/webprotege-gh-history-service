package edu.stanford.protege.github.cloneservice.message;

import static edu.stanford.protege.github.cloneservice.message.CreateProjectHistoryRequest.CHANNEL;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.webprotege.common.ProjectId;
import edu.stanford.protege.webprotege.common.Request;
import java.util.Objects;

@JsonTypeName(CHANNEL)
public record CreateProjectHistoryRequest(
        @JsonProperty("projectId") ProjectId projectId,
        @JsonProperty("branchCoordinates") BranchCoordinates branchCoordinates,
        @JsonProperty("rootOntologyPath") RelativeFilePath rootOntologyPath)
        implements Request<CreateProjectHistoryResponse> {

    public static final String CHANNEL = "webprotege.github.CreateProjectHistory";

    public CreateProjectHistoryRequest {
        Objects.requireNonNull(projectId, "Project id cannot be null");
        Objects.requireNonNull(branchCoordinates, "Branch coordinates cannot be null");
        Objects.requireNonNull(rootOntologyPath, "Root ontology path cannot be null");
    }

    @Override
    public String getChannel() {
        return CHANNEL;
    }
}
