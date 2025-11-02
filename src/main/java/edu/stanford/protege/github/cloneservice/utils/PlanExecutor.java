package edu.stanford.protege.github.cloneservice.utils;

interface PlanExecutor {

    LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache,
                              CommitWindow commitWindow,
                              LoadedOboDocCache oboDocCache,
                              LoadedFsDocCache fsDocCache) throws Exception;
}
