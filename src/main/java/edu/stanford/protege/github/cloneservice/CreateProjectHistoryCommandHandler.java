package edu.stanford.protege.github.cloneservice;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.GitHubRepositoryBuilderFactory;
import edu.stanford.protege.commitnavigator.exceptions.GitHubNavigatorException;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.github.cloneservice.exception.OntologyComparisonException;
import edu.stanford.protege.github.cloneservice.message.*;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.service.ProjectHistoryStorer;
import edu.stanford.protege.github.cloneservice.utils.OntologyHistoryAnalyzer;
import edu.stanford.protege.github.cloneservice.utils.OntologyHistoryAnalyzerProgressMonitor;
import edu.stanford.protege.webprotege.common.*;
import edu.stanford.protege.webprotege.ipc.CommandHandler;
import edu.stanford.protege.webprotege.ipc.EventDispatcher;
import edu.stanford.protege.webprotege.ipc.ExecutionContext;
import edu.stanford.protege.webprotege.ipc.WebProtegeHandler;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.NestedExceptionUtils;
import reactor.core.publisher.Mono;

@WebProtegeHandler
public class CreateProjectHistoryCommandHandler
        implements CommandHandler<CreateProjectHistoryRequest, CreateProjectHistoryResponse> {

    private static final Logger logger = LoggerFactory.getLogger(CreateProjectHistoryCommandHandler.class);

    private final OntologyHistoryAnalyzer ontologyHistoryAnalyzer;
    private final ProjectHistoryStorer projectHistoryStorer;
    private final EventDispatcher eventDispatcher;
    private final Executor projectHistoryCreationExecutor;

    public CreateProjectHistoryCommandHandler(
            @Nonnull OntologyHistoryAnalyzer ontologyHistoryAnalyzer,
            @Nonnull ProjectHistoryStorer projectHistoryStorer,
            @Nonnull EventDispatcher eventDispatcher,
            @Qualifier("projectHistoryCreationExecutor") @Nonnull Executor projectHistoryCreationExecutor) {
        this.ontologyHistoryAnalyzer = ontologyHistoryAnalyzer;
        this.projectHistoryStorer = projectHistoryStorer;
        this.eventDispatcher = eventDispatcher;
        this.projectHistoryCreationExecutor = projectHistoryCreationExecutor;
    }

    @NotNull @Override
    public String getChannelName() {
        return CreateProjectHistoryRequest.CHANNEL;
    }

    @Override
    public Class<CreateProjectHistoryRequest> getRequestClass() {
        return CreateProjectHistoryRequest.class;
    }

    @Override
    public Mono<CreateProjectHistoryResponse> handleRequest(
            CreateProjectHistoryRequest request, ExecutionContext executionContext) {

        var operationId = CreateProjectHistoryOperationId.generate();

        var userId = executionContext.userId();

        var projectId = request.projectId();
        var branchCoordinates = request.branchCoordinates();
        var rootOntologyPath = request.rootOntologyPath();

        startAsyncProcessing(userId, projectId, operationId, branchCoordinates, rootOntologyPath);

        return Mono.just(new CreateProjectHistoryResponse(projectId, operationId, branchCoordinates));
    }

    private void startAsyncProcessing(
            UserId userId,
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            BranchCoordinates branchCoordinates,
            RelativeFilePath rootOntologyPath) {

        cloneRepositoryAsync(userId, projectId, operationId, branchCoordinates)
                .whenComplete((repository, t) -> {
                    if (t == null) {
                        var eventId = EventId.generate();
                        logger.info(
                                "{} {} {} Successfully cloned repository {}",
                                projectId,
                                operationId,
                                eventId,
                                branchCoordinates.repositoryUrl());
                        fireCloneSucceeded(projectId, operationId, eventId, branchCoordinates, repository);
                    }
                })
                .thenComposeAsync(repository ->
                        generateProjectHistory(projectId, operationId, rootOntologyPath, branchCoordinates, repository), projectHistoryCreationExecutor)
                .whenComplete((projectHistory, t) -> {
                    if (t == null) {
                        logger.info(
                                "{} {} Successfully extracted ontology changes from file {}",
                                projectId,
                                operationId,
                                rootOntologyPath);
                        fireGenerateSucceeded(projectId, operationId, branchCoordinates);
                    }
                })
                .thenComposeAsync(projectHistory ->
                        storeProjectHistory(projectId, operationId, branchCoordinates, projectHistory), projectHistoryCreationExecutor)
                .whenComplete((documentLocation, t) -> {
                    if (t == null) {
                        logger.info(
                                "{} {} Successfully stored project history at location {}",
                                projectId,
                                operationId,
                                documentLocation);
                        fireStoreSucceeded(projectId, operationId, branchCoordinates);
                        fireCreateProjectHistoryFromGitHubRepoSucceeded(operationId, projectId, documentLocation);
                    } else {
                        logger.info("{} {} Failed to create project history {}", operationId, projectId, t.getMessage());
                        // Terminal overarching error
                        fireCreateProjectHistoryFromGitHubRepoFailed(operationId, projectId, t);
                    }
                });
    }

    private CompletableFuture<GitHubRepository> cloneRepositoryAsync(
            UserId userId,
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            BranchCoordinates branchCoordinates) {

        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        logger.info(
                                "{} {} Starting repository clone {}",
                                projectId,
                                operationId,
                                branchCoordinates.repositoryUrl());
                        var workingDirectory = getLocalWorkingDirectory(projectId, branchCoordinates);
                        return cloneGitHubRepository(branchCoordinates, workingDirectory);
                    } catch (Exception e) {
                        logger.error(
                                "{} {} Failed to clone GitHub repository {}",
                                projectId,
                                operationId,
                                branchCoordinates.repositoryUrl(),
                                e);
                        fireCloneFailed(projectId, operationId, EventId.generate(), branchCoordinates, e);
                        throw new RuntimeException("Failed to clone repository", e);
                    }
                },
                projectHistoryCreationExecutor);
    }

    private CompletableFuture<List<OntologyCommitChange>> generateProjectHistory(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            RelativeFilePath rootOntologyPath,
            BranchCoordinates branchCoordinates,
            GitHubRepository repository) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        logger.info(
                                "{} {} Starting ontology change extraction from file {}",
                                projectId,
                                operationId,
                                rootOntologyPath);
                        return ontologyHistoryAnalyzer.getCommitHistory(rootOntologyPath, repository, new OntologyHistoryAnalyzerProgressMonitor() {
                            @Override
                            public void processingStarted(CommitMetadata commitMetadata) {
                                eventDispatcher.dispatchEvent(new CommitProcessingStartedEvent(EventId.generate(), operationId, projectId, commitMetadata.commitHash()));
                            }

                            @Override
                            public void processingFinished(CommitMetadata commitMetadata) {

                            }
                        });
                    } catch (OntologyComparisonException e) {
                        logger.error(
                                "{} {} Failed to extract ontology changes from file {}",
                                projectId,
                                operationId,
                                rootOntologyPath,
                                e);
                        fireGenerateFailed(projectId, operationId, EventId.generate(), branchCoordinates, e);
                        throw new RuntimeException("Failed to extract ontology change", e);
                    }
                },
                projectHistoryCreationExecutor);
    }

    private CompletableFuture<BlobLocation> storeProjectHistory(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            BranchCoordinates branchCoordinates,
            List<OntologyCommitChange> projectHistory) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        logger.info("{} {} Starting project history store", projectId, operationId);
                        return projectHistoryStorer.storeProjectHistory(projectId, projectHistory);
                    } catch (Exception e) {
                        logger.error("{} {} Failed to store project history", projectId, operationId, e);
                        fireStoreFailed(projectId, operationId, branchCoordinates, e);
                        throw new RuntimeException(e);
                    }
                },
                projectHistoryCreationExecutor);
    }

    private Path getLocalWorkingDirectory(ProjectId projectId, BranchCoordinates branchCoordinates) {
        var tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        var subDir = Path.of("github-repos", projectId.value(), branchCoordinates.ownerName(), branchCoordinates.repositoryName());
        return tempDir.resolve(subDir);
    }

    private GitHubRepository cloneGitHubRepository(BranchCoordinates branchCoordinates, Path workingDirectory)
            throws GitHubNavigatorException {
        var repository = GitHubRepositoryBuilderFactory.create(branchCoordinates)
                .localWorkingDirectory(workingDirectory)
                .build();
        repository.initialize();
        return repository;
    }

    private void fireCloneFailed(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            EventId eventId,
            BranchCoordinates branchCoordinates,
            Throwable t) {
        var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(t);
        eventDispatcher.dispatchEvent(
                new CloneRepositoryFailedEvent(projectId, operationId, eventId, branchCoordinates, mostSpecificCause.getMessage()));
    }

    private void fireCloneSucceeded(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            EventId eventId,
            BranchCoordinates branchCoordinates,
            GitHubRepository repository) {
        eventDispatcher.dispatchEvent(
                new CloneRepositorySucceededEvent(projectId, operationId, eventId, branchCoordinates));
    }

    private void fireGenerateFailed(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            EventId eventId,
            BranchCoordinates branchCoordinates,
            Throwable t) {
        var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(t);
        eventDispatcher.dispatchEvent(new GenerateProjectHistoryFailedEvent(
                projectId, operationId, eventId, branchCoordinates, mostSpecificCause.getMessage()));
    }

    private void fireGenerateSucceeded(
            ProjectId projectId, CreateProjectHistoryOperationId operationId, BranchCoordinates branchCoordinates) {
        eventDispatcher.dispatchEvent(new GenerateProjectHistorySucceededEvent(
                projectId, operationId, EventId.generate(), branchCoordinates));
    }

    private void fireStoreFailed(
            ProjectId projectId,
            CreateProjectHistoryOperationId operationId,
            BranchCoordinates branchCoordinates,
            Throwable t) {
        var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(t);
        eventDispatcher.dispatchEvent(new StoreProjectHistoryFailedEvent(
                projectId, operationId, EventId.generate(), branchCoordinates, mostSpecificCause.getMessage()));
    }

    private void fireStoreSucceeded(
            ProjectId projectId, CreateProjectHistoryOperationId operationId, BranchCoordinates branchCoordinates) {
        eventDispatcher.dispatchEvent(
                new StoreProjectHistorySucceededEvent(projectId, operationId, EventId.generate(), branchCoordinates));
    }

    private void fireCreateProjectHistoryFromGitHubRepoFailed(
            CreateProjectHistoryOperationId operationId, ProjectId projectId, Throwable t) {
        logger.info("{} {} Firing CreateProjectHistoryFailedEvent: {}", projectId, operationId, t.getMessage());
        var mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(t);
        eventDispatcher.dispatchEvent(
                new CreateProjectHistoryFailedEvent(EventId.generate(), operationId, projectId, mostSpecificCause.getMessage()));
    }

    private void fireCreateProjectHistoryFromGitHubRepoSucceeded(
            CreateProjectHistoryOperationId operationId, ProjectId projectId, BlobLocation documentLocation) {
        eventDispatcher.dispatchEvent(
                new CreateProjectHistorySucceededEvent(EventId.generate(), operationId, projectId, documentLocation));
    }
}
