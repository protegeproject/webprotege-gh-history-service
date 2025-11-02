package edu.stanford.protege.github.cloneservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.service.ChangeCommitToRevisionConverter;
import edu.stanford.protege.github.cloneservice.service.ProjectHistoryConverter;
import edu.stanford.protege.github.cloneservice.utils.*;
import edu.stanford.protege.webprotege.common.UserId;
import edu.stanford.protege.webprotege.revision.Revision;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test that coordinates with the grocery-ontology GitHub repository, gets the project
 * history, and tests the ProjectHistoryConverter with proper ordering logic.
 *
 * <p>This test uses real GitHub repository coordination and hardcoded expected results from actual
 * runs. It tests the complete conversion pipeline including the critical ordering logic where
 * project history items (newest to oldest) are converted to revisions (oldest to newest). External
 * dependencies that could be mocked for faster/more reliable testing: - Network access to GitHub
 * (but task specifically requires real coordination) - File system operations (handled by
 * GitHubRepository implementation) - OWL API ontology loading (tested through real files)
 *
 * <p>The test maintains a balance between integration testing with real dependencies and
 * predictable results through hardcoded expectations based on the known state of the
 * grocery-ontology repository as of 2025-08-26.
 */
@DisplayName("Grocery Ontology Integration Tests")
class GroceryOntologyIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(GroceryOntologyIntegrationTest.class);

    private static final String GROCERY_ONTOLOGY_URL = "https://github.com/protegeteam/grocery-ontology";
    private static final String MASTER_BRANCH = "master";
    private static final RelativeFilePath ONTOLOGY_FILE_PATH = new RelativeFilePath("grocery.owl");

    private OntologyHistoryAnalyzer historyAnalyzer;
    private ProjectHistoryConverter projectHistoryConverter;
    private String cloneDirectory;

    @BeforeEach
    void setUp() {
        var ontologyManagerProvider = new OntologyManagerProvider();
        var ontologyLoader = new OntologyLoader(ontologyManagerProvider);
        var differenceCalculator = new OntologyDifferencesCalculator();
        historyAnalyzer = OntologyHistoryAnalyzer.withDefaultMaxAnalysisTime(ontologyLoader, differenceCalculator);

        // Use the new ProjectHistoryConverter which includes the ordering logic
        var changeCommitToRevisionConverter = new ChangeCommitToRevisionConverter();
        projectHistoryConverter = new ProjectHistoryConverter(changeCommitToRevisionConverter);
        cloneDirectory = null;
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up current test's directory
        if (cloneDirectory != null) {
            var path = Path.of(cloneDirectory);
            if (Files.exists(path)) {
                logger.info("Cleaning up temporary directory: {}", cloneDirectory);
                deleteDirectoryRecursively(path);
            }
        }
    }

    @Test
    @DisplayName("Should coordinate with grocery-ontology repository and convert commit history to revisions")
    void coordinateWithGroceryOntologyRepositoryAndConvertToRevisions() throws Exception {
        // Arrange
        logger.info("Starting integration test with grocery-ontology repository");
        cloneDirectory = "/tmp/test-" + UUID.randomUUID();
        var gitHubRepository = createGitHubRepository(cloneDirectory);

        // Act
        logger.info("Getting commit history from repository");
        var commitHistory = historyAnalyzer.getCommitHistory(ONTOLOGY_FILE_PATH, gitHubRepository, new NullProgressMonitor());

        logger.info("Converting {} commit changes to revisions", commitHistory.size());
        var revisions = projectHistoryConverter.convertProjectHistoryToRevisions(commitHistory);

        // Assert - Based on hardcoded expected results from actual run
        // Updated expected results from running against grocery-ontology as of 2025-08-26:
        // The repository now has multiple commits (54 as of this run)
        assertNotNull(commitHistory, "Commit history should not be null");
        assertTrue(commitHistory.size() >= 54, "Should have at least 54 commit in grocery ontology history");

        assertNotNull(revisions, "Revisions should not be null");
        assertEquals(commitHistory.size(), revisions.size(), "Should have same number of revisions as commits");

        // Verify basic revision structure
        var firstRevision = revisions.get(0);
        assertNotNull(firstRevision, "First revision object should not be null");
        assertNotNull(firstRevision.getUserId(), "First revision should have a user ID");
        assertNotNull(firstRevision.getRevisionNumber(), "First revision should have a revision number");
        assertNotNull(firstRevision.getChanges(), "First revision should have changes");
        assertTrue(firstRevision.getTimestamp() > 0, "First revision should have a valid timestamp");

        // Verify revision has expected structure - should be revision number 1 (oldest commit first)
        assertEquals(1, firstRevision.getRevisionNumber().getValue(), "First revision should be revision number 1");

        // Verify ordering logic: the last project history item (oldest commit) becomes first revision
        if (commitHistory.size() > 1) {
            var lastCommitChange = commitHistory.get(commitHistory.size() - 1); // oldest commit
            var lastRevision = revisions.get(revisions.size() - 1); // should be highest revision number

            assertEquals(
                    1,
                    firstRevision.getRevisionNumber().getValue(),
                    "First revision should be revision number 1 (oldest commit)");
            assertEquals(
                    commitHistory.size(),
                    lastRevision.getRevisionNumber().getValue(),
                    "Last revision should have highest revision number (newest commit)");

            // Verify that the oldest commit (last in history) becomes the first revision
            var expectedFirstUserId =
                    UserId.valueOf(lastCommitChange.commitMetadata().committerUsername());
            assertEquals(
                    expectedFirstUserId,
                    firstRevision.getUserId(),
                    "First revision should correspond to oldest commit (last project history item)");

            // Verify that the newest commit (first in history) becomes the last revision
            var newestCommitChange = commitHistory.get(0); // newest commit
            var expectedLastUserId =
                    UserId.valueOf(newestCommitChange.commitMetadata().committerUsername());
            assertEquals(
                    expectedLastUserId,
                    lastRevision.getUserId(),
                    "Last revision should correspond to newest commit (first project history item)");

            // Verify timestamps match for the correct corresponding pairs
            var expectedFirstTimestamp =
                    lastCommitChange.commitMetadata().commitDate().toEpochMilli();
            assertEquals(
                    expectedFirstTimestamp,
                    firstRevision.getTimestamp(),
                    "First revision timestamp should match oldest commit metadata");

            logger.info(
                    "Verified ordering: {} project history items converted to {} revisions with correct order",
                    commitHistory.size(),
                    revisions.size());
        }

        // Log results for verification
        logger.info(
                "Successfully processed {} commits into {} revisions using ProjectHistoryConverter",
                commitHistory.size(),
                revisions.size());
        logRevisionSummary(revisions);
    }

    @Test
    @DisplayName("Should handle grocery-ontology repository with expected commit structure")
    void handleGroceryOntologyRepositoryWithExpectedCommitStructure() throws Exception {
        // Arrange
        logger.info("Testing grocery-ontology repository commit structure");
        cloneDirectory = "/tmp/test-" + UUID.randomUUID();
        var gitHubRepository = createGitHubRepository(cloneDirectory);

        // Act
        var commitHistory = historyAnalyzer.getCommitHistory(ONTOLOGY_FILE_PATH, gitHubRepository, new NullProgressMonitor());

        // Assert - Validate expected repository structure with updated expectations
        assertNotNull(commitHistory, "Commit history should not be null");
        assertTrue(commitHistory.size() > 0, "Grocery ontology should have at least 1 commit");

        // Validate all commits have expected metadata structure
        for (var commitChange : commitHistory) {
            assertNotNull(commitChange.commitMetadata(), "Commit metadata should not be null");
            assertNotNull(commitChange.commitMetadata().committerUsername(), "Committer username should not be null");
            assertNotNull(commitChange.commitMetadata().commitMessage(), "Commit message should not be null");
            assertNotNull(commitChange.commitMetadata().commitDate(), "Commit date should not be null");
            assertNotNull(commitChange.axiomChanges(), "Axiom changes should not be null");

            assertFalse(
                    commitChange.commitMetadata().committerUsername().isEmpty(),
                    "Committer username should not be empty");
            assertFalse(commitChange.commitMetadata().commitMessage().isEmpty(), "Commit message should not be empty");
        }

        logger.info("Validated {} commits with proper metadata structure", commitHistory.size());
    }

    @Test
    @DisplayName("Should convert grocery-ontology commits to revisions with proper ontology changes")
    void convertGroceryOntologyCommitsToRevisionsWithProperOntologyChanges() throws Exception {
        // Arrange
        logger.info("Testing conversion of grocery-ontology commits to ontology changes");
        cloneDirectory = "/tmp/test-" + UUID.randomUUID();
        var gitHubRepository = createGitHubRepository(cloneDirectory);

        // Act
        var commitHistory = historyAnalyzer.getCommitHistory(ONTOLOGY_FILE_PATH, gitHubRepository, new NullProgressMonitor());
        var revisions = projectHistoryConverter.convertProjectHistoryToRevisions(commitHistory);

        // Assert - Validate ontology change conversion with updated expectations
        assertNotNull(revisions, "Revisions should not be null");
        assertEquals(
                commitHistory.size(), revisions.size(), "Should have converted same number of revisions as commits");

        // Verify each revision has properly converted changes
        // Note: Due to ordering logic, we need to compare with the reversed index
        for (int i = 0; i < revisions.size(); i++) {
            var revision = revisions.get(i);
            var correspondingCommitIndex = commitHistory.size() - 1 - i; // Reverse index
            var commitChange = commitHistory.get(correspondingCommitIndex);

            assertEquals(
                    commitChange.axiomChanges().size(),
                    revision.getChanges().size(),
                    "Revision " + i + " should have same number of changes as corresponding commit (index "
                            + correspondingCommitIndex + ")");
        }

        // Verify total counts match
        var totalOntologyChanges =
                revisions.stream().mapToInt(rev -> rev.getChanges().size()).sum();

        var totalAxiomChanges = commitHistory.stream()
                .mapToInt(commit -> commit.axiomChanges().size())
                .sum();

        assertTrue(totalOntologyChanges >= 0, "Should have non-negative total ontology changes");
        assertTrue(totalAxiomChanges >= 0, "Should have non-negative total axiom changes");
        assertEquals(
                totalAxiomChanges, totalOntologyChanges, "Total ontology changes should match total axiom changes");

        logger.info(
                "Successfully validated {} revisions with {} total ontology changes",
                revisions.size(),
                totalOntologyChanges);
    }

    /**
     * Logs a summary of the revisions for verification purposes.
     *
     * @param revisions the list of revisions to summarize
     */
    private void logRevisionSummary(List<Revision> revisions) {
        logger.info("=== Revision Summary ===");

        for (int i = 0; i < Math.min(revisions.size(), 5); i++) { // Log first 5 revisions
            var revision = revisions.get(i);
            logger.info(
                    "Revision {}: User={}, Changes={}, Message='{}'",
                    revision.getRevisionNumber().getValue(),
                    revision.getUserId().id(),
                    revision.getChanges().size(),
                    truncateMessage(revision.getHighLevelDescription(), 50));
        }

        if (revisions.size() > 5) {
            logger.info("... and {} more revisions", revisions.size() - 5);
        }

        var totalChanges =
                revisions.stream().mapToInt(r -> r.getChanges().size()).sum();

        logger.info("Total: {} revisions with {} ontology changes", revisions.size(), totalChanges);
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "null";
        return message.length() > maxLength ? message.substring(0, maxLength) + "..." : message;
    }

    /**
     * Creates a GitHub repository for the grocery ontology integration test.
     *
     * @param cloneDirectory the directory to clone the repository to
     * @return configured GitHub repository for testing
     * @throws Exception if repository creation or initialization fails
     */
    private GitHubRepository createGitHubRepository(String cloneDirectory) throws Exception {
        var branchCoordinates = BranchCoordinates.createFromUrl(GROCERY_ONTOLOGY_URL, MASTER_BRANCH);

        var repository = GitHubRepositoryBuilderFactory.create(branchCoordinates)
                .localWorkingDirectory(cloneDirectory)
                .build();
        repository.initialize();
        return repository;
    }

    /**
     * Recursively deletes a directory and all its contents.
     *
     * @param path the path to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(child -> {
                    try {
                        deleteDirectoryRecursively(child);
                    } catch (IOException e) {
                        logger.warn("Failed to delete {}: {}", child, e.getMessage());
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }
}
