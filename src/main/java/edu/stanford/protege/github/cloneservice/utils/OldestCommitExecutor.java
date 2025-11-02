package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;

class OldestCommitExecutor implements PlanExecutor {

    private final OntologyLoader loader;

    OldestCommitExecutor(OntologyLoader loader) {
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
