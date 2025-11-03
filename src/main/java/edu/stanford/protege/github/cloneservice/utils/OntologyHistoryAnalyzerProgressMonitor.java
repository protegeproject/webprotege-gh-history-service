package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;

import java.util.List;

public interface OntologyHistoryAnalyzerProgressMonitor {

    void processingHistoryStarted(String repositoryUrl, int numberOfCommits);

    void processingCommitStarted(CommitMetadata commitMetadata, List<String> changedFilePaths);

    void processingCommitFinished(CommitMetadata commitMetadata);

    void processingHistoryFinished();
}
