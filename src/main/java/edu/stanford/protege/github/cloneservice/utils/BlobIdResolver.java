package edu.stanford.protege.github.cloneservice.utils;

import java.nio.file.Path;
import java.util.Optional;

public interface BlobIdResolver {

    Optional<String> getBlobId(Path filePath);
}
