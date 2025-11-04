package edu.stanford.protege.github.cloneservice.model;

import com.google.common.collect.ImmutableList;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.webprotege.change.OntologyChange;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Represents a difference between two ontology versions containing a set of axiom changes and
 * commit metadata
 */
public record OntologyCommitChange(
        @Nonnull List<OntologyChange> axiomChanges,
        @Nonnull CommitMetadata baselineCommit,
        @Nonnull String ancestorCommitId) {

    public OntologyCommitChange {
        Objects.requireNonNull(axiomChanges, "axiomChanges cannot be null");
        Objects.requireNonNull(baselineCommit, "baselineCommit cannot be null");
        // Create defensive copy to prevent external mutation
        axiomChanges = ImmutableList.copyOf(axiomChanges);
    }
}
