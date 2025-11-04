package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.webprotege.change.OntologyChange;

import java.util.ArrayList;
import java.util.List;

public class OntologyChangesCollectingHandler implements OntologyChangesHandler {

    private final List<OntologyChange> changes = new ArrayList<>();

    public OntologyChangesCollectingHandler() {
    }

    @Override
    public void handleOntologyChanges(CommitMetadata baselineCommit, CommitMetadata ancestorCommit, List<OntologyChange> ontologyChanges) {
        var wrapper = new OntologyCommitChange(changes, baselineCommit, ancestorCommit.commitHash());
    }
}
