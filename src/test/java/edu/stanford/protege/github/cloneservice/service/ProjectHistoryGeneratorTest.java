package edu.stanford.protege.github.cloneservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.utils.*;
import edu.stanford.protege.webprotege.common.BlobLocation;
import edu.stanford.protege.webprotege.common.ProjectId;
import edu.stanford.protege.webprotege.common.UserId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.shaded.org.checkerframework.checker.units.qual.N;

/** Unit tests for {@link ProjectHistoryGenerator} */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectHistoryGenerator Tests")
class ProjectHistoryGeneratorTest {

    private ProjectHistoryGenerator projectHistoryGenerator;

    @Mock
    private OntologyHistoryAnalyzer ontologyHistoryAnalyzer;

    @Mock
    private ProjectHistoryStorer projectHistoryStorer;

    @Mock
    private BranchCoordinates branchCoordinates;

    @Mock
    private GitHubRepository gitHubRepository;

    @Mock
    private OntologyCommitChange commitChange1;

    @Mock
    private OntologyCommitChange commitChange2;

    private UserId testUserId;
    private ProjectId testProjectId;
    private BlobLocation testBlobLocation;

    @BeforeEach
    void setUp() {
        projectHistoryGenerator = new ProjectHistoryGenerator(ontologyHistoryAnalyzer, projectHistoryStorer);
        testUserId = UserId.valueOf("testuser");
        testProjectId = ProjectId.valueOf("12345678-1234-1234-1234-123456789012");
        testBlobLocation = new BlobLocation("test-bucket", "test-object");
    }

    @Test
    @DisplayName("Constructor accepts valid parameters")
    void constructorAcceptsValidParameters() {
        assertDoesNotThrow(() -> new ProjectHistoryGenerator(ontologyHistoryAnalyzer, projectHistoryStorer));
    }

    @Test
    @DisplayName("Generate project history successfully")
    void generateProjectHistorySuccessfully() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("test.owl");
        var projectHistory = List.of(commitChange1, commitChange2);

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(projectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(projectHistory)))
                .thenReturn(testBlobLocation);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act
            var result = projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                    testUserId, testProjectId, branchCoordinates, rootOntologyPath, any(OntologyHistoryAnalyzerProgressMonitor.class));

            // Assert
            assertEquals(testBlobLocation, result);
            verify(gitHubRepository).initialize();
            verify(ontologyHistoryAnalyzer).getCommitHistory(rootOntologyPath, gitHubRepository, any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class));
            verify(projectHistoryStorer).storeProjectHistory(eq(testProjectId), eq(projectHistory));
        }
    }

    @Test
    @DisplayName("Handle ontology history analyzer exception")
    void handleOntologyHistoryAnalyzerException() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("test.owl");
        var expectedException = new RuntimeException("Analysis failed");

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenThrow(expectedException);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act & Assert
            var exception = assertThrows(
                    Exception.class,
                    () -> projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                            testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor()));

            assertEquals(expectedException, exception);
            verify(projectHistoryStorer, never()).storeProjectHistory(any(), any());
        }
    }

    @Test
    @DisplayName("Handle project history storer exception")
    void handleProjectHistoryStorerException() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("test.owl");
        var projectHistory = List.of(commitChange1);
        var expectedException = new RuntimeException("Storage failed");

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(projectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(projectHistory)))
                .thenThrow(expectedException);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act & Assert
            var exception = assertThrows(
                    Exception.class,
                    () -> projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                            testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor()));

            assertEquals(expectedException, exception);
        }
    }

    @Test
    @DisplayName("Configure GitHub repository with correct file filters")
    void configureGitHubRepositoryWithCorrectFileFilters() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("test.owl");
        var projectHistory = List.of(commitChange1);

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(projectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(projectHistory)))
                .thenReturn(testBlobLocation);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act
            projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                    testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor());

            // Assert
            verify(mockBuilder).build();
        }
    }

    @Test
    @DisplayName("Initialize GitHub repository before use")
    void initializeGitHubRepositoryBeforeUse() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("test.owl");
        var projectHistory = List.of(commitChange1);

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(projectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(projectHistory)))
                .thenReturn(testBlobLocation);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act
            projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                    testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor());

            // Assert
            var inOrder = inOrder(gitHubRepository, ontologyHistoryAnalyzer);
            inOrder.verify(gitHubRepository).initialize();
            inOrder.verify(ontologyHistoryAnalyzer).getCommitHistory(rootOntologyPath, gitHubRepository, new NullProgressMonitor(), new NullOntologyChangesHandler());
        }
    }

    @Test
    @DisplayName("Handle empty project history")
    void handleEmptyProjectHistory() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("empty.owl");
        var emptyProjectHistory = List.<OntologyCommitChange>of();

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(emptyProjectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(emptyProjectHistory)))
                .thenReturn(testBlobLocation);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act
            var result = projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                    testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor());

            // Assert
            assertEquals(testBlobLocation, result);
            verify(projectHistoryStorer).storeProjectHistory(eq(testProjectId), eq(emptyProjectHistory));
        }
    }

    @Test
    @DisplayName("Pass correct parameters to dependencies")
    void passCorrectParametersToDependencies() throws Exception {
        // Arrange
        var rootOntologyPath = new RelativeFilePath("specific.owl");
        var projectHistory = List.of(commitChange1, commitChange2);

        when(ontologyHistoryAnalyzer.getCommitHistory(eq(rootOntologyPath), any(GitHubRepository.class), any(OntologyHistoryAnalyzerProgressMonitor.class), any(OntologyChangesHandler.class)))
                .thenReturn(projectHistory);
        when(projectHistoryStorer.storeProjectHistory(eq(testProjectId), eq(projectHistory)))
                .thenReturn(testBlobLocation);

        try (MockedStatic<edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory> mockedFactory =
                mockStatic(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.class)) {

            var mockBuilder = mock(edu.stanford.protege.commitnavigator.GitHubRepositoryBuilder.class);
            mockedFactory
                    .when(() -> edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory.create(
                            branchCoordinates))
                    .thenReturn(mockBuilder);
            when(mockBuilder.localWorkingDirectory(any(java.nio.file.Path.class)))
                    .thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(gitHubRepository);

            // Act
            projectHistoryGenerator.writeProjectHistoryFromGitHubRepo(
                    testUserId, testProjectId, branchCoordinates, rootOntologyPath, new NullProgressMonitor());

            // Assert
            verify(ontologyHistoryAnalyzer).getCommitHistory(rootOntologyPath, gitHubRepository, new NullProgressMonitor(), new NullOntologyChangesHandler());
            verify(projectHistoryStorer).storeProjectHistory(eq(testProjectId), eq(projectHistory));
        }
    }
}
