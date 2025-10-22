package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;

public class NullProgressMonitor implements OntologyHistoryAnalyzerProgressMonitor {

    @Override
    public void processingStarted(CommitMetadata commitMetadata) {

    }

    @Override
    public void processingFinished(CommitMetadata commitMetadata) {

    }
}
