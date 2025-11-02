package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;

public interface OntologyHistoryAnalyzerProgressMonitor {

    void processingHistoryStarted(String repositoryUrl, int numberOfCommits);

    void processingCommitStarted(CommitMetadata commitMetadata);

    void processingCommitFinished(CommitMetadata commitMetadata);

    void processingHistoryFinished();
}
