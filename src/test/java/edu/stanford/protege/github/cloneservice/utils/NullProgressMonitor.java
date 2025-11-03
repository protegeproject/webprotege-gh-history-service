package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;

import java.util.List;

public class NullProgressMonitor implements OntologyHistoryAnalyzerProgressMonitor {

    @Override
    public void processingHistoryStarted(String repositoryUrl, int numberOfCommits) {

    }

    @Override
    public void processingCommitStarted(CommitMetadata commitMetadata, List<String> changedFilePaths) {

    }

    @Override
    public void processingCommitFinished(CommitMetadata commitMetadata) {

    }

    @Override
    public void processingHistoryFinished() {

    }
}
