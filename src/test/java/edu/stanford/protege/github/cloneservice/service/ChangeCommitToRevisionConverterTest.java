package edu.stanford.protege.github.cloneservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.webprotege.change.AddAxiomChange;
import edu.stanford.protege.webprotege.change.OntologyChange;
import edu.stanford.protege.webprotege.change.RemoveAxiomChange;
import edu.stanford.protege.webprotege.common.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntologyID;

/** Unit tests for {@link ChangeCommitToRevisionConverter} */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeCommitToRevisionConverter Tests")
class ChangeCommitToRevisionConverterTest {

    private ChangeCommitToRevisionConverter converter;

    @Mock
    private CommitMetadata commitMetadata;

    @Mock
    private OWLAxiom axiom1;

    @Mock
    private OWLAxiom axiom2;

    @Mock
    private OWLOntologyID ontologyId;

    private static final String REPOSITORY_URL = "https://github.com/example/repo";

    @BeforeEach
    void setUp() {
        converter = new ChangeCommitToRevisionConverter();
    }

    @Test
    @DisplayName("Convert ontology commit change to revision successfully")
    void convertOntologyCommitChangeToRevisionSuccessfully() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Test commit message";
        var commitDate = Instant.parse("2023-01-01T12:00:00Z");

        var addAxiomChange = new AddAxiomChange(ontologyId, axiom1);
        var removeAxiomChange = new RemoveAxiomChange(ontologyId, axiom2);
        var axiomChanges = List.<OntologyChange>of(addAxiomChange, removeAxiomChange);

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        assertNotNull(result);
        assertEquals(UserId.valueOf(username), result.getUserId());
        assertEquals(commitDate.toEpochMilli(), result.getTimestamp());
        assertEquals(2, result.getChanges().size());

        // Verify revision number is assigned
        assertNotNull(result.getRevisionNumber());
        assertEquals(1, result.getRevisionNumber().getValue());
    }

    @Test
    @DisplayName("Convert ADD axiom change to AddAxiomChange")
    void convertAddAxiomChangeToAddAxiomChange() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Add axiom";
        var commitDate = Instant.now();

        var addAxiomChange = new AddAxiomChange(ontologyId, axiom1);
        var axiomChanges = List.<OntologyChange>of(addAxiomChange);

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        var ontologyChanges = result.getChanges();
        assertEquals(1, ontologyChanges.size());
        assertInstanceOf(AddAxiomChange.class, ontologyChanges.get(0));

        var addChange = (AddAxiomChange) ontologyChanges.get(0);
        assertNotNull(addChange);
    }

    @Test
    @DisplayName("Convert REMOVE axiom change to RemoveAxiomChange")
    void convertRemoveAxiomChangeToRemoveAxiomChange() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Remove axiom";
        var commitDate = Instant.now();

        var removeAxiomChange = new RemoveAxiomChange(ontologyId, axiom1);
        var axiomChanges = List.<OntologyChange>of(removeAxiomChange);

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        var ontologyChanges = result.getChanges();
        assertEquals(1, ontologyChanges.size());
        assertInstanceOf(RemoveAxiomChange.class, ontologyChanges.get(0));

        var removeChange = (RemoveAxiomChange) ontologyChanges.get(0);
        assertNotNull(removeChange);
    }

    @Test
    @DisplayName("Handle empty axiom changes list")
    void handleEmptyAxiomChangesList() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Empty commit";
        var commitDate = Instant.now();

        var axiomChanges = List.<OntologyChange>of();

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        assertNotNull(result);
        assertEquals(UserId.valueOf(username), result.getUserId());
        assertTrue(result.getChanges().isEmpty());
    }

    @Test
    @DisplayName("Generate sequential revision numbers")
    void generateSequentialRevisionNumbers() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Test commit";
        var commitDate = Instant.now();
        var axiomChanges = List.<OntologyChange>of();

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result1 = converter.convert(ontologyCommitChange);
        var result2 = converter.convert(ontologyCommitChange);
        var result3 = converter.convert(ontologyCommitChange);

        // Assert
        var revisionNumber1 = result1.getRevisionNumber().getValue();
        var revisionNumber2 = result2.getRevisionNumber().getValue();
        var revisionNumber3 = result3.getRevisionNumber().getValue();

        assertTrue(revisionNumber2 > revisionNumber1);
        assertTrue(revisionNumber3 > revisionNumber2);
        assertEquals(2, revisionNumber2);
        assertEquals(3, revisionNumber3);
    }

    @Test
    @DisplayName("Preserve commit timestamp as epoch milliseconds")
    void preserveCommitTimestampAsEpochMilliseconds() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Timestamp test";
        var commitDate = Instant.parse("2023-12-25T14:30:45.123Z");
        var axiomChanges = List.<OntologyChange>of();

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        assertEquals(commitDate.toEpochMilli(), result.getTimestamp());
    }

    @Test
    @DisplayName("Handle mixed axiom change operations")
    void handleMixedAxiomChangeOperations() {
        // Arrange
        var username = "testuser";
        var commitMessage = "Mixed operations";
        var commitDate = Instant.now();

        var addChange1 = new AddAxiomChange(ontologyId, axiom1);
        var removeChange = new RemoveAxiomChange(ontologyId, axiom1);
        var addChange2 = new AddAxiomChange(ontologyId, axiom1);
        var axiomChanges = List.<OntologyChange>of(addChange1, removeChange, addChange2);

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        var ontologyChanges = result.getChanges();
        assertEquals(3, ontologyChanges.size());

        assertInstanceOf(AddAxiomChange.class, ontologyChanges.get(0));
        assertInstanceOf(RemoveAxiomChange.class, ontologyChanges.get(1));
        assertInstanceOf(AddAxiomChange.class, ontologyChanges.get(2));
    }

    @Test
    @DisplayName("Create UserId from committer username")
    void createUserIdFromCommitterUsername() {
        // Arrange
        var username = "john.doe@example.com";
        var commitMessage = "Test commit";
        var commitDate = Instant.now();
        var axiomChanges = List.<OntologyChange>of();

        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitMessage()).thenReturn(commitMessage);
        when(commitMetadata.commitDate()).thenReturn(commitDate);

        var ontologyCommitChange = new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());

        // Act
        var result = converter.convert(ontologyCommitChange);

        // Assert
        assertEquals(UserId.valueOf(username), result.getUserId());
        assertEquals(username, result.getUserId().id());
    }
}
