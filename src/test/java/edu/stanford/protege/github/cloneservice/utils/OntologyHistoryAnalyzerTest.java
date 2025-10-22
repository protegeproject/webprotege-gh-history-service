package edu.stanford.protege.github.cloneservice.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.config.RepositoryConfig;
import edu.stanford.protege.github.cloneservice.exception.OntologyComparisonException;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link OntologyHistoryAnalyzer} */
@ExtendWith(MockitoExtension.class)
@DisplayName("OntologyHistoryAnalyzer Tests")
class OntologyHistoryAnalyzerTest {

    private OntologyHistoryAnalyzer historyAnalyzer;

    @Mock
    private OntologyLoader ontologyLoader;

    @Mock
    private OntologyDifferenceCalculator differenceCalculator;

    @Mock
    private GitHubRepository gitHubRepository;

    @Mock
    private RepositoryConfig repositoryConfig;

    @BeforeEach
    void setUp() {
        historyAnalyzer = new OntologyHistoryAnalyzer(ontologyLoader, differenceCalculator);
    }

    @Test
    @DisplayName("Should throw NullPointerException when ontologyLoader is null")
    void throwExceptionWhenOntologyLoaderNull() {
        var exception =
                assertThrows(NullPointerException.class, () -> new OntologyHistoryAnalyzer(null, differenceCalculator));

        assertEquals("OntologyLoader cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when differenceCalculator is null")
    void throwExceptionWhenDifferenceCalculatorNull() {
        var exception =
                assertThrows(NullPointerException.class, () -> new OntologyHistoryAnalyzer(ontologyLoader, null));

        assertEquals("OntologyDifferenceCalculator cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when ontologyFilePath is null")
    void throwExceptionWhenOntologyFilePathNull() {
        var exception = assertThrows(
                NullPointerException.class, () -> historyAnalyzer.getCommitHistory(null, gitHubRepository, new NullProgressMonitor()));

        assertEquals("ontologyFilePath cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when gitHubRepository is null")
    void throwExceptionWhenGitHubRepositoryNull() {
        var ontologyFile = new RelativeFilePath("ontology.owl");
        var exception =
                assertThrows(NullPointerException.class, () -> historyAnalyzer.getCommitHistory(ontologyFile, null, new NullProgressMonitor()));

        assertEquals("gitHubRepository cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw OntologyComparisonException when repository operations fail")
    void throwOntologyComparisonExceptionWhenRepositoryOperationsFail() throws Exception {
        var ontologyFile = new RelativeFilePath("test.owl");

        // Mock repository config
        when(repositoryConfig.getRepositoryUrl()).thenReturn("https://github.com/test/repo.git");
        when(gitHubRepository.getConfig()).thenReturn(repositoryConfig);

        // Simulate the repository throwing an exception during working directory access
        when(gitHubRepository.getWorkingDirectory()).thenThrow(new RuntimeException("Repository error"));

        var exception = assertThrows(
                OntologyComparisonException.class,
                () -> historyAnalyzer.getCommitHistory(ontologyFile, gitHubRepository, new NullProgressMonitor()));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Failed to analyze ontology commit history"));
        assertNotNull(exception.getCause());
        assertInstanceOf(RuntimeException.class, exception.getCause());
        assertEquals("Repository error", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("Should validate constructor parameters using Objects.requireNonNull")
    void validateConstructorParametersUsingObjectsRequireNonNull() {
        // Test first parameter validation
        var exception1 =
                assertThrows(NullPointerException.class, () -> new OntologyHistoryAnalyzer(null, differenceCalculator));
        assertEquals("OntologyLoader cannot be null", exception1.getMessage());

        // Test second parameter validation
        var exception2 =
                assertThrows(NullPointerException.class, () -> new OntologyHistoryAnalyzer(ontologyLoader, null));
        assertEquals("OntologyDifferenceCalculator cannot be null", exception2.getMessage());
    }

    @Test
    @DisplayName("Should validate getCommitHistory parameters using Objects.requireNonNull")
    void validateGetCommitHistoryParametersUsingObjectsRequireNonNull() {
        // Test ontologyFilePath validation
        var exception1 = assertThrows(
                NullPointerException.class, () -> historyAnalyzer.getCommitHistory(null, gitHubRepository, new NullProgressMonitor()));
        assertEquals("ontologyFilePath cannot be null", exception1.getMessage());

        // Test gitHubRepository validation
        var ontologyFile = new RelativeFilePath("test.owl");
        var exception2 =
                assertThrows(NullPointerException.class, () -> historyAnalyzer.getCommitHistory(ontologyFile, null, new NullProgressMonitor()));
        assertEquals("gitHubRepository cannot be null", exception2.getMessage());
    }
}
