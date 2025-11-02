package edu.stanford.protege.github.cloneservice.utils;

class MultiFileExecutor implements PlanExecutor {

    private final OntologyLoader loader;

    MultiFileExecutor(OntologyLoader loader) {
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
