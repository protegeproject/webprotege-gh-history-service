package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import edu.stanford.protege.webprotege.change.*;
import org.jetbrains.annotations.NotNull;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.*;

/** Calculates differences between ontology versions */
@Component
public class OntologyDifferencesCalculator {

    private static final Logger logger = LoggerFactory.getLogger(OntologyDifferencesCalculator.class);

    private static void calculateImportDeclarationChanges(@NotNull OWLOntology childCommitOntology, @NotNull OWLOntology parentCommitOntology, @NotNull OWLOntologyID ontologyId, ArrayList<OntologyChange> ontologyChanges) {
        var parentImports = parentCommitOntology.getImportsDeclarations();
        var childImports = childCommitOntology.getImportsDeclarations();

        parentImports
                .forEach(parentImport -> {
                    if(!childImports.contains(parentImport)) {
                        logger.info("Found import declaration removed: {}", parentImport);
                        ontologyChanges.add(new RemoveImportChange(ontologyId, parentImport));
                    }
                });
        childImports
                .forEach(childImport -> {
                    if(!parentImports.contains(childImport)) {
                        logger.info("Found import declaration added: {}", childImport);
                        ontologyChanges.add(new AddImportChange(ontologyId, childImport));
                    }
                });
    }

    private static void calculateOntologyAnnotationChanges(@NotNull OWLOntology childCommitOntology, @NotNull OWLOntology parentCommitOntology, @NotNull OWLOntologyID ontologyId, ArrayList<OntologyChange> ontologyChanges) {
        var parentAnnotations = parentCommitOntology.getAnnotations();
        var childAnnotations = childCommitOntology.getAnnotations();

        parentAnnotations
                .forEach(parentOntologyAnnotation -> {
                    if(!childAnnotations.contains(parentOntologyAnnotation)) {
                        ontologyChanges.add(new RemoveOntologyAnnotationChange(ontologyId, parentOntologyAnnotation));
                    }
                });
        childAnnotations
                .forEach(childOntologyAnnotation -> {
                    if(!parentAnnotations.contains(childOntologyAnnotation)) {
                        ontologyChanges.add(new AddOntologyAnnotationChange(ontologyId, childOntologyAnnotation));
                    }
                });
    }

    private static void calculateAxiomChanges(@NotNull OWLOntology childCommitOntology, @NotNull OWLOntology parentCommitOntology, @NotNull OWLOntologyID ontologyId, ArrayList<OntologyChange> axiomChanges) {
        var childCommitAxioms = Sets.newHashSet(childCommitOntology.getAxioms());
        var parentCommitAxioms = Sets.newHashSet(parentCommitOntology.getAxioms());

        // Find added axioms (present in current but not in previous)
        var addedAxioms = findAddedAxioms(childCommitAxioms, parentCommitAxioms);
        addedAxioms.forEach(axiom -> axiomChanges.add(new AddAxiomChange(ontologyId, axiom)));

        // Find removed axioms (present in previous but not in current)
        var removedAxioms = findRemovedAxioms(childCommitAxioms, parentCommitAxioms);
        removedAxioms.forEach(axiom -> axiomChanges.add(new RemoveAxiomChange(ontologyId, axiom)));

        if(!addedAxioms.isEmpty() || !removedAxioms.isEmpty()) {
            logger.debug(
                    "Found {} added axioms and {} removed axioms for ontology {}",
                    addedAxioms.size(),
                    removedAxioms.size(),
                    ontologyId);
        }
    }

    /** Finds axioms that were added (present in current but not in previous) */
    private static Set<OWLAxiom> findAddedAxioms(Set<OWLAxiom> currentAxioms, Set<OWLAxiom> previousAxioms) {
        var addedAxioms = new HashSet<>(currentAxioms);
        addedAxioms.removeAll(previousAxioms);
        return addedAxioms;
    }

    /** Finds axioms that were removed (present in previous but not in current) */
    private static Set<OWLAxiom> findRemovedAxioms(Set<OWLAxiom> currentAxioms, Set<OWLAxiom> previousAxioms) {
        var removedAxioms = new HashSet<>(previousAxioms);
        removedAxioms.removeAll(currentAxioms);
        return removedAxioms;
    }

    /**
     * Calculates differences between baseline and ancestor commit ontologies
     *
     * @param childCommitOntology The ontology from a baseline commit
     * @param parentCommitOntology The ontology from a ancestor commit
     * @return OntologyDifference containing all changes for this commit
     */
    @Nonnull
    public List<OntologyChange> calculateChanges(
            @Nonnull OWLOntology childCommitOntology,
            @Nonnull OWLOntology parentCommitOntology,
            @Nonnull OWLOntologyID ontologyId) {

        Objects.requireNonNull(childCommitOntology, "childCommitOntology cannot be null");
        Objects.requireNonNull(parentCommitOntology, "parentCommitOntology cannot be null");

        var ontologyChanges = Lists.<OntologyChange>newArrayList();

        calculateAxiomChanges(childCommitOntology, parentCommitOntology, ontologyId, ontologyChanges);
        calculateOntologyAnnotationChanges(childCommitOntology, parentCommitOntology, ontologyId, ontologyChanges);
        calculateImportDeclarationChanges(childCommitOntology, parentCommitOntology, ontologyId, ontologyChanges);

        return ImmutableList.copyOf(ontologyChanges);
    }
}
