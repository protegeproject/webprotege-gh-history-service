package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.CommitNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

final class CommitWindow {

    private static final Logger logger = LoggerFactory.getLogger(CommitWindow.class);

    private final UncheckedCommitNavigator nav;

    private int baselineIndex = 0;

    private int ancestorOffset = 1;

    private int lastLoadedIndex = -1;


    CommitWindow(CommitNavigator nav) {
        this.nav = new UncheckedCommitNavigator(nav);
    }

    public boolean hasBaselineCommit() {
        return baselineIndex < nav.getCommitCount();
    }

    public boolean hasAncestorCommit() {
        return getAncestorIndex() < nav.getCommitCount();
    }

    public void incrementAncestorOffset() {
        ancestorOffset++;
    }

    public int getBaselineIndex() {
        return baselineIndex;
    }

    public void advanceBaselineToAncestor() {
        baselineIndex = baselineIndex + ancestorOffset;
        ancestorOffset = 1;
        lastLoadedIndex = baselineIndex;
    }

    public int getLastLoadedIndex() {
        return lastLoadedIndex;
    }

    public int getAncestorIndex() {
        return baselineIndex + ancestorOffset;
    }

    public CommitMetadata checkoutBaseline() {
        return checkoutAt(baselineIndex);
    }

    public CommitMetadata checkoutAncestor() {
        int ancestorIndex = getAncestorIndex();
        return checkoutAt(ancestorIndex);
    }

    public InputStream getInputStreamForBaseline(String path) {
        return nav.getInputStreamAt(getBaselineIndex(), path);
    }

    public InputStream getInputStreamForAncestor(String path) {
        return nav.getInputStreamAt(getAncestorIndex(), path);
    }

    private CommitMetadata checkoutAt(int commitIndex) {
        long t0 = System.currentTimeMillis();
        var metadata = nav.checkoutCommitAt(commitIndex);
        long t1 = System.currentTimeMillis();
        logger.debug("Checked out commit at index {} in {}ms" , commitIndex, t1 - t0);
        return metadata;
    }

    public void validateAncestorIsParent() {
        if(ancestorOffset != 1) {
            throw new RuntimeException("Expected ancestor commit to be the parent of the baseline commit" );
        }
    }

    public String getBaselineCommitHash() {
        return nav.getCommitAt(baselineIndex).commitHash();
    }

    public String getAncestorCommitHash() {
        if(hasAncestorCommit()) {
            return nav.getCommitAt(getAncestorIndex()).commitHash();
        }
        return "";
    }

    public CommitMetadata getBaselineCommitMetadata() {
        return nav.getCommitAt(baselineIndex);
    }

    public void setBaselineToLastLoadedCommit() {
        if (lastLoadedIndex >= 0) {
            baselineIndex = lastLoadedIndex;
        } else {
            baselineIndex = 0;
        }
        ancestorOffset = 1;
    }

    public String getContentAtAncestor(String path) {
        try (var in = getInputStreamForAncestor(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String getContentAtBaseline(String path) {
        try (var in = getInputStreamForBaseline(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
