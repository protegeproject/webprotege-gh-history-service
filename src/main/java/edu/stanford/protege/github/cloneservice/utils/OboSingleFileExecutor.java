package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentSource;

import java.util.List;

class OboSingleFileExecutor implements PlanExecutor {

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
        return new LoadedPair(List.of(baselineOnt), List.of(parentOnt));
    }
}
