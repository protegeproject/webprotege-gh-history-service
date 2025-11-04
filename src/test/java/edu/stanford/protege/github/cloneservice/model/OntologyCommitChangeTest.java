package edu.stanford.protege.github.cloneservice.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import java.util.List;
import java.util.Optional;

import edu.stanford.protege.webprotege.change.AxiomChange;
import edu.stanford.protege.webprotege.change.OntologyChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link OntologyCommitChange} record */
@DisplayName("OntologyCommitChange Tests")
class OntologyCommitChangeTest {

    private List<OntologyChange> mockAxiomChanges;
    private CommitMetadata mockCommitMetadata;
    private String mockRepositoryUrl;

    @BeforeEach
    void setUp() {
        mockAxiomChanges = List.of(mock(AxiomChange.class), mock(AxiomChange.class));
        mockCommitMetadata = mock(CommitMetadata.class);
        mockRepositoryUrl = "https://github.com/example/repo";
    }

    @Test
    @DisplayName("Should create OntologyCommitChange with all required parameters")
    void createOntologyCommitChangeWhenAllParametersProvided() {
        var ontologyCommitChange = new OntologyCommitChange(mockAxiomChanges, mockCommitMetadata, Optional.empty());

        assertEquals(mockAxiomChanges, ontologyCommitChange.axiomChanges());
        assertEquals(mockCommitMetadata, ontologyCommitChange.baselineCommit());
    }

    @Test
    @DisplayName("Should throw NullPointerException when axiomChanges is null")
    void throwExceptionWhenAxiomChangesNull() {
        var exception = assertThrows(
                NullPointerException.class,
                () -> new OntologyCommitChange(null, mockCommitMetadata, Optional.empty()));

        assertEquals("axiomChanges cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when baselineCommit is null")
    void throwExceptionWhenCommitMetadataNull() {
        var exception = assertThrows(
                NullPointerException.class, () -> new OntologyCommitChange(mockAxiomChanges, null, Optional.empty()));

        assertEquals("baselineCommit cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when ancestorCommitId is null")
    void throwExceptionWhenRepositoryUrlNull() {
        var exception = assertThrows(
                NullPointerException.class, () -> new OntologyCommitChange(mockAxiomChanges, mockCommitMetadata, null));

        assertEquals("ancestorCommit cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should accept empty axiom changes list")
    void acceptEmptyAxiomChangesList() {
        var emptyAxiomChanges = List.<OntologyChange>of();

        var ontologyCommitChange = new OntologyCommitChange(emptyAxiomChanges, mockCommitMetadata, Optional.empty());

        assertEquals(emptyAxiomChanges, ontologyCommitChange.axiomChanges());
        assertTrue(ontologyCommitChange.axiomChanges().isEmpty());
    }
}
