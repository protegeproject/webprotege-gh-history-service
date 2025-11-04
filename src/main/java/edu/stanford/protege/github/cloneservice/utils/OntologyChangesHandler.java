package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.webprotege.change.OntologyChange;

import java.util.List;

public interface OntologyChangesHandler {

    /**
     * Called when the changes for the specified baseline commit have been computed with respect
     * the the specified ancestor commit.
     * @param baselineCommit The baseline commit for which the ontology changes are applicable
     * @param ancestorCommit The ancestor commit that was used to produce the diff and the ontology
     *                       changes.
     * @param ontologyChanges A list of ontology changes that represent the changes to the ontologies
     *                        between the ancestor commit and the basline commit.
     */
    void handleOntologyChanges(CommitMetadata baselineCommit,
                               CommitMetadata ancestorCommit,
                               List<OntologyChange> ontologyChanges);
}
