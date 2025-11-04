package edu.stanford.protege.github.cloneservice.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import edu.stanford.protege.webprotege.change.AddAxiomChange;
import edu.stanford.protege.webprotege.change.RemoveAxiomChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

/** Unit tests for {@link OntologyDifferencesCalculator} */
@ExtendWith(MockitoExtension.class)
@DisplayName("OntologyDifferencesCalculator Tests")
class OntologyDifferenceCalculatorTest {

    private OntologyDifferencesCalculator differenceCalculator;

    @Mock
    private OWLOntology currentOntology;

    @Mock
    private OWLOntology previousOntology;

    @Mock
    private OWLOntologyID ontologyId;

    @Mock
    private OWLAxiom axiom1;

    @Mock
    private OWLAxiom axiom2;

    @Mock
    private OWLAxiom axiom3;

    @BeforeEach
    void setUp() {
        differenceCalculator = new OntologyDifferencesCalculator();
    }

    @Test
    @DisplayName("Should throw NullPointerException when currentOntology is null")
    void throwExceptionWhenCurrentOntologyNull() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> differenceCalculator.calculateChanges(null, previousOntology, ontologyId));

        assertEquals("childCommitOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when previousOntology is null")
    void throwExceptionWhenPreviousOntologyNull() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> differenceCalculator.calculateChanges(currentOntology, null, ontologyId));

        assertEquals("parentCommitOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should return empty changes when ontologies are identical")
    void returnEmptyChangesWhenOntologiesIdentical() {
        var axioms = Set.of(axiom1, axiom2);
        when(currentOntology.getAxioms()).thenReturn(axioms);
        when(previousOntology.getAxioms()).thenReturn(axioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should detect added axioms correctly")
    void detectAddedAxiomsCorrectly() {
        var currentAxioms = Set.of(axiom1, axiom2, axiom3);
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(1, result.size());

        var axiomChange = result.get(0);
        assertEquals(axiom3, axiomChange.getAxiomOrThrow());
        assertEquals(ontologyId, axiomChange.ontologyId());
    }

    @Test
    @DisplayName("Should detect removed axioms correctly")
    void detectRemovedAxiomsCorrectly() {
        var currentAxioms = Set.of(axiom1, axiom2);
        var previousAxioms = Set.of(axiom1, axiom2, axiom3);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(1, result.size());

        var axiomChange = result.get(0);
        assertInstanceOf(RemoveAxiomChange.class, axiomChange);
        assertEquals(axiom3, axiomChange.getAxiomOrThrow());
        assertEquals(ontologyId, axiomChange.ontologyId());
    }

    @Test
    @DisplayName("Should detect both added and removed axioms")
    void detectBothAddedAndRemovedAxioms() {
        var currentAxioms = Set.of(axiom1, axiom3); // axiom1 stays, axiom2 removed, axiom3 added
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());

        var addedChange = result.stream()
                .filter(change -> change instanceof AddAxiomChange)
                .findFirst();
        var removedChange = result.stream()
                .filter(change -> change instanceof RemoveAxiomChange)
                .findFirst();

        assertTrue(addedChange.isPresent());
        assertEquals(axiom3, addedChange.get().getAxiomOrThrow());

        assertTrue(removedChange.isPresent());
        assertEquals(axiom2, removedChange.get().getAxiomOrThrow());
    }

    @Test
    @DisplayName("Should handle empty current ontology")
    void handleEmptyCurrentOntology() {
        var currentAxioms = Set.<OWLAxiom>of();
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change instanceof RemoveAxiomChange));
    }

    @Test
    @DisplayName("Should handle empty previous ontology")
    void handleEmptyPreviousOntology() {
        var currentAxioms = Set.of(axiom1, axiom2);
        var previousAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change instanceof AddAxiomChange));
    }

    @Test
    @DisplayName("Should handle both ontologies being empty")
    void handleBothOntologiesEmpty() {
        var emptyAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(emptyAxioms);
        when(previousOntology.getAxioms()).thenReturn(emptyAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should use provided ontology ID for all changes")
    void useProvidedOntologyIdForAllChanges() {
        var currentAxioms = Set.of(axiom1, axiom3);
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change.ontologyId().equals(ontologyId)));
    }

    @Test
    @DisplayName("Should return immutable list")
    void returnImmutableList() {
        var currentAxioms = Set.of(axiom1);
        var previousAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(new AddAxiomChange(ontologyId, axiom2)));
    }
}
