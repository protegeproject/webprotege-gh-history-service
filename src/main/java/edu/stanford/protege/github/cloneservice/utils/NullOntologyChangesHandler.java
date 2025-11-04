package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.webprotege.change.OntologyChange;

import java.util.List;
import java.util.Optional;

public class NullOntologyChangesHandler implements OntologyChangesHandler {

    @Override
    public void handleOntologyChanges(CommitMetadata baselineCommit, Optional<CommitMetadata> ancestorCommit, List<OntologyChange> ontologyChanges) {

    }
}
