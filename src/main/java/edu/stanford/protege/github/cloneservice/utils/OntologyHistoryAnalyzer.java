package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import edu.stanford.protege.commitnavigator.CommitNavigatorBuilder;
import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.exceptions.RepositoryException;
import edu.stanford.protege.commitnavigator.model.ChangedFile;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.CommitNavigator;
import edu.stanford.protege.github.cloneservice.exception.OntologyComparisonException;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.webprotege.change.OntologyChange;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Main service for analyzing ontology history across Git commits */
@Component
public class OntologyHistoryAnalyzer {

    public static final String[] INCLUDE_FILTER_PATTERNS = new String[]{"**/*.owl" , "**/*.obo" , "**/*.ofn" , "**/*.ttl" , "**/*.rdf" , "**/*.owx"};

    public static final String[] EXCLUDE_FILTER_PATTERNS = new String[]{"**/bridge/**" , "**/*-idranges.owl"};

    private static final Logger logger = LoggerFactory.getLogger(OntologyHistoryAnalyzer.class);

    private final OntologyLoader ontologyLoader;

    private final OntologyDifferencesCalculator differenceCalculator;

    private final Duration maxAnalysisDuration;

    private final OntologyManagerProvider ontologyManagerProvider;

    private final List<PathMatcher> pathMatchers = new ArrayList<>();

    private final List<PathMatcher> pathExcludeMatchers = new ArrayList<>();

