package edu.stanford.protege.github.cloneservice.utils;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the blob SHA (Git object ID) for a given file
 * in the <strong>currently checked-out commit</strong>.
 *
 * <p>This interface is used when working with the active repository
 * state — e.g., the commit that is currently checked out by the
 * {@link edu.stanford.protege.commitnavigator.utils.CommitNavigator}.</p>
 */
public interface CheckedOutBlobIdResolver {

    /**
     * Resolves the blob ID for the given file in the checked-out commit.
     *
     * @param filePath the absolute or repository-relative path to the file
     * @return an Optional containing the blob ID if found
     */
    @Nonnull
    Optional<String> getBlobId(Path filePath);
}
