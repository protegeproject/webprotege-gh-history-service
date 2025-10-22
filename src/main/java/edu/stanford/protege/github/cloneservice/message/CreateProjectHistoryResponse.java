package edu.stanford.protege.github.cloneservice.message;

import static edu.stanford.protege.github.cloneservice.message.CreateProjectHistoryRequest.CHANNEL;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.webprotege.common.ProjectId;
import edu.stanford.protege.webprotege.common.Response;

@JsonTypeName(CHANNEL)
public record CreateProjectHistoryResponse(
        @JsonProperty("projectId") ProjectId projectId,
        @JsonProperty("operationId") CreateProjectHistoryOperationId operationId,
        @JsonProperty("branchCoordinates") BranchCoordinates branchCoordinates)
        implements Response {}
