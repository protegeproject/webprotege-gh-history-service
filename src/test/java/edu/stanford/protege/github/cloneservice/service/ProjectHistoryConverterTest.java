package edu.stanford.protege.github.cloneservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.webprotege.change.AddAxiomChange;
import edu.stanford.protege.webprotege.change.OntologyChange;
import edu.stanford.protege.webprotege.revision.Revision;
import edu.stanford.protege.webprotege.revision.RevisionNumber;
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

/** Unit tests for {@link ProjectHistoryConverter} */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectHistoryConverter Tests")
class ProjectHistoryConverterTest {

    private ProjectHistoryConverter projectHistoryConverter;

    @Mock
    private ChangeCommitToRevisionConverter changeCommitToRevisionConverter;

    @Mock
    private OntologyCommitChange commitChange1;

    @Mock
    private OntologyCommitChange commitChange2;

    @Mock
    private OWLAxiom axiom1;

    @Mock
    private OWLOntologyID ontologyId;

    @BeforeEach
    void setUp() {
        projectHistoryConverter = new ProjectHistoryConverter(changeCommitToRevisionConverter);
    }

    @Test
    @DisplayName("Handle empty project history list")
    void handleEmptyProjectHistoryList() {
        // Given
        var emptyProjectHistory = List.<OntologyCommitChange>of();

        // When
        var result = projectHistoryConverter.convertProjectHistoryToRevisions(emptyProjectHistory);

        // Then
        assertTrue(result.isEmpty());
        verify(changeCommitToRevisionConverter, never()).convert(any());
    }

