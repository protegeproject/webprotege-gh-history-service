package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import edu.stanford.protege.github.cloneservice.exception.OntologyLoadException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.protege.xmlcatalog.owlapi.XMLCatalogIRIMapper;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Handles loading of ontology files using OWL-API */
@Component
public class OntologyLoader {

    private final OntologyManagerProvider ontologyManagerProvider;

    private OWLOntology emptyOntology;

    public OntologyLoader(@Nonnull OntologyManagerProvider ontologyManagerProvider) {
        this.ontologyManagerProvider =
                Objects.requireNonNull(ontologyManagerProvider, "ontologyManagerProvider cannot be null");
    }

    private static final Logger logger = LoggerFactory.getLogger(OntologyLoader.class);

    /**
     * Creates a new empty OWL ontology
     *
     * @return A new empty OWL ontology
     */
    @Nonnull
    public synchronized OWLOntology getEmptyOntology() {
        if(emptyOntology == null) {
            var ontologyManager = ontologyManagerProvider.getEmptyOntologyManager();
            try {
                emptyOntology = ontologyManager.createOntology();
            } catch (OWLOntologyCreationException e) {
                throw new RuntimeException("Failed to create empty ontology", e);
            }
        }
        return emptyOntology;
    }

    /**
     * Loads an OWL ontology from the specified file path along with all its imported ontologies.
     *
     * <p>The returned list contains the root ontology as the first element, followed by all imported
     * ontologies. If the ontology has no imports, the list will contain only the root ontology.
     *
     * <p><strong>Import Resolution:</strong>
     *
     * <ul>
     *   <li>Missing or anonymous imports are handled silently (no exceptions thrown)
     *   <li>Local imports in the same directory as the main ontology are automatically mapped
     *   <li>All successfully resolved imports are included in the result
     * </ul>
     *
     * @param rootOntology the path to the root ontology file to load.
     * @return a list containing the root ontology and all its imported ontologies. The root ontology
     *     is always the first element.
     * @throws OntologyLoadException if {@code rootOntology} is {@code null} or doesn't exist, The
     *     catalog file is invalid, or ontology failed to load.
     */
    @Nonnull
    public List<OWLOntology> loadOntologyWithImports(@Nonnull Path rootOntology, LoadedOntologyCache loadedOntologyCache) throws OntologyLoadException {
        Objects.requireNonNull(rootOntology, "rootOntology cannot be null");
        try {
            var ontologyFile = getOntologyFile(rootOntology);
            var ontologyManager = ontologyManagerProvider.getOntologyManagerWithLoadImports(loadedOntologyCache);

            // Add IRI mapper for local imports in the same directory
            ontologyManager.getIRIMappers().clear();
            var parentDir = rootOntology.getParent();
            if (parentDir != null) {
                var catalogFile = findCatalogFile(parentDir);
                if (catalogFile.isPresent()) {
                    var newMapper = new XMLCatalogIRIMapper(catalogFile.get().toFile());
                    ontologyManager.getIRIMappers().add(newMapper);
                    logger.debug("Using XMLCatalogIRIMapper with catalog file: {}", catalogFile.get());
                } else {
                    var newMapper = new AutoIRIMapper(parentDir.toFile(), true);
                    ontologyManager.getIRIMappers().add(newMapper);
                    logger.debug("Using AutoIRIMapper for directory: {}", parentDir);
                }
            }

            logger.info("Loading root ontology from: {}", rootOntology);
            ontologyManager.loadOntologyFromOntologyDocument(ontologyFile);
            var ontologies = ontologyManager.getOntologies();
            logger.info("Successfully loaded {} ontologies including imports", ontologies.size());
            return List.copyOf(ontologies);
        } catch (IOException | OWLOntologyCreationException e) {
            throw new OntologyLoadException("Failed to load ontology from: " + rootOntology, e);
        }
    }

    /**
     * Loads an OWL ontology from the specified file path without loading its imported ontologies.
     *
     * <p>The returned list contains only the target ontology. All import statements are ignored.
     *
     * @param targetOntology the path to the ontology file to load.
     * @return a list containing only the target ontology.
     * @throws OntologyLoadException if {@code targetOntology} is {@code null} or doesn't exist, The
     *     catalog file is invalid, or ontology failed to load.
     */
    @Nonnull
    public List<OWLOntology> loadOntologyWithoutImports(@Nonnull Path targetOntology, LoadedOntologyCache loadedOntologyCache) throws OntologyLoadException {
        Objects.requireNonNull(targetOntology, "targetOntology cannot be null");
        try {
            var ontologyFile = getOntologyFile(targetOntology);
            var ontologyManager = ontologyManagerProvider.getOntologyManagerWithIgnoredImports();

            logger.info("Loading ontology from: {}", targetOntology);
            var ontology = ontologyManager.loadOntologyFromOntologyDocument(ontologyFile);

            // Return only the root ontology
            return ImmutableList.of(ontology);
        } catch (IOException | OWLOntologyCreationException e) {
            throw new OntologyLoadException("Failed to load ontology from: " + targetOntology, e);
        }
    }

    /**
     * Loads an OWL ontology from the specified file path along with all its imported ontologies.
     *
     * <p>This method provides backward compatibility by defaulting to loading imports.
     *
     * @param filePath the path to the ontology file to load.
     * @return a list containing the main ontology and all its imported ontologies.
     * @throws OntologyLoadException if {@code filePath} is {@code null} or doesn't exist, The catalog
     *     file is invalid, or ontology failed to load.
     */
    @Nonnull
    public List<OWLOntology> loadOntologyFromFile(@Nonnull Path filePath) throws OntologyLoadException {
//        return loadOntologyWithImports(filePath);
        throw new RuntimeException("Not implemented yet");
    }

    /**
     * Loads an OWL ontology from the specified file path along with all its imported ontologies.
     *
     * @param filePath the path to the ontology file to load.
     * @param includeImports whether to include imported ontologies
     * @return a list containing the main ontology and optionally its imported ontologies.
     * @throws OntologyLoadException if {@code filePath} is {@code null} or doesn't exist, The catalog
     *     file is invalid, or ontology failed to load.
     */
    @Nonnull
    public List<OWLOntology> loadOntologyFromFile(@Nonnull Path filePath, boolean includeImports)
            throws OntologyLoadException {
//        return includeImports ? loadOntologyWithImports(filePath) : loadOntologyWithoutImports(filePath);
        throw new RuntimeException("Not implemented yet");
    }

    private File getOntologyFile(@NotNull Path filePath) throws FileNotFoundException {
        var ontologyFile = filePath.toFile();
        if (!ontologyFile.exists()) {
            var message = "Ontology file does not exist: " + filePath;
            logger.error(message);
            throw new FileNotFoundException(message);
        }
        return ontologyFile;
    }

    /**
     * Finds a catalog file in the directory (catalog-v001.xml or catalog-*.xml)
     *
     * @param directory the directory to search
     * @return an Optional containing the path to the catalog file if found, empty otherwise
     */
    @Nonnull
    private Optional<Path> findCatalogFile(@Nonnull Path directory) {
        Objects.requireNonNull(directory, "directory cannot be null");

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> {
                        var fileName = file.getFileName().toString();
                        return fileName.equals("catalog-v001.xml")
                                || (fileName.startsWith("catalog-") && fileName.endsWith(".xml"));
                    })
                    .findFirst();
        } catch (IOException e) {
            logger.warn("Failed to list files in directory: {}", directory, e);
            return Optional.empty();
        }
    }
}
