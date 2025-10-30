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
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.StringReader;
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

    private final OntologyDifferenceCalculator differenceCalculator;

    private final Duration maxAnalysisDuration;

    private final OntologyManagerProvider ontologyManagerProvider;

    private final List<PathMatcher> pathMatchers = new ArrayList<>();

    private final List<PathMatcher> pathExcludeMatchers = new ArrayList<>();

    @Inject
    public OntologyHistoryAnalyzer(OntologyLoader ontologyLoader,
                                   OntologyDifferenceCalculator differenceCalculator,
                                   @Value("${webprotege.github.max-analysis-time:5m}")
                                       Duration maxAnalysisDuration, OntologyManagerProvider ontologyManagerProvider) {
        this.ontologyLoader = Objects.requireNonNull(ontologyLoader, "OntologyLoader cannot be null");
        this.differenceCalculator =
                Objects.requireNonNull(differenceCalculator, "OntologyDifferenceCalculator cannot be null");
        this.maxAnalysisDuration = maxAnalysisDuration;
        this.ontologyManagerProvider = ontologyManagerProvider;
        for(var filterPattern : INCLUDE_FILTER_PATTERNS) {
            pathMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
        for(var filterPattern : EXCLUDE_FILTER_PATTERNS) {
            pathExcludeMatchers.add(FileSystems.getDefault().getPathMatcher("glob:" + filterPattern));
        }
    }

    public OntologyHistoryAnalyzer(OntologyLoader ontologyLoader, OntologyDifferenceCalculator differenceCalculator) {
        this(ontologyLoader, differenceCalculator, Duration.ofMinutes(10), new OntologyManagerProvider());
    }

    private static void safeResetWorkingDirectory(Path workingDirectory) {
        try {
            if(workingDirectory != null) {
                CommitNavigator cm = CommitNavigatorBuilder.forWorkingDirectory(workingDirectory).build();
//                cm.reset();
            }
        } catch(Exception e) {
            // best effort
            logger.warn("Working directory could not be reset" , e);
        }
    }

    private static void logAxiomChanges(List<AxiomChange> axiomChanges) {
        logger.info("Total number of axiom changes: {}" , axiomChanges.size());
    }

    private static void forceFunctionalSyntaxParsing(OWLOntologyManager childManager) {
        var childParsers = childManager.getOntologyParsers();
        childParsers.clear();
        childParsers.add(new OWLFunctionalSyntaxOWLParserFactory());
    }

    private static IRI ontologyKey(OWLOntology ont) {
        var id = ont.getOntologyID();
        return id.getOntologyIRI()
                .or(() -> id.getDefaultDocumentIRI().or(IRI.generateDocumentIRI()));
    }

    private static OWLOntologyID effectiveOntologyId(OWLOntology child, OWLOntology parent) {
        var cid = child.getOntologyID();
        if(!cid.isAnonymous()) {
            return cid;
        }
        var pid = parent.getOntologyID();
        if(!pid.isAnonymous()) {
            return pid;
        }
        // Fallback: derive from the child key to keep stable-ish identity
        var iri = child.getOntologyID().getDefaultDocumentIRI()
                .or(IRI::generateDocumentIRI);
        return new OWLOntologyID(Optional.of(iri), Optional.empty());
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

            Path workingDirectory = null;
            try {
                // Get the working directory from the repository
                workingDirectory = gitHubRepository.getWorkingDirectory();

                // Configure commit navigator to focus on the target ontology file
                var rootOntologyFile = ontologyFilePath.asString();
                var commitNavigator = CommitNavigatorBuilder.forWorkingDirectory(workingDirectory)
                        .withRequiredRepoPath(rootOntologyFile)
                        .fileFilters(INCLUDE_FILTER_PATTERNS)
                        .build();

                var startTime = Instant.now();
                // Resolve the absolute path to the ontology file in the local clone
                var ontologyFile = commitNavigator.resolveFilePath(rootOntologyFile);

                var cache = new LoadedOntologyCache(path -> {
                    try {
                        var relativePath = commitNavigator.relativize(path);
                        var currentCommit = commitNavigator.getCurrentCommit();
                        return commitNavigator.getBlobId(relativePath.toString(), currentCommit.commitHash());
                    } catch(RepositoryException e) {
                        logger.warn("Could not resolve blob id" );
                        return Optional.empty();
                    }

                });

                // Get the current commit metadata
                var childCommitMetadata = commitNavigator.getCurrentCommit();
                recordProcessingStarted(progressMonitor, childCommitMetadata);
                var childCommitOntologies = loadOntologiesWithErrorHandling(ontologyFile, childCommitMetadata, false, cache);


                var counter = 0;
                while(commitNavigator.hasParent()) {

                    var parentCommitPeek = commitNavigator.getParentOfCurrentCommit().get();
                    logger.info("----{}-------------------------------------------------------------------------------" , childCommitMetadata.commitHash());
                    logger.info("Child commit: {} on {} by {} (msg: {})" , childCommitMetadata.commitHash(), childCommitMetadata.commitDate(), childCommitMetadata.committerUsername(), StringUtils.truncate(childCommitMetadata.commitMessage().replace("\n" , " " ), 200));
                    logger.info("Parent commit: {} on {} by {}" , parentCommitPeek.commitHash(), parentCommitPeek.commitDate(), parentCommitPeek.committerUsername());
                    logger.info("Changed files for this commit: {}" , childCommitMetadata.getChangedFiles());
                    var filteredChangedFiles = getFilteredChangedFiles(childCommitMetadata);
                    logger.info("Filtered changed files for this commit: {}" , filteredChangedFiles);

                    var rootOntologyPath = commitNavigator.resolveFilePath(rootOntologyFile);

                    // The commit HAS to contain the root ontology
                    logger.info("Root ontology document exists: {}" , Files.exists(rootOntologyPath));

                    if(filteredChangedFiles.isEmpty()) {
                        // No actual changes in this commit.  We just need to advance the commit
                        recordProcessingFinished(progressMonitor, childCommitMetadata);
                        childCommitMetadata = commitNavigator.checkoutParent();
                        childCommitOntologies = Optional.empty();
                    } else if(isRootOntologyOboChangeOnly(commitNavigator, childCommitMetadata, rootOntologyFile)) {
                        logger.info("OBO single file diff.  Doing optimized loading and change calculation" );
                        // We can do a fast diff
                        var childContent = Files.readString(rootOntologyPath, StandardCharsets.UTF_8);
                        var parentCommitMetadata = commitNavigator.checkoutParent();
                        try {
                            boolean parentRootOntologyDocumentExists = Files.exists(rootOntologyPath);
                            if(parentRootOntologyDocumentExists) {
                                var parentContent = Files.readString(rootOntologyPath, StandardCharsets.UTF_8);
                                var axiomChanges = computeAxiomChangesUsingFastOboDiff(parentContent, childContent);
                                logAxiomChanges(axiomChanges);
                                allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));
                                recordProcessingFinished(progressMonitor, childCommitMetadata);
                                childCommitOntologies = Optional.empty();
                                childCommitMetadata = parentCommitMetadata;
                            } else {
                                // Ontology comparison is with an empty ontology
                                // We need to do the full on load and comparison with empty ontologies
                                commitNavigator.checkoutChild();
                                var childOntologies = loadOntologiesWithErrorHandling(rootOntologyPath, childCommitMetadata, false, cache);
                                commitNavigator.checkoutParent();
                                var axiomChanges = calculateAxiomChangesBetweenOntologies(childOntologies.get(), List.of());
                                logAxiomChanges(axiomChanges);
                                allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));
                                recordProcessingFinished(progressMonitor, childCommitMetadata);
                                childCommitOntologies = Optional.of(List.of());
                                childCommitMetadata = parentCommitMetadata;
                            }

                        } catch(Exception e) {
                            logger.warn("Error when loading ontology" , e);
                        }


                    } else if(isRootOntologyChangeOnly(commitNavigator, childCommitMetadata, rootOntologyFile)) {
                        CommitMetadata parentCommitMetadata = null;
                        if(isOwlFunctionalSyntax(rootOntologyPath)) {
                            try {
                                logger.info("Functional Syntax single file diff.  Doing optimized loading and change calculation" );
                                var childContent = Files.readString(rootOntologyPath, StandardCharsets.UTF_8);
                                parentCommitMetadata = commitNavigator.checkoutParent();
                                var parentContent = Files.readString(rootOntologyPath, StandardCharsets.UTF_8);
                                var afterDoc = new FsParser(new StringReader(childContent)).parse();
                                var beforeDoc = new FsParser(new StringReader(parentContent)).parse();
                                var diff = MinimalOfnDiff.build(beforeDoc, afterDoc);
                                var minimalParentDoc = new MinimalOfnDiff.Renderer().render(diff.before);
                                var minimalChildDoc = new MinimalOfnDiff.Renderer().render(diff.after);

                                try {
                                    // Recall parent=before and child=after
                                    var parentManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                                    forceFunctionalSyntaxParsing(parentManager);
                                    var parentOnt = parentManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalParentDoc));
                                    try {
                                        var childManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                                        forceFunctionalSyntaxParsing(childManager);
                                        var childOnt = childManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalChildDoc));
                                        var axiomChanges = calculateAxiomChangesBetweenOntologies(List.of(childOnt), List.of(parentOnt));
                                        logAxiomChanges(axiomChanges);
                                        allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));
                                    } catch(Exception e) {
                                        logger.info("Error when loading OFN child ontology document from commit: {}" , childCommitMetadata.commitHash());
                                    }
                                } catch(Exception e) {
                                    logger.info("Error when loading parent ontology in functional syntax from commit: {}" , parentCommitMetadata.commitHash(), e);
                                }
                            } catch(Exception e) {
                                logger.error("Error when loading ontology document" , e);
                            }
                        } else {
                            logger.info("General OWL single file diff.  Doing optimized loading and change calculation" );
                            childCommitOntologies = loadOntologiesWithErrorHandling(rootOntologyPath, childCommitMetadata, true, cache);
                            parentCommitMetadata = commitNavigator.checkoutParent();
                            var parentCommitOntologies = loadOntologiesWithErrorHandling(rootOntologyPath, parentCommitMetadata, true, cache);
                            if(parentCommitOntologies.isPresent() && childCommitOntologies.isPresent()) {
                                var axiomChanges = calculateAxiomChangesBetweenOntologies(
                                        childCommitOntologies.get(), parentCommitOntologies.get());
                                logAxiomChanges(axiomChanges);
                                allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));
                            }

                        }

                        // Reset for next load
                        childCommitOntologies = Optional.empty();
                        if(parentCommitMetadata != null) {
                            childCommitMetadata = parentCommitMetadata;
                        }

                    } else {
                        if(childCommitOntologies.isEmpty()) {
                            childCommitMetadata = commitNavigator.checkoutChild();
                            logger.info("Child ontologies is empty.  Reloading children from commit: {}" , childCommitMetadata.commitHash());
                            childCommitOntologies = loadOntologiesWithErrorHandling(ontologyFile, childCommitMetadata, false, cache);
                        }
                        // Load ontologies at the previous commit
                        var parentCommitMetadata = commitNavigator.checkoutParent();
                        var parentCommitOntologies = loadOntologiesWithErrorHandling(ontologyFile, parentCommitMetadata, false, cache);

                        if(childCommitOntologies.isPresent() && parentCommitOntologies.isPresent()) {
                            var axiomChanges = calculateAxiomChangesBetweenOntologies(
                                    childCommitOntologies.get(), parentCommitOntologies.get());
                            logAxiomChanges(axiomChanges);
                            allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));

                            recordProcessingFinished(progressMonitor, childCommitMetadata);
                            // Advance the window: parent becomes the new child
                            childCommitOntologies = parentCommitOntologies;
                            childCommitMetadata = parentCommitMetadata;
                            recordProcessingStarted(progressMonitor, childCommitMetadata);
                        } else {
                            if(childCommitOntologies.isEmpty()) {
                                logger.warn("Could not load ontologies in child commit {}" , childCommitMetadata.commitHash());
                            }
                            if(parentCommitOntologies.isEmpty()) {
                                logger.warn("Could not load ontologies in parent commit {}" , parentCommitMetadata.commitHash());
                            }

                            // Ensure the in-flight commit gets a finished signal even if we skip differencing
                            recordProcessingFinished(progressMonitor, childCommitMetadata);
                            // Advance the window even if one side failed to load
                            childCommitOntologies = parentCommitOntologies;
                            childCommitMetadata = parentCommitMetadata;
                            recordProcessingStarted(progressMonitor, childCommitMetadata);
                        }

                    }


                    counter++;
                    var elapsedTime = Duration.between(startTime, Instant.now());

                    logger.info("Processed {} commits out of {} commits (~{} ms per commit)" , counter, commitNavigator.getCommitCount(), (elapsedTime.toMillis() / counter));

                    if(elapsedTime.compareTo(maxAnalysisDuration) > 0) {
                        logger.info(
                                "Spent longer than {} minutes analyzing history at commit {}. Finishing." ,
                                maxAnalysisDuration.toMinutes(),
                                childCommitMetadata != null ? childCommitMetadata.commitHash() : "<unknown>"
                        );
                        break;
                    }
                }

                // Handle the initial commit
                if(childCommitOntologies.isPresent()) {
                    // Finish the last started commit, if any
                    var axiomChanges = calculateInitialOntologyChanges(childCommitOntologies.get());
                    allCommitChanges.add(new OntologyCommitChange(axiomChanges, childCommitMetadata, repositoryUrl));
                }
                return ImmutableList.copyOf(allCommitChanges);
            } catch(Exception e) {
                logger.error("An error occurred when analyzing the commit history" , e);
                throw new OntologyComparisonException("Failed to analyze ontology commit history" , e);
            } finally {
                // Ensure repo state is restored even on failure (best-effort)
                safeResetWorkingDirectory(workingDirectory);
            }
    }

    private boolean isOwlFunctionalSyntax(Path rootOntologyPath) {
        return OfnFileDetector.isOwlFunctionalSyntax(rootOntologyPath);
    }

    private void recordProcessingStarted(@NotNull OntologyHistoryAnalyzerProgressMonitor progressMonitor, CommitMetadata commitMetadata) {
        if(commitMetadata != null) {
            progressMonitor.processingStarted(commitMetadata);
        }
    }

    private void recordProcessingFinished(@NotNull OntologyHistoryAnalyzerProgressMonitor progressMonitor, CommitMetadata commitMetadata) {
        if(commitMetadata != null) {
            progressMonitor.processingFinished(commitMetadata);
        }
    }

    private @NotNull List<AxiomChange> computeAxiomChangesUsingFastOboDiff(String parentContent, String childContent) throws OWLOntologyCreationException {
        var diffOptions = FastOboDiff.DiffOptions.builder()
                .stripIdenticalImports(true)
                .build();
        var differ = new FastOboDiff(diffOptions);
        var diff = differ.diff(parentContent, childContent);
        var parentMin = differ.renderBefore(diff);
        var childMin = differ.renderAfter(diff);
        var parentMan = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
        var parentOnt = parentMan.loadOntologyFromOntologyDocument(new StringDocumentSource(parentMin));
        var childMan = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
        var childOnt = childMan.loadOntologyFromOntologyDocument(new StringDocumentSource(childMin));
        return calculateAxiomChangesBetweenOntologies(List.of(childOnt), List.of(parentOnt));
    }

    private List<ChangedFile> getFilteredChangedFiles(CommitMetadata commitMetadata) {
        return commitMetadata.getChangedFiles().stream()
                .filter(f -> matchesFilters(f.path()))
                .collect(Collectors.toList());
    }

    private boolean isRootOntologyOboChangeOnly(CommitNavigator commitNavigator,
                                                CommitMetadata childCommitMetadata,
                                                String rootOntologyRepoPath) {
        var rootOntologyChange = isRootOntologyChangeOnly(commitNavigator, childCommitMetadata, rootOntologyRepoPath);
        return rootOntologyChange && rootOntologyRepoPath.endsWith(".obo" );
    }

    private boolean isRootOntologyChangeOnly(CommitNavigator commitNavigator, CommitMetadata childCommitMetadata, String rootOntologyRepoPath) {
        var rootOntologyPath = commitNavigator.resolveFilePath(rootOntologyRepoPath);
        if(!Files.exists(rootOntologyPath)) {
            return false;
        }
        var filteredChangedFiles = getFilteredChangedFiles(childCommitMetadata);
        if(filteredChangedFiles.size() != 1) {
            return false;
        }
        var changedFile = filteredChangedFiles.getFirst();
        return changedFile.path().equals(rootOntologyRepoPath);
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

    /**
     * Loads ontologies with centralized error handling and logging.
     *
     * @param rootOntology   the root ontology file to load
     * @param commitMetadata metadata of the current commit for logging
     * @param rootOnly
     * @return Optional of loaded ontologies; empty if loading failed
     */
    private Optional<List<OWLOntology>> loadOntologiesWithErrorHandling(
            @Nonnull Path rootOntology, @Nonnull CommitMetadata commitMetadata, boolean rootOnly, LoadedOntologyCache cache) {
        if(!Files.exists(rootOntology)) {
            logger.info("Root ontology document does not exist.  This means there are no ontologies in this commit. Commit: {}" , commitMetadata.commitHash());
            // Simply no ontologies
            return Optional.of(List.of());
        }
        try {
            if(rootOnly) {
                var ontologies = ontologyLoader.loadOntologyWithoutImports(rootOntology, cache);
                return Optional.of(ontologies);
            } else {
                var ontologies = ontologyLoader.loadOntologyWithImports(rootOntology, cache);
                return Optional.of(ontologies);
            }

        } catch (Exception e) {
            logger.warn("Failed to load ontology for commit {} at {}: {}" ,
                    commitMetadata.commitHash(), rootOntology, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Calculates axiom changes between current and previous commit ontologies
     *
     * @param childCommitOntologies  ontologies from the child commit
     * @param parentCommitOntologies ontologies from the parent commit
     * @return list of axiom changes between commits
     */
    @Nonnull
    private List<AxiomChange> calculateAxiomChangesBetweenOntologies(
            @Nonnull List<OWLOntology> childCommitOntologies,
            @Nonnull List<OWLOntology> parentCommitOntologies) {

        var childrenByIri = new HashMap<IRI, OWLOntology>();
        var parentsByIri = new HashMap<IRI, OWLOntology>();

        childCommitOntologies.forEach(ont -> childrenByIri.put(ontologyKey(ont), ont));
        parentCommitOntologies.forEach(ont -> parentsByIri.put(ontologyKey(ont), ont));

        var pairs = new ArrayList<OntologyPair>();

        // 1) Matched pairs (remove matched keys from both maps)
        var matchedKeys = new HashSet<>(childrenByIri.keySet());
        matchedKeys.retainAll(parentsByIri.keySet());
        for(var iri : matchedKeys) {
            pairs.add(new OntologyPair(childrenByIri.remove(iri), parentsByIri.remove(iri)));
        }

        // 2) Parent-only → removed; Child-only → added
        parentsByIri.values().forEach(p -> pairs.add(new OntologyPair(ontologyLoader.getEmptyOntology(), p)));
        childrenByIri.values().forEach(c -> pairs.add(new OntologyPair(c, ontologyLoader.getEmptyOntology())));

        return pairs.stream()
                .flatMap(pair -> differenceCalculator
                        .calculateAxiomChanges(
                                pair.child,
                                pair.parent,
                                // Prefer child’s real ID if not anonymous; else use parent’s; else synthesize
                                effectiveOntologyId(pair.child, pair.parent))
                        .stream())
                .collect(ImmutableList.toImmutableList());
    }

    private record OntologyPair(OWLOntology child, OWLOntology parent) {

    }

    /**
     * Calculates axiom changes for the initial commit (compared to empty ontology)
     *
     * @param ontologies ontologies from the initial commit
     * @return list of axiom changes for initial commit
     */
    @Nonnull
    private List<AxiomChange> calculateInitialOntologyChanges(@Nonnull List<OWLOntology> ontologies) {

        var emptyOntology = ontologyLoader.getEmptyOntology();
        return ontologies.stream()
                .flatMap(ontology ->
                        differenceCalculator
                                .calculateAxiomChanges(ontology, emptyOntology, ontology.getOntologyID())
                                .stream())
                .collect(ImmutableList.toImmutableList());
    }


}
