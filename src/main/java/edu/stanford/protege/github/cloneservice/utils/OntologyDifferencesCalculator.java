package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.stanford.protege.github.cloneservice.model.AxiomChange;
import java.util.*;
import javax.annotation.Nonnull;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Calculates differences between ontology versions */
@Component
public class OntologyDifferencesCalculator {

    private static final Logger logger = LoggerFactory.getLogger(OntologyDifferencesCalculator.class);

    /**
     * Calculates differences between baseline and ancestor commit ontologies
     *
     * @param childCommitOntology The ontology from a baseline commit
     * @param parentCommitOntology The ontology from a ancestor commit
     * @return OntologyDifference containing all changes for this commit
     */
    @Nonnull
    public List<AxiomChange> calculateAxiomChanges(
            @Nonnull OWLOntology childCommitOntology,
            @Nonnull OWLOntology parentCommitOntology,
            @Nonnull OWLOntologyID ontologyId) {

        Objects.requireNonNull(childCommitOntology, "childCommitOntology cannot be null");
        Objects.requireNonNull(parentCommitOntology, "parentCommitOntology cannot be null");

        var axiomChanges = Lists.<AxiomChange>newArrayList();

        var childCommitAxioms = Sets.newHashSet(childCommitOntology.getAxioms());
        var parentCommitAxioms = Sets.newHashSet(parentCommitOntology.getAxioms());

        // Find added axioms (present in current but not in previous)
        var addedAxioms = findAddedAxioms(childCommitAxioms, parentCommitAxioms);
        addedAxioms.forEach(axiom -> axiomChanges.add(AxiomChange.addAxiom(axiom, ontologyId)));

        // Find removed axioms (present in previous but not in current)
        var removedAxioms = findRemovedAxioms(childCommitAxioms, parentCommitAxioms);
        removedAxioms.forEach(axiom -> axiomChanges.add(AxiomChange.removeAxiom(axiom, ontologyId)));

        if(!addedAxioms.isEmpty() || !removedAxioms.isEmpty()) {
            logger.debug(
                    "Found {} added axioms and {} removed axioms for ontology {}",
                    addedAxioms.size(),
                    removedAxioms.size(),
                    ontologyId);
        }

        return ImmutableList.copyOf(axiomChanges);
    }

    /** Finds axioms that were added (present in current but not in previous) */
    private Set<OWLAxiom> findAddedAxioms(Set<OWLAxiom> currentAxioms, Set<OWLAxiom> previousAxioms) {
        var addedAxioms = new HashSet<>(currentAxioms);
        addedAxioms.removeAll(previousAxioms);
        return addedAxioms;
    }

    /** Finds axioms that were removed (present in previous but not in current) */
    private Set<OWLAxiom> findRemovedAxioms(Set<OWLAxiom> currentAxioms, Set<OWLAxiom> previousAxioms) {
        var removedAxioms = new HashSet<>(previousAxioms);
        removedAxioms.removeAll(currentAxioms);
        return removedAxioms;
    }
}