    @Test
    @DisplayName("Convert single commit change to single revision")
    void convertSingleCommitChangeToSingleRevision() {
        // Given
        var projectHistory = List.of(commitChange1);
        var mockRevision = mock(Revision.class);
        when(mockRevision.getRevisionNumber()).thenReturn(RevisionNumber.getRevisionNumber(1));
        when(changeCommitToRevisionConverter.convert(commitChange1)).thenReturn(mockRevision);

        // When
        var result = projectHistoryConverter.convertProjectHistoryToRevisions(projectHistory);

        // Then
        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getRevisionNumber().getValue());
        verify(changeCommitToRevisionConverter).convert(commitChange1);
    }

    @Test
    @DisplayName("Verify correct ordering: last project history item becomes first revision")
    void verifyCorrectOrderingLastProjectHistoryItemBecomesFirstRevision() {
        // Given: Project history ordered newest to oldest
        var newestCommit = createMockCommitChange("newest", "commit1");
        var middleCommit = createMockCommitChange("middle", "commit2");
        var oldestCommit = createMockCommitChange("oldest", "commit3");
        var projectHistory = List.of(newestCommit, middleCommit, oldestCommit);

        // Mock converter to return revisions with sequential numbers
        var revision1 = mock(Revision.class);
        var revision2 = mock(Revision.class);
        var revision3 = mock(Revision.class);

        when(changeCommitToRevisionConverter.convert(oldestCommit)).thenReturn(revision1);
        when(changeCommitToRevisionConverter.convert(middleCommit)).thenReturn(revision2);
        when(changeCommitToRevisionConverter.convert(newestCommit)).thenReturn(revision3);

        // When
        var result = projectHistoryConverter.convertProjectHistoryToRevisions(projectHistory);

        // Then: Verify ordering
        assertEquals(3, result.size());

        // Verify conversion order: oldest first, newest last
        var inOrder = inOrder(changeCommitToRevisionConverter);
        inOrder.verify(changeCommitToRevisionConverter).convert(oldestCommit);
        inOrder.verify(changeCommitToRevisionConverter).convert(middleCommit);
        inOrder.verify(changeCommitToRevisionConverter).convert(newestCommit);
    }

    @Test
    @DisplayName("Handle null project history parameter")
    void handleNullProjectHistoryParameter() {
        // Given
        List<OntologyCommitChange> nullProjectHistory = null;

        // When & Then
        assertThrows(
                NullPointerException.class,
                () -> projectHistoryConverter.convertProjectHistoryToRevisions(nullProjectHistory));
        verify(changeCommitToRevisionConverter, never()).convert(any());
    }

    @Test
    @DisplayName("Preserve original list order in input")
    void preserveOriginalListOrderInInput() {
        // Given: Mutable list that we can verify wasn't modified
        var mutableProjectHistory = new java.util.ArrayList<OntologyCommitChange>();
        mutableProjectHistory.add(commitChange1);
        mutableProjectHistory.add(commitChange2);
        var originalFirstElement = mutableProjectHistory.get(0);
        var originalSecondElement = mutableProjectHistory.get(1);

        var mockRevision = mock(Revision.class);
        when(changeCommitToRevisionConverter.convert(any())).thenReturn(mockRevision);

        // When
        projectHistoryConverter.convertProjectHistoryToRevisions(mutableProjectHistory);

        // Then: Verify original list wasn't modified
        assertEquals(2, mutableProjectHistory.size());
        assertSame(originalFirstElement, mutableProjectHistory.get(0));
        assertSame(originalSecondElement, mutableProjectHistory.get(1));
    }

    @Test
    @DisplayName("Handle multiple commits with same content")
    void handleMultipleCommitsWithSameContent() {
        // Given
        var projectHistory = List.of(commitChange1, commitChange1, commitChange1);

        var revision1 = mock(Revision.class);
        var revision2 = mock(Revision.class);
        var revision3 = mock(Revision.class);

        when(changeCommitToRevisionConverter.convert(commitChange1)).thenReturn(revision1, revision2, revision3);

        // When
        var result = projectHistoryConverter.convertProjectHistoryToRevisions(projectHistory);

        // Then
        assertEquals(3, result.size());
        verify(changeCommitToRevisionConverter, times(3)).convert(commitChange1);
    }

    @Test
    @DisplayName("Handle null ChangeCommitToRevisionConverter in constructor")
    void handleNullChangeCommitToRevisionConverterInConstructor() {
        // Given
        ChangeCommitToRevisionConverter nullConverter = null;

        // When & Then
        assertThrows(NullPointerException.class, () -> new ProjectHistoryConverter(nullConverter));
    }

    @Test
    @DisplayName("Verify sequential revision numbers across conversions")
    void verifySequentialRevisionNumbersAcrossConversions() {
        // Given: Use real ChangeCommitToRevisionConverter to test actual sequential behavior
        var realConverter = new ChangeCommitToRevisionConverter();
        var converterWithRealImplementation = new ProjectHistoryConverter(realConverter);

        var commit1 = createRealCommitChange("user1", "hash1");
        var commit2 = createRealCommitChange("user2", "hash2");
        var commit3 = createRealCommitChange("user3", "hash3");
        var projectHistory = List.of(commit1, commit2, commit3);

        // When
        var result = converterWithRealImplementation.convertProjectHistoryToRevisions(projectHistory);

        // Then: Verify sequential revision numbers
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).getRevisionNumber().getValue()); // Oldest commit -> revision 1
        assertEquals(2, result.get(1).getRevisionNumber().getValue()); // Middle commit -> revision 2
        assertEquals(3, result.get(2).getRevisionNumber().getValue()); // Newest commit -> revision 3
    }

    private OntologyCommitChange createMockCommitChange(String username, String commitHash) {
        var commitMetadata = mock(CommitMetadata.class);
        lenient().when(commitMetadata.committerUsername()).thenReturn(username);
        lenient().when(commitMetadata.commitHash()).thenReturn(commitHash);
        lenient().when(commitMetadata.commitMessage()).thenReturn("Test commit: " + username);
        lenient().when(commitMetadata.commitDate()).thenReturn(Instant.now());

        var axiomChanges = List.<OntologyChange>of(new AddAxiomChange(ontologyId, axiom1));
        return new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());
    }

    private OntologyCommitChange createRealCommitChange(String username, String commitHash) {
        var commitMetadata = mock(CommitMetadata.class);
        when(commitMetadata.committerUsername()).thenReturn(username);
        when(commitMetadata.commitHash()).thenReturn(commitHash);
        when(commitMetadata.commitMessage()).thenReturn("Real commit: " + username);
        when(commitMetadata.commitDate()).thenReturn(Instant.now());

        var axiomChanges = List.<OntologyChange>of(new AddAxiomChange(ontologyId, axiom1));
        return new OntologyCommitChange(axiomChanges, commitMetadata, Optional.empty());
    }
}
