package edu.stanford.protege.github.cloneservice.utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class OfnFileDetector {

    /**
     * Checks if the first non-blank line of a file starts with "Prefix(" (ignoring leading whitespace).
     *
     * @param path The file to inspect.
     * @return true if the first non-blank line starts with "Prefix(", false otherwise.
     */
    public static boolean isOwlFunctionalSyntax(@Nonnull Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.stripLeading();
                if (line.isEmpty()) {
                    continue;
                }
                return line.startsWith("Prefix(");
            }
        } catch (IOException e) {
            // could not read file - does not matter in this case
        }
        return false;
    }

}
