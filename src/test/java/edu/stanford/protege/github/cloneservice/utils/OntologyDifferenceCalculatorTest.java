package edu.stanford.protege.github.cloneservice.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import edu.stanford.protege.github.cloneservice.model.AxiomChange;
import java.util.Set;
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
                () -> differenceCalculator.calculateAxiomChanges(null, previousOntology, ontologyId));

        assertEquals("childCommitOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when previousOntology is null")
    void throwExceptionWhenPreviousOntologyNull() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> differenceCalculator.calculateAxiomChanges(currentOntology, null, ontologyId));

        assertEquals("parentCommitOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should return empty changes when ontologies are identical")
    void returnEmptyChangesWhenOntologiesIdentical() {
        var axioms = Set.of(axiom1, axiom2);
        when(currentOntology.getAxioms()).thenReturn(axioms);
        when(previousOntology.getAxioms()).thenReturn(axioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

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

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(1, result.size());

        var axiomChange = result.get(0);
        assertEquals(AxiomChange.OperationType.ADD, axiomChange.operationType());
        assertEquals(axiom3, axiomChange.axiom());
        assertEquals(ontologyId, axiomChange.ontologyID());
    }

    @Test
    @DisplayName("Should detect removed axioms correctly")
    void detectRemovedAxiomsCorrectly() {
        var currentAxioms = Set.of(axiom1, axiom2);
        var previousAxioms = Set.of(axiom1, axiom2, axiom3);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(1, result.size());

        var axiomChange = result.get(0);
        assertEquals(AxiomChange.OperationType.REMOVE, axiomChange.operationType());
        assertEquals(axiom3, axiomChange.axiom());
        assertEquals(ontologyId, axiomChange.ontologyID());
    }

    @Test
    @DisplayName("Should detect both added and removed axioms")
    void detectBothAddedAndRemovedAxioms() {
        var currentAxioms = Set.of(axiom1, axiom3); // axiom1 stays, axiom2 removed, axiom3 added
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());

        var addedChange = result.stream()
                .filter(change -> change.operationType() == AxiomChange.OperationType.ADD)
                .findFirst();
        var removedChange = result.stream()
                .filter(change -> change.operationType() == AxiomChange.OperationType.REMOVE)
                .findFirst();

        assertTrue(addedChange.isPresent());
        assertEquals(axiom3, addedChange.get().axiom());

        assertTrue(removedChange.isPresent());
        assertEquals(axiom2, removedChange.get().axiom());
    }

    @Test
    @DisplayName("Should handle empty current ontology")
    void handleEmptyCurrentOntology() {
        var currentAxioms = Set.<OWLAxiom>of();
        var previousAxioms = Set.of(axiom1, axiom2);

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change.operationType() == AxiomChange.OperationType.REMOVE));
    }

    @Test
    @DisplayName("Should handle empty previous ontology")
    void handleEmptyPreviousOntology() {
        var currentAxioms = Set.of(axiom1, axiom2);
        var previousAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change.operationType() == AxiomChange.OperationType.ADD));
    }

    @Test
    @DisplayName("Should handle both ontologies being empty")
    void handleBothOntologiesEmpty() {
        var emptyAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(emptyAxioms);
        when(previousOntology.getAxioms()).thenReturn(emptyAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

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

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(change -> change.ontologyID().equals(ontologyId)));
    }

    @Test
    @DisplayName("Should return immutable list")
    void returnImmutableList() {
        var currentAxioms = Set.of(axiom1);
        var previousAxioms = Set.<OWLAxiom>of();

        when(currentOntology.getAxioms()).thenReturn(currentAxioms);
        when(previousOntology.getAxioms()).thenReturn(previousAxioms);

        var result = differenceCalculator.calculateAxiomChanges(currentOntology, previousOntology, ontologyId);

        assertNotNull(result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(AxiomChange.addAxiom(axiom2, ontologyId)));
    }
}
