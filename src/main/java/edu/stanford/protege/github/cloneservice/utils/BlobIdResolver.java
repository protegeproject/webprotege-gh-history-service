package edu.stanford.protege.github.cloneservice.utils;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the blob SHA (Git object ID) for a given file path
 * in a historical commit context.
 *
 * <p>This interface is used when reading files from a repository
 * at specific commits (not necessarily the currently checked-out one),
 * typically during commit history analysis.</p>
 *
 * <p>Implementations are expected to map a file path to its blob ID
 * within a specific commit identified by an index into a commit
 * sequence (e.g., a baseline commit vs. its ancestor).</p>
 */
public interface BlobIdResolver {

    /**
     * Resolves the blob ID for a given file at a specific commit index.
     *
     * @param filePath the absolute or repository-relative path to the file
     * @param index the index of the commit in history (0 = baseline)
     * @return an Optional containing the blob ID if found
     */
    @Nonnull
    Optional<String> getBlobId(Path filePath, int index);
}