    @Inject
    public OntologyHistoryAnalyzer(OntologyLoader ontologyLoader,
                                   OntologyDifferencesCalculator differenceCalculator,
                                   @Value("${webprotege.github.max-analysis-time:5m}")
                                       Duration maxAnalysisDuration, OntologyManagerProvider ontologyManagerProvider) {
        this.ontologyLoader = Objects.requireNonNull(ontologyLoader, "OntologyLoader cannot be null");
        this.differenceCalculator =
                Objects.requireNonNull(differenceCalculator, "OntologyDifferencesCalculator cannot be null");
        this.maxAnalysisDuration = maxAnalysisDuration;
        this.ontologyManagerProvider = ontologyManagerProvider;
        for(var filterPattern : INCLUDE_FILTER_PATTERNS) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
        for(var filterPattern : EXCLUDE_FILTER_PATTERNS) {
            pathExcludeMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
    }

    public static OntologyHistoryAnalyzer withDefaultMaxAnalysisTime(OntologyLoader ontologyLoader, OntologyDifferencesCalculator differenceCalculator) {
        return new OntologyHistoryAnalyzer(ontologyLoader, differenceCalculator, Duration.ofMinutes(10), new OntologyManagerProvider());
    }

    private static void safeResetRepository(GitHubRepository repository) {
        try {
            var workingDirectory = repository.getWorkingDirectory();
            CommitNavigator cm = CommitNavigatorBuilder.forWorkingDirectory(workingDirectory).build();
            cm.checkoutCommitAt(0);
        } catch(Exception e) {
            // best effort
            logger.warn("Working directory could not be reset", e);
        }
    }

    private static void logAxiomChanges(List<OntologyChange> axiomChanges) {
        logger.info("Total number of axiom changes: {}" , axiomChanges.size());
    }

    private static Optional<String> getCheckedOutBlobIdForPath(Path path, CommitNavigator commitNavigator) {
        try {
            var relativePath = commitNavigator.relativize(path);
            var currentCommit = commitNavigator.getCurrentCommit().orElseThrow();
            return commitNavigator.getBlobId(relativePath.toString(), currentCommit.commitHash());
        } catch(RepositoryException e) {
            logger.warn("Could not resolve blob id" );
            return Optional.empty();
        }
    }

    private static Optional<String> getBlobIdForPath(Path path, CommitNavigator commitNavigator, int index) {
        try {
            var relativePath = commitNavigator.relativize(path);
            var currentCommit = commitNavigator.getCommitAt(index);
            return commitNavigator.getBlobId(relativePath.toString(), currentCommit.commitHash());
        } catch(RepositoryException e) {
            logger.warn("Could not resolve blob id" );
            return Optional.empty();
        }
    }
    private void recordProcessingStarted(@NotNull OntologyHistoryAnalyzerProgressMonitor progressMonitor, CommitMetadata commitMetadata, List<ChangedFile> changedFiles) {
        if(commitMetadata != null) {
            var files = changedFiles.stream().map(ChangedFile::path).toList();
            progressMonitor.processingCommitStarted(commitMetadata, files);
        }
    }

    private void recordProcessingFinished(@NotNull OntologyHistoryAnalyzerProgressMonitor progressMonitor, CommitMetadata commitMetadata) {
        if(commitMetadata != null) {
            progressMonitor.processingCommitFinished(commitMetadata);
        }
    }

    private List<ChangedFile> getFilteredChangedFiles(CommitMetadata commitMetadata) {
        return commitMetadata.getChangedFiles().stream()
                .filter(f -> matchesFilters(f.path()))
                .collect(Collectors.toList());
    }

    /**
     * Analyzes ontology history across all consecutive commits from HEAD backwards
     *
     * @param ontologyFilePath The name of the ontology file to analyze
     * @param gitHubRepository The GitHub repository where all commits are stored
     * @return List of all ontology changes across commit history
     * @throws OntologyComparisonException if analysis fails
     */
    @Nonnull
    public List<OntologyCommitChange> getCommitHistory(
            @Nonnull RelativeFilePath ontologyFilePath,
            @Nonnull GitHubRepository gitHubRepository,
            @Nonnull OntologyHistoryAnalyzerProgressMonitor progressMonitor,
            @Nonnull OntologyChangesHandler ontologyChangesHandler)
            throws OntologyComparisonException {

            Objects.requireNonNull(ontologyFilePath, "ontologyFilePath cannot be null" );
            Objects.requireNonNull(gitHubRepository, "gitHubRepository cannot be null" );

            logger.info("Starting ontology commit history analysis for ontology file: {}.  Max analysis time: {}" , ontologyFilePath, maxAnalysisDuration);

            var repositoryUrl = gitHubRepository.getConfig().getRepositoryUrl();
            var allCommitChanges = Lists.<OntologyCommitChange>newArrayList();

            try {
                // Get the working directory from the repository
                var gitDirectory = gitHubRepository.getWorkingDirectory();

                // Configure commit navigator to focus on the target ontology file
                var rootOntologyFile = ontologyFilePath.asString();
                var commitNavigator = CommitNavigatorBuilder.forWorkingDirectory(gitDirectory)
                        .withRequiredRepoPath(rootOntologyFile)
                        .fileFilters(INCLUDE_FILTER_PATTERNS)
                        .build();

                var startTime = Instant.now();


                var window = new CommitWindow(new UncheckedCommitNavigator(commitNavigator));

                var cache = new LoadedOntologyCache((path) -> {
                    return getCheckedOutBlobIdForPath(path, commitNavigator);
                });

                var oboDocCache = new LoadedOboDocCache((path, index) -> {
                    return getBlobIdForPath(path, commitNavigator, index);
                });


                var fsDocCache = new LoadedFsDocCache((path, index) -> {
                    return getBlobIdForPath(path, commitNavigator, index);
                });


                var differencesCalculator = new OntologiesDifferenceCalculator(differenceCalculator, ontologyLoader);

                var strategy = Map.of(
                        PlanKind.NO_RELEVANT_CHANGE, new NoChangeExecutor(),
                        PlanKind.ROOT_OBO_SINGLE_FILE, new OboSingleFileExecutor(ontologyManagerProvider),
                        PlanKind.ROOT_OFN_SINGLE_FILE, new OfnSingleFileExecutor(ontologyManagerProvider),
                        PlanKind.ROOT_OWL_SINGLE_FILE, new RootOwlSingleFileExecutor(ontologyLoader),
                        PlanKind.MULTI_FILE_OR_IMPORTS, new MultiFileExecutor(ontologyLoader),
                        PlanKind.OLDEST_COMMIT, new OldestCommitExecutor(ontologyLoader)
                );

                var fallback = new FallbackStrategy(ontologyLoader, differencesCalculator);

                progressMonitor.processingHistoryStarted(repositoryUrl, commitNavigator.getNumberOfCommits());

                while(window.hasBaselineCommit()) {
                    var baseline = commitNavigator.getCommitAt(window.getBaselineIndex());
                    var filtered = getFilteredChangedFiles(baseline);
                    var plan = selectDiffPlan(commitNavigator, window, rootOntologyFile, filtered);

                    recordProcessingStarted(progressMonitor, baseline, filtered);
                    List<OntologyChange> axiomChanges;
                    try {
                        try {
                            var exec = strategy.get(plan.kind());
                            var pair = exec.loadOntologies(plan, cache, window, oboDocCache, fsDocCache);
                            axiomChanges = differencesCalculator.calculateAxiomChangesBetweenOntologies(pair.baseline(), pair.ancestor());
                        } catch(SingleRootOntologyImportsMismatchException e) {
                            logger.info("Imports declaration changed in commit.  Doing a full reload. Commit: {}" , baseline.commitHash());
                            var pair = strategy.get(PlanKind.MULTI_FILE_OR_IMPORTS).loadOntologies(plan, cache, window, oboDocCache, fsDocCache);
                            axiomChanges = differencesCalculator.calculateAxiomChangesBetweenOntologies(pair.baseline(), pair.ancestor());
                        }
                        var ancestor = window.getAncestorCommit();
                        ontologyChangesHandler.handleOntologyChanges(baseline, ancestor, axiomChanges);
                    } catch(Throwable e) {
                        logger.info("Could not load ontology changes [baselineCommit={}]", baseline.commitHash(), e);
                        var rootOntologyPath = commitNavigator.resolveFilePath(rootOntologyFile);
                        axiomChanges = fallback.fallback(rootOntologyPath, cache, window);
                        var ancestor = window.getAncestorCommit();
                        ontologyChangesHandler.handleOntologyChanges(baseline, ancestor, axiomChanges);
                    } finally {
                        recordProcessingFinished(progressMonitor, baseline);
                    }

                    logAxiomChanges(axiomChanges);

                    allCommitChanges.add(new OntologyCommitChange(axiomChanges, baseline, window.getAncestorCommit()));

                    if(Duration.between(startTime, Instant.now()).compareTo(maxAnalysisDuration) > 0) {
                        logger.info("Time budget exceeded at {}" , baseline.commitHash());
                        break;
                    }

                    window.advanceBaselineToAncestor();
                }


                return ImmutableList.copyOf(allCommitChanges);
            } catch(Exception e) {
                logger.error("An error occurred when analyzing the commit history" , e);
                throw new OntologyComparisonException("Failed to analyze ontology commit history" , e);
            } finally {
                // Ensure repo state is restored even on failure (best-effort)
                safeResetRepository(gitHubRepository);
                progressMonitor.processingHistoryFinished();
            }
    }

    private boolean matchesFilters(String path) {
        var excluded = pathExcludeMatchers.stream()
                .anyMatch(matcher -> matcher.matches(Path.of(path)));
        if(excluded) {
            return false;
        }
        return pathMatchers.stream()
                .anyMatch(matcher -> matcher.matches(Path.of(path)));
    }

    private boolean isRootOntologyChangeOnly(CommitNavigator commitNavigator,
                                             CommitMetadata baselineCommitMetadata,
                                             String rootOntologyRepoPath) {
        var rootOntologyPath = commitNavigator.resolveFilePath(rootOntologyRepoPath);
        if(!Files.exists(rootOntologyPath)) {
            return false;
        }
        var filteredChangedFiles = getFilteredChangedFiles(baselineCommitMetadata);
        if(filteredChangedFiles.size() != 1) {
            return false;
        }
        var changedFile = filteredChangedFiles.getFirst();
        return changedFile.path().equals(rootOntologyRepoPath);
    }

    ////////////////////////////////////////////////////////////////////////////

    private DiffPlan selectDiffPlan(CommitNavigator nav,
                                    CommitWindow window,
                                    String rootRepoPath,
                                    List<ChangedFile> filteredChangedFiles) {

        var rootPath = nav.resolveFilePath(rootRepoPath);

        var baselineCommit = nav.getCommitAt(window.getBaselineIndex());
        if(!window.hasAncestorCommit()) {
            return new DiffPlan(PlanKind.OLDEST_COMMIT, rootPath, rootRepoPath);
        }

        if(filteredChangedFiles.isEmpty()) {
            return new DiffPlan(PlanKind.NO_RELEVANT_CHANGE, rootPath, rootRepoPath);
        }

        if(isRootOntologyChangeOnly(nav, baselineCommit, rootRepoPath) && Files.exists(rootPath)) {
            if(isObo(rootRepoPath)) {
                return new DiffPlan(PlanKind.ROOT_OBO_SINGLE_FILE, rootPath, rootRepoPath);
            }
            if(isOfn(rootPath)) {
                return new DiffPlan(PlanKind.ROOT_OFN_SINGLE_FILE, rootPath, rootRepoPath);
            }
            return new DiffPlan(PlanKind.ROOT_OWL_SINGLE_FILE, rootPath, rootRepoPath);
        }

        return new DiffPlan(PlanKind.MULTI_FILE_OR_IMPORTS, rootPath, rootRepoPath);
    }

    private boolean isObo(String p) {
        return p.toLowerCase(Locale.ROOT).endsWith(".obo" );
    }

    private boolean isOfn(Path p) {
        return OfnFileDetector.isOwlFunctionalSyntax(p);
    }

    enum PlanKind {
        NO_RELEVANT_CHANGE,          // filteredChangedFiles empty
        ROOT_OBO_SINGLE_FILE,        // only root changed, *.obo
        ROOT_OFN_SINGLE_FILE,        // only root changed, OFN
        ROOT_OWL_SINGLE_FILE,        // only root changed, general OWL
        MULTI_FILE_OR_IMPORTS,       // otherwise
        OLDEST_COMMIT               // last step when no ancestor
    }

}
