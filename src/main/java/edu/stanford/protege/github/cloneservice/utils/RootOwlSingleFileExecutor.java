package edu.stanford.protege.github.cloneservice.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RootOwlSingleFileExecutor implements PlanExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RootOwlSingleFileExecutor.class);

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
        if(!Imports.getOwlImportsDeclarations(baselineOnts).equals(Imports.getOwlImportsDeclarations(parentOnts))) {
            throw new SingleRootOntologyImportsMismatchException();
        }
        return new LoadedPair(baselineOnts, parentOnts);
    }
}
