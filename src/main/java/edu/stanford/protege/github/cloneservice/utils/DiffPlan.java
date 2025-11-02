package edu.stanford.protege.github.cloneservice.utils;

import java.nio.file.Path;

record DiffPlan(
        OntologyHistoryAnalyzer.PlanKind kind,
        Path rootOntologyPath,
        String rootRepoPath
) {

}
