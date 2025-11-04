package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.webprotege.change.OntologyChange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class OntologyChangesCollectingHandler implements OntologyChangesHandler {

    private final List<OntologyCommitChange> changes = new ArrayList<>();

    public OntologyChangesCollectingHandler() {
    }

    public List<OntologyCommitChange> getChanges() {
        return changes;
    }

    @Override
    public void handleOntologyChanges(CommitMetadata baselineCommit, Optional<CommitMetadata> ancestorCommit, List<OntologyChange> ontologyChanges) {
        var wrapper = new OntologyCommitChange(ontologyChanges, baselineCommit, ancestorCommit);
        changes.add(wrapper);
    }
}
