package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;

public interface OntologyHistoryAnalyzerProgressMonitor {

    void processingStarted(CommitMetadata commitMetadata);

    void processingFinished(CommitMetadata commitMetadata);
}
