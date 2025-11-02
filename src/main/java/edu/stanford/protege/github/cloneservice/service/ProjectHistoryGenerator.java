package edu.stanford.protege.github.cloneservice.service;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory;
import edu.stanford.protege.commitnavigator.exceptions.GitHubNavigatorException;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.github.cloneservice.exception.OntologyComparisonException;
import edu.stanford.protege.github.cloneservice.exception.StorageException;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.utils.OntologyHistoryAnalyzer;
import edu.stanford.protege.github.cloneservice.utils.OntologyHistoryAnalyzerProgressMonitor;
import edu.stanford.protege.webprotege.common.BlobLocation;
import edu.stanford.protege.webprotege.common.ProjectId;
import edu.stanford.protege.webprotege.common.UserId;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProjectHistoryGenerator {

    private final Logger logger = LoggerFactory.getLogger(ProjectHistoryGenerator.class);

    private final OntologyHistoryAnalyzer ontologyHistoryAnalyzer;

    private final ProjectHistoryStorer projectHistoryStorer;

    public ProjectHistoryGenerator(
            @Nonnull OntologyHistoryAnalyzer ontologyHistoryAnalyzer,
            @Nonnull ProjectHistoryStorer projectHistoryStorer) {
        this.ontologyHistoryAnalyzer = ontologyHistoryAnalyzer;
        this.projectHistoryStorer = projectHistoryStorer;
    }

    /**
     * Generates and stores the project history for a specified ontology file from a GitHub
     * repository.
     *
     * <p>This method retrieves the commit history for the target ontology file from the specified
     * GitHub repository, processes it into a project history document, and stores it in a blob
     * location.
     *
     * @param userId the unique identifier of the WebProtege user
     * @param projectId the unique identifier of the ontology project in WebProtege
     * @param branchCoordinates the GitHub repository specification
     * @param rootOntologyPath the ontology file in the repository to generate the history for
     * @return a {@link BlobLocation} indicating where the project history document has been stored
     * @throws StorageException if an error occurs during repository access, history analysis, or
     *     storage operations. This may include network issues, authentication failures, file access
     *     problems, or serialization errors
     */
    public BlobLocation writeProjectHistoryFromGitHubRepo(
            UserId userId,
            ProjectId projectId,
            BranchCoordinates branchCoordinates,
            RelativeFilePath rootOntologyPath,
            OntologyHistoryAnalyzerProgressMonitor progressMonitor) {
        try {
            var localCloneDirectory = getLocalCloneDirectory(projectId, branchCoordinates);
            var repository = getGitHubRepository(branchCoordinates, localCloneDirectory, rootOntologyPath);
            var projectHistory = ontologyHistoryAnalyzer.getCommitHistory(rootOntologyPath, repository, progressMonitor);
            var projectHistoryLocation = projectHistoryStorer.storeProjectHistory(projectId, projectHistory);
            logger.info("Stored project history document at: {}", projectHistoryLocation);
            return projectHistoryLocation;
        } catch (IOException | GitHubNavigatorException | OntologyComparisonException e) {
            throw new StorageException("Problem writing project history document to storage", e);
        }
    }

    private Path getLocalCloneDirectory(ProjectId projectId, BranchCoordinates branchCoordinates) throws IOException {
        var tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        var subDir = Path.of("github-repos", projectId.value(), branchCoordinates.ownerName(), branchCoordinates.repositoryName());
        Files.createDirectories(subDir);
        return tempDir.resolve(subDir);
    }

    private GitHubRepository getGitHubRepository(
            BranchCoordinates branchCoordinates, Path localCloneDirectory, RelativeFilePath rootOntologyPath)
            throws GitHubNavigatorException {
        var repository = GitHubRepositoryBuilderFactory.create(branchCoordinates)
                .localWorkingDirectory(localCloneDirectory)
                .build();
        repository.initialize();
        return repository;
    }
}
