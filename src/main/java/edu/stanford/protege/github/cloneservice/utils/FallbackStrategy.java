package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.webprotege.change.OntologyChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

class FallbackStrategy {

    private final Logger logger = LoggerFactory.getLogger(FallbackStrategy.class);

    private final OntologyLoader loader;

    private final OntologiesDifferenceCalculator differenceCalaculator;

    public FallbackStrategy(OntologyLoader loader, OntologiesDifferenceCalculator differenceCalaculator) {
        this.loader = loader;
        this.differenceCalaculator = differenceCalaculator;
    }

    public List<OntologyChange> fallback(
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
