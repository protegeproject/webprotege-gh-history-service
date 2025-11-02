package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.exceptions.RepositoryException;
import edu.stanford.protege.commitnavigator.exceptions.UncheckedRepositoryException;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.CommitNavigator;

import java.io.InputStream;

public class UncheckedCommitNavigator {

    private final CommitNavigator delegate;

    public UncheckedCommitNavigator(CommitNavigator delegate) {
        this.delegate = delegate;
    }

    public int getCommitCount() {
        return delegate.getNumberOfCommits();
    }

    public CommitMetadata getCommitAt(int index) {
        return delegate.getCommitAt(index);
    }

    public CommitMetadata checkoutCommitAt(int index) {
        try {
            return delegate.checkoutCommitAt(index);
        } catch(RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        }
    }

    public InputStream getInputStreamAt(int index, String path) {
        try {
            return delegate.getStream(path, getCommitAt(index).commitHash());
        } catch(RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        }
    }
}
