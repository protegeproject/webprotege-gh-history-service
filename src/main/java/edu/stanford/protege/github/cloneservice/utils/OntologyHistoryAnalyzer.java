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
import edu.stanford.protege.github.cloneservice.model.AxiomChange;
import edu.stanford.protege.github.cloneservice.model.OntologyCommitChange;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import org.jetbrains.annotations.NotNull;
import org.semanticweb.owlapi.functional.parser.OWLFunctionalSyntaxOWLParserFactory;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

    private final OntologiesDifferenceCalculator differenceCalculator;

    private final Duration maxAnalysisDuration;

    private final OntologyManagerProvider ontologyManagerProvider;

    private final List<PathMatcher> pathMatchers = new ArrayList<>();

    private final List<PathMatcher> pathExcludeMatchers = new ArrayList<>();

    @Inject
    public OntologyHistoryAnalyzer(OntologyLoader ontologyLoader,
                                   OntologiesDifferenceCalculator differenceCalculator,
                                   @Value("${webprotege.github.max-analysis-time:5m}")
                                       Duration maxAnalysisDuration, OntologyManagerProvider ontologyManagerProvider) {
        this.ontologyLoader = Objects.requireNonNull(ontologyLoader, "OntologyLoader cannot be null");
        this.differenceCalculator =
                Objects.requireNonNull(differenceCalculator, "OntologiesDifferenceCalculator cannot be null");
        this.maxAnalysisDuration = maxAnalysisDuration;
        this.ontologyManagerProvider = ontologyManagerProvider;
        for(var filterPattern : INCLUDE_FILTER_PATTERNS) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
        for(var filterPattern : EXCLUDE_FILTER_PATTERNS) {
            pathExcludeMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
    }

    public OntologyHistoryAnalyzer(OntologyLoader ontologyLoader, OntologiesDifferenceCalculator differenceCalculator) {
        this(ontologyLoader, differenceCalculator, Duration.ofMinutes(10), new OntologyManagerProvider());
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

    private static void logAxiomChanges(List<AxiomChange> axiomChanges) {
        logger.info("Total number of axiom changes: {}" , axiomChanges.size());
    }

    private static void forceFunctionalSyntaxParsing(OWLOntologyManager baselineManager) {
        var baselineParsers = baselineManager.getOntologyParsers();
        baselineParsers.clear();
        baselineParsers.add(new OWLFunctionalSyntaxOWLParserFactory());
    }

    private static IRI ontologyKey(OWLOntology ont) {
        var id = ont.getOntologyID();
        return id.getOntologyIRI()
                .or(() -> id.getDefaultDocumentIRI().or(IRI.generateDocumentIRI()));
    }

    private static OWLOntologyID effectiveOntologyId(OWLOntology baseline, OWLOntology parent) {
        var cid = baseline.getOntologyID();
        if(!cid.isAnonymous()) {
            return cid;
        }
        var pid = parent.getOntologyID();
        if(!pid.isAnonymous()) {
            return pid;
        }
        // Fallback: derive from the baseline key to keep stable-ish identity
        var iri = baseline.getOntologyID().getDefaultDocumentIRI()
                .or(IRI::generateDocumentIRI);
        return new OWLOntologyID(iri, null);
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

    private static @NotNull Set<OWLImportsDeclaration> getOwlImportsDeclarations(List<OWLOntology> baselineOnts) {
        return baselineOnts.stream().flatMap(o -> o.getImportsDeclarations().stream()).collect(Collectors.toSet());
    }

    private void recordProcessingStarted(@NotNull OntologyHistoryAnalyzerProgressMonitor progressMonitor, CommitMetadata commitMetadata) {
        if(commitMetadata != null) {
            progressMonitor.processingCommitStarted(commitMetadata);
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
            @Nonnull OntologyHistoryAnalyzerProgressMonitor progressMonitor)
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


                var window = new CommitWindow(commitNavigator);

                var cache = new LoadedOntologyCache((path) -> {
                    return getCheckedOutBlobIdForPath(path, commitNavigator);
                });

                var oboDocCache = new LoadedOboDocCache((path, index) -> {
                    return getBlobIdForPath(path, commitNavigator, index);
                });


                var fsDocCache = new LoadedFsDocCache((path, index) -> {
                    return getBlobIdForPath(path, commitNavigator, index);
                });


                var differencesCalculator = new OntologiesDifferenceCalaculator(differenceCalculator, ontologyLoader);

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

                    recordProcessingStarted(progressMonitor, baseline);
                    List<AxiomChange> axiomChanges;
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
                    } catch(Throwable e) {
                        var rootOntologyPath = commitNavigator.resolveFilePath(rootOntologyFile);
                        axiomChanges = fallback.fallback(rootOntologyPath, cache, window);
                    } finally {
                        recordProcessingFinished(progressMonitor, baseline);
                    }

                    logAxiomChanges(axiomChanges);
                    allCommitChanges.add(new OntologyCommitChange(axiomChanges, baseline, repositoryUrl));

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

    private interface PlanExecutor {

        LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception;
    }

    private record OntologyPair(OWLOntology baseline, OWLOntology parent) {

    }

    private static class OntologiesDifferenceCalaculator {

        private final OntologiesDifferenceCalculator differenceCalculator;

        private final OntologyLoader ontologyLoader;

        private OntologiesDifferenceCalaculator(OntologiesDifferenceCalculator differenceCalculator, OntologyLoader ontologyLoader) {
            this.differenceCalculator = differenceCalculator;
            this.ontologyLoader = ontologyLoader;
        }

        /**
         * Calculates axiom changes between current and previous commit ontologies
         *
         * @param baselineCommitOntologies ontologies from the baseline commit
         * @param parentCommitOntologies   ontologies from the ancestor commit
         * @return list of axiom changes between commits
         */
        @Nonnull
        private List<AxiomChange> calculateAxiomChangesBetweenOntologies(
                @Nonnull List<OWLOntology> baselineCommitOntologies,
                @Nonnull List<OWLOntology> parentCommitOntologies) {

            var baselinesByIri = new HashMap<IRI, OWLOntology>();
            var ancestorsByIri = new HashMap<IRI, OWLOntology>();

            baselineCommitOntologies.forEach(ont -> baselinesByIri.put(ontologyKey(ont), ont));
            parentCommitOntologies.forEach(ont -> ancestorsByIri.put(ontologyKey(ont), ont));

            var pairs = new ArrayList<OntologyPair>();

            // 1) Matched pairs (remove matched keys from both maps)
            var matchedKeys = new HashSet<>(baselinesByIri.keySet());
            matchedKeys.retainAll(ancestorsByIri.keySet());
            for(var iri : matchedKeys) {
                pairs.add(new OntologyPair(baselinesByIri.remove(iri), ancestorsByIri.remove(iri)));
            }

            // 2) Parent-only → removed; baseline-only → added
            ancestorsByIri.values().forEach(p -> pairs.add(new OntologyPair(ontologyLoader.getEmptyOntology(), p)));
            baselinesByIri.values().forEach(c -> pairs.add(new OntologyPair(c, ontologyLoader.getEmptyOntology())));

            return pairs.stream()
                    .flatMap(pair -> differenceCalculator
                            .calculateAxiomChanges(
                                    pair.baseline,
                                    pair.parent,
                                    // Prefer baseline’s real ID if not anonymous; else use ancestor’s; else synthesize
                                    effectiveOntologyId(pair.baseline, pair.parent))
                            .stream())
                    .collect(ImmutableList.toImmutableList());
        }
    }

    private static class FallbackStrategy {

        private final OntologyLoader loader;

        private final OntologiesDifferenceCalaculator differenceCalaculator;

        public FallbackStrategy(OntologyLoader loader, OntologiesDifferenceCalaculator differenceCalaculator) {
            this.loader = loader;
            this.differenceCalaculator = differenceCalaculator;
        }

        private List<AxiomChange> fallback(
                Path rootOntologyPath,
                LoadedOntologyCache cache,
                CommitWindow commitWindow) {

            try {
                // We are either unable to load the current baseline or we
                // are unable to load the current ancestor.
                // Back track to the last loaded commit - this may be the current baseline
                commitWindow.setBaselineToLastLoadedCommit();
                while(commitWindow.hasBaselineCommit()) {
                    // Ensure we are at the baseline commit
                    try {
                        var baselineCommit = commitWindow.checkoutBaseline();
                        logger.info("Fallingback to baseline commit {}" , baselineCommit.commitHash());
                        var baselineOntologies = loader.loadOntologyWithImports(rootOntologyPath, cache);

                        while(commitWindow.hasAncestorCommit()) {
                            try {
                                var ancestorCommit = commitWindow.checkoutAncestor();
                                var ancestorOntologies = loader.loadOntologyWithImports(rootOntologyPath, cache);
                                return differenceCalaculator.calculateAxiomChangesBetweenOntologies(
                                        baselineOntologies, ancestorOntologies);
                            } catch(Throwable e) {
                                logger.warn("Could not process ancestor commit: {} (baseline commit: {})" ,
                                        commitWindow.getAncestorCommitHash(),
                                        baselineCommit.commitHash());
                                commitWindow.incrementAncestorOffset();
                                logger.info("Advanced ancestor commit to {}" , commitWindow.getAncestorIndex());
                            }
                        }
                    } catch(Throwable e) {
                        // Could not process baseline commit
                        logger.error("Could not process baseline commit: {}.  Advancing baseline." ,
                                commitWindow.getBaselineCommitHash());
                        commitWindow.advanceBaselineToAncestor();
                    }
                }
            } catch(Throwable t) {
                logger.error("Could not find a loadable commit pair." );
            }
            return List.of();
        }
    }

    record DiffPlan(
            PlanKind kind,
            Path rootOntologyPath,
            String rootRepoPath
    ) {

    }

    private record LoadedPair(List<OWLOntology> baseline, List<OWLOntology> ancestor) {

    }

    private static class OfnSingleFileExecutor implements PlanExecutor {

        private final OntologyManagerProvider ontologyManagerProvider;

        public OfnSingleFileExecutor(OntologyManagerProvider ontologyManagerProvider) {
            this.ontologyManagerProvider = ontologyManagerProvider;
        }

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            try {
                logger.info("Functional Syntax single file diff.  Doing optimized loading and change calculation for commit: {}" , commitWindow.getBaselineCommitHash());

                var rootOntologyPath = plan.rootOntologyPath();

                var docSource = new FileDocumentSource(rootOntologyPath.toFile());

                var afterDoc = fsDocCache.get(docSource, commitWindow.getBaselineIndex()).orElseGet(() -> {
                    try {
                        var baselineContent = commitWindow.getContentAtBaseline(plan.rootRepoPath());
                        logger.info("Loaded chars {} from blob at path: {}", baselineContent.length(), plan.rootRepoPath());
                        var doc = new FsParser(new StringReader(baselineContent)).parse();
                        fsDocCache.put(docSource, doc, commitWindow.getBaselineIndex());
                        return doc;
                    } catch(Throwable e) {
                        throw new RuntimeException(e);
                    }
                });

                var beforeDoc = fsDocCache.get(docSource, commitWindow.getAncestorIndex()).orElseGet(() -> {
                    try {
                        var parentContent = commitWindow.getContentAtAncestor(plan.rootRepoPath());
                        logger.info("Loaded chars {} from blob at path: {}", parentContent.length(), plan.rootRepoPath());
                        var doc = new FsParser(new StringReader(parentContent)).parse();
                        fsDocCache.put(docSource, doc, commitWindow.getAncestorIndex());
                        return doc;
                    } catch(Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
                var diff = MinimalOfnDiff.build(beforeDoc, afterDoc);

                var minimalParentDoc = new MinimalOfnDiff.Renderer().render(diff.before);
                var minimalbaselineDoc = new MinimalOfnDiff.Renderer().render(diff.after);

                try {
                    var baselineManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                    forceFunctionalSyntaxParsing(baselineManager);
                    var baselineOnt = baselineManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalbaselineDoc));

                    try {

                        var parentManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                        forceFunctionalSyntaxParsing(parentManager);
                        var parentOnt = parentManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalParentDoc));

                        if(!baselineOnt.getImportsDeclarations().equals(parentOnt.getImportsDeclarations())) {
                            throw new SingleRootOntologyImportsMismatchException();
                        }

                        return new LoadedPair(List.of(baselineOnt), List.of(parentOnt));
                    } catch(Exception e) {
                        logger.info("Error when minimal-diff baseline ontology: Commit: {}" , commitWindow.getBaselineCommitHash());
                        throw e;
                    }
                } catch(Exception e) {
                    logger.info("Error when loading minimal diff ancestor ontology. Commit: {}" , commitWindow.getAncestorCommitHash(), e);
                    throw e;
                }
            } catch(Throwable e) {
                logger.error("Error when loading ontology document: {}" , e.getMessage());
                throw e;
            }
        }
    }

    private static class OboSingleFileExecutor implements OntologyHistoryAnalyzer.PlanExecutor {

        private final OntologyManagerProvider ontologyManagerProvider;

        public OboSingleFileExecutor(OntologyManagerProvider ontologyManagerProvider) {
            this.ontologyManagerProvider = ontologyManagerProvider;
        }

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            commitWindow.validateAncestorIsParent();
            var rootOntologyPath = plan.rootOntologyPath();
            var diffOptions = FastOboDiff.DiffOptions.builder()
                    .stripIdenticalImports(true)
                    .build();
            var differ = new FastOboDiff(diffOptions);

            var documentSource = new FileDocumentSource(rootOntologyPath.toFile());
            var afterDoc = oboDocCache.get(documentSource, commitWindow.getBaselineIndex()).orElseGet(() -> {
                var rootRepoPath = plan.rootRepoPath();
                var baselineContent = commitWindow.getContentAtBaseline(rootRepoPath);
                var parsed = differ.parse(baselineContent);
                oboDocCache.put(documentSource, parsed, commitWindow.getBaselineIndex());
                return parsed;
            });
            var beforeDoc = oboDocCache.get(documentSource, commitWindow.getAncestorIndex()).orElseGet(() -> {
                var rootPath = plan.rootRepoPath();
                var ancestorContent = commitWindow.getContentAtAncestor(rootPath);
                var parsed = differ.parse(ancestorContent);
                oboDocCache.put(documentSource, parsed, commitWindow.getAncestorIndex());
                return parsed;
            });

            var diff = differ.diff(beforeDoc, afterDoc);
            var parentMin = differ.renderBefore(diff);
            var baselineMin = differ.renderAfter(diff);
            var parentMan = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
            var parentOnt = parentMan.loadOntologyFromOntologyDocument(new StringDocumentSource(parentMin));
            var baselineMan = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
            var baselineOnt = baselineMan.loadOntologyFromOntologyDocument(new StringDocumentSource(baselineMin));
            if(!baselineOnt.getImportsDeclarations().equals(parentOnt.getImportsDeclarations())) {
                throw new SingleRootOntologyImportsMismatchException();
            }
            return new OntologyHistoryAnalyzer.LoadedPair(List.of(baselineOnt), List.of(parentOnt));
        }
    }

    private static class RootOwlSingleFileExecutor implements PlanExecutor {

        private final OntologyLoader loader;

        public RootOwlSingleFileExecutor(OntologyLoader loader) {
            this.loader = loader;
        }

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            commitWindow.validateAncestorIsParent();
            var rootOntologyPath = plan.rootOntologyPath();
            commitWindow.checkoutBaseline();
            logger.info("General OWL single file diff.  Doing optimized loading and change calculation" );
            var baselineOnts = loader.loadOntologyWithoutImports(rootOntologyPath, cache);
            commitWindow.checkoutAncestor();
            var parentOnts = loader.loadOntologyWithoutImports(rootOntologyPath, cache);
            if(!getOwlImportsDeclarations(baselineOnts).equals(getOwlImportsDeclarations(parentOnts))) {
                throw new SingleRootOntologyImportsMismatchException();
            }
            return new LoadedPair(baselineOnts, parentOnts);
        }
    }

    private static class MultiFileExecutor implements PlanExecutor {

        private final OntologyLoader loader;

        private MultiFileExecutor(OntologyLoader loader) {
            this.loader = loader;
        }

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            var rootOntologyPath = plan.rootOntologyPath();
            commitWindow.checkoutBaseline();
            var baselineCommitOntologies = loader.loadOntologyWithImports(rootOntologyPath, cache);
            // Load ontologies at the previous commit
            commitWindow.checkoutAncestor();
            var parentCommitOntologies = loader.loadOntologyWithImports(rootOntologyPath, cache);
            return new LoadedPair(baselineCommitOntologies, parentCommitOntologies);
        }
    }

    private static class OldestCommitExecutor implements PlanExecutor {

        private final OntologyLoader loader;

        private OldestCommitExecutor(OntologyLoader loader) {
            this.loader = loader;
        }

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            commitWindow.checkoutBaseline();
            var rootOntologyPath = plan.rootOntologyPath();
            commitWindow.getBaselineCommitMetadata();
            var baselineCommitOntologies = loader.loadOntologyWithImports(rootOntologyPath, cache);
            var emptyOntologiesList = List.<OWLOntology>of();
            return new LoadedPair(baselineCommitOntologies, emptyOntologiesList);
        }
    }

    private static class NoChangeExecutor implements PlanExecutor {

        @Override
        public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
            return new LoadedPair(List.of(), List.of());
        }
    }

    private static final class CommitWindow {

        private final UncheckedCommitNavigator nav;

        private int baselineIndex = 0;

        private int ancestorOffset = 1;

        private int lastLoadedIndex = -1;

        CommitWindow(CommitNavigator nav) {
            this.nav = new UncheckedCommitNavigator(nav);
        }

        public boolean hasBaselineCommit() {
            return baselineIndex < nav.getCommitCount();
        }

        public boolean hasAncestorCommit() {
            return getAncestorIndex() < nav.getCommitCount();
        }

        public void incrementAncestorOffset() {
            ancestorOffset++;
        }

        public int getBaselineIndex() {
            return baselineIndex;
        }

        public void advanceBaselineToAncestor() {
            baselineIndex = baselineIndex + ancestorOffset;
            ancestorOffset = 1;
            lastLoadedIndex = baselineIndex;
        }

        public int getLastLoadedIndex() {
            return lastLoadedIndex;
        }

        public int getAncestorIndex() {
            return baselineIndex + ancestorOffset;
        }

        public CommitMetadata checkoutBaseline() {
            return checkoutAt(baselineIndex);
        }

        public CommitMetadata checkoutAncestor() {
            int ancestorIndex = getAncestorIndex();
            return checkoutAt(ancestorIndex);
        }

        public InputStream getInputStreamForBaseline(String path) {
            return nav.getInputStreamAt(getBaselineIndex(), path);
        }

        public InputStream getInputStreamForAncestor(String path) {
            return nav.getInputStreamAt(getAncestorIndex(), path);
        }

        private CommitMetadata checkoutAt(int commitIndex) {
            long t0 = System.currentTimeMillis();
            var metadata = nav.checkoutCommitAt(commitIndex);
            long t1 = System.currentTimeMillis();
            logger.info("Checked out commit at index {} in {}ms" , commitIndex, t1 - t0);
            return metadata;
        }

        public void validateAncestorIsParent() {
            if(ancestorOffset != 1) {
                throw new RuntimeException("Expected ancestor commit to be the parent of the baseline commit" );
            }
        }

        public String getBaselineCommitHash() {
            return nav.getCommitAt(baselineIndex).commitHash();
        }

        public String getAncestorCommitHash() {
            if(hasAncestorCommit()) {
                return nav.getCommitAt(getAncestorIndex()).commitHash();
            }
            return "";
        }

        public CommitMetadata getBaselineCommitMetadata() {
            return nav.getCommitAt(baselineIndex);
        }

        public void setBaselineToLastLoadedCommit() {
            baselineIndex = lastLoadedIndex;
        }

        public String getContentAtAncestor(String path) {
            try {
                return new String(getInputStreamForAncestor(path).readAllBytes(), StandardCharsets.UTF_8);
            } catch(IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public String getContentAtBaseline(String rootRepoPath) {
            try {
                return new String(getInputStreamForBaseline(rootRepoPath).readAllBytes(), StandardCharsets.UTF_8);
            } catch(IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
