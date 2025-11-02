package edu.stanford.protege.github.cloneservice.utils;

import java.util.List;

class NoChangeExecutor implements PlanExecutor {

    @Override
    public LoadedPair loadOntologies(DiffPlan plan, LoadedOntologyCache cache, CommitWindow commitWindow, LoadedOboDocCache oboDocCache, LoadedFsDocCache fsDocCache) throws Exception {
        return new LoadedPair(List.of(), List.of());
    }
}
