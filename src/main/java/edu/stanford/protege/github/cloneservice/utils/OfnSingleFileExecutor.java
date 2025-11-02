package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.functional.parser.OWLFunctionalSyntaxOWLParserFactory;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;

class OfnSingleFileExecutor implements PlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(OfnSingleFileExecutor.class);

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
                    var doc = new FsParser(new StringReader(parentContent)).parse();
                    fsDocCache.put(docSource, doc, commitWindow.getAncestorIndex());
                    return doc;
                } catch(Throwable e) {
                    throw new RuntimeException(e);
                }
            });
            var diff = MinimalOfnDiff.build(beforeDoc, afterDoc);

            var minimalParentDoc = new MinimalOfnDiff.Renderer().render(diff.before);
            var minimalBaselineDoc = new MinimalOfnDiff.Renderer().render(diff.after);

            try {
                var baselineManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                forceFunctionalSyntaxParsing(baselineManager);
                var baselineOnt = baselineManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalBaselineDoc));

                try {

                    var parentManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();
                    forceFunctionalSyntaxParsing(parentManager);
                    var parentOnt = parentManager.loadOntologyFromOntologyDocument(new StringDocumentSource(minimalParentDoc));

                    if(!baselineOnt.getImportsDeclarations().equals(parentOnt.getImportsDeclarations())) {
                        throw new SingleRootOntologyImportsMismatchException();
                    }

                    return new LoadedPair(List.of(baselineOnt), List.of(parentOnt));
                } catch(Exception e) {
                    logger.info("Error when minimal-diff ancestor ontology: Commit: {}" , commitWindow.getBaselineCommitHash());
                    throw e;
                }
            } catch(Exception e) {
                logger.info("Error when loading minimal diff baseline ontology. Commit: {}" , commitWindow.getAncestorCommitHash(), e);
                throw e;
            }
        } catch(Throwable e) {
            logger.error("Error when loading ontology document: {}" , e.getMessage());
            throw e;
        }
    }



    private static void forceFunctionalSyntaxParsing(OWLOntologyManager baselineManager) {
        var baselineParsers = baselineManager.getOntologyParsers();
        baselineParsers.clear();
        baselineParsers.add(new OWLFunctionalSyntaxOWLParserFactory());
    }
}
