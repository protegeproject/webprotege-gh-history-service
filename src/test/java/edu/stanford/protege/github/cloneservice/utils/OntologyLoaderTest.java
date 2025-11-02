package edu.stanford.protege.github.cloneservice.utils;

import static org.junit.jupiter.api.Assertions.*;

import edu.stanford.protege.github.cloneservice.exception.OntologyLoadException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link OntologyLoader} */
@DisplayName("OntologyLoader Tests")
class OntologyLoaderTest {

    private OntologyLoader ontologyLoader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var ontologyManagerProvider = new OntologyManagerProvider();
        ontologyLoader = new OntologyLoader(ontologyManagerProvider);
    }

    @Test
    @DisplayName("Should create empty ontology successfully")
    void getEmptyOntologySuccessfully() {
        var ontology = ontologyLoader.getEmptyOntology();

        assertNotNull(ontology);
        assertTrue(ontology.getAxioms().isEmpty());
        assertNotNull(ontology.getOntologyID());
    }

    @Test
    @DisplayName("Should throw OntologyLoadException when ontology file does not exist")
    void throwExceptionWhenFileDoesNotExist() {
        var nonExistentFile = tempDir.resolve("non-existent.owl");

        var exception =
                assertThrows(OntologyLoadException.class, () -> ontologyLoader.loadOntologyFromFile(nonExistentFile));

        assertTrue(exception.getMessage().contains("Failed to load ontology from"));
        assertTrue(exception.getCause() instanceof FileNotFoundException);
    }

    @Test
    @DisplayName("Should throw NullPointerException when file path is null")
    void throwExceptionWhenFilePathNull() {
        var exception = assertThrows(NullPointerException.class, () -> ontologyLoader.loadOntologyFromFile(null));

        assertEquals("rootOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should load simple OWL ontology successfully")
    void loadSimpleOwlOntologySuccessfully() throws IOException {
        var owlContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/test#"
				         xml:base="http://example.org/test"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#"
				         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#">
				    <owl:Ontology rdf:about="http://example.org/test"/>
				    <owl:Class rdf:about="http://example.org/test#TestClass"/>
				</rdf:RDF>
				""";

        var owlFile = tempDir.resolve("test.owl");
        Files.writeString(owlFile, owlContent);

        var result = ontologyLoader.loadOntologyFromFile(owlFile);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());

        var ontology = result.get(0);
        assertNotNull(ontology);
        assertFalse(ontology.getAxioms().isEmpty());
    }

    @Test
    @DisplayName("Should handle invalid OWL file gracefully")
    void handleInvalidOwlFileGracefully() throws IOException {
        var invalidContent = "This is not a valid OWL file";
        var invalidFile = tempDir.resolve("invalid.owl");
        Files.writeString(invalidFile, invalidContent);

        var exception = assertThrows(RuntimeException.class, () -> ontologyLoader.loadOntologyFromFile(invalidFile));

        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("Failed to load ontology from"));
    }

    @Test
    @DisplayName("Should load ontology with missing imports silently")
    void loadOntologyWithMissingImportsSilently() throws IOException {
        var owlContentWithMissingImport =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/test#"
				         xml:base="http://example.org/test"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/test">
				        <owl:imports rdf:resource="http://example.org/non-existent-import"/>
				    </owl:Ontology>
				    <owl:Class rdf:about="http://example.org/test#TestClass"/>
				</rdf:RDF>
				""";

        var owlFile = tempDir.resolve("test-with-missing-import.owl");
        Files.writeString(owlFile, owlContentWithMissingImport);

        var result = ontologyLoader.loadOntologyFromFile(owlFile);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should load ontology with local import successfully")
    void loadOntologyWithLocalImportSuccessfully() throws IOException {
        var importedOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/imported#"
				         xml:base="http://example.org/imported"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/imported"/>
				    <owl:Class rdf:about="http://example.org/imported#ImportedClass"/>
				</rdf:RDF>
				""";

        var mainOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/main#"
				         xml:base="http://example.org/main"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/main">
				        <owl:imports rdf:resource="http://example.org/imported"/>
				    </owl:Ontology>
				    <owl:Class rdf:about="http://example.org/main#MainClass"/>
				</rdf:RDF>
				""";

        var importedFile = tempDir.resolve("imported.owl");
        var mainFile = tempDir.resolve("main.owl");
        Files.writeString(importedFile, importedOntologyContent);
        Files.writeString(mainFile, mainOntologyContent);

        var result = ontologyLoader.loadOntologyFromFile(mainFile);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());

        var mainOntology = result.get(0);
        assertNotNull(mainOntology);
        assertTrue(mainOntology.getOntologyID().getOntologyIRI().isPresent());
    }

    @Test
    @DisplayName("Should create multiple empty ontologies independently")
    void createMultipleEmptyOntologiesIndependently() {
        var ontology1 = ontologyLoader.getEmptyOntology();
        var ontology2 = ontologyLoader.getEmptyOntology();

        assertNotNull(ontology1);
        assertNotNull(ontology2);
        assertNotSame(ontology1, ontology2);
        assertNotEquals(ontology1.getOntologyID(), ontology2.getOntologyID());
    }

    @Test
    @DisplayName("Should load ontology with imports using loadOntologyWithImports")
    void loadOntologyWithImportsSuccessfully() throws IOException {
        var importedOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/imported#"
				         xml:base="http://example.org/imported"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/imported"/>
				    <owl:Class rdf:about="http://example.org/imported#ImportedClass"/>
				</rdf:RDF>
				""";

        var mainOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/main#"
				         xml:base="http://example.org/main"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/main">
				        <owl:imports rdf:resource="http://example.org/imported"/>
				    </owl:Ontology>
				    <owl:Class rdf:about="http://example.org/main#MainClass"/>
				</rdf:RDF>
				""";

        var importedFile = tempDir.resolve("imported.owl");
        var mainFile = tempDir.resolve("main.owl");
        Files.writeString(importedFile, importedOntologyContent);
        Files.writeString(mainFile, mainOntologyContent);

        var result = ontologyLoader.loadOntologyWithImports(mainFile, new LoadedOntologyCache((path) -> Optional.empty()));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size(), "Should load main ontology and imported ontology");

        var mainOntology = result.get(0);
        assertNotNull(mainOntology);
        assertTrue(mainOntology.getOntologyID().getOntologyIRI().isPresent());
    }

    @Test
    @DisplayName("Should load ontology without imports using loadOntologyWithoutImports")
    void loadOntologyWithoutImportsSuccessfully() throws IOException {
        var importedOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/imported#"
				         xml:base="http://example.org/imported"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/imported"/>
				    <owl:Class rdf:about="http://example.org/imported#ImportedClass"/>
				</rdf:RDF>
				""";

        var mainOntologyContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/main#"
				         xml:base="http://example.org/main"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#">
				    <owl:Ontology rdf:about="http://example.org/main">
				        <owl:imports rdf:resource="http://example.org/imported"/>
				    </owl:Ontology>
				    <owl:Class rdf:about="http://example.org/main#MainClass"/>
				</rdf:RDF>
				""";

        var importedFile = tempDir.resolve("imported.owl");
        var mainFile = tempDir.resolve("main.owl");
        Files.writeString(importedFile, importedOntologyContent);
        Files.writeString(mainFile, mainOntologyContent);

        var result = ontologyLoader.loadOntologyWithoutImports(mainFile, new LoadedOntologyCache((path) -> Optional.empty()));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size(), "Should load only main ontology, ignoring imports");

        var mainOntology = result.get(0);
        assertNotNull(mainOntology);
        assertTrue(mainOntology.getOntologyID().getOntologyIRI().isPresent());
    }

    @Test
    @DisplayName("Should load ontology with boolean parameter - includeImports=true")
    void loadOntologyFromFileWithImportsTrue() throws IOException {
        var owlContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/test#"
				         xml:base="http://example.org/test"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#"
				         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#">
				    <owl:Ontology rdf:about="http://example.org/test"/>
				    <owl:Class rdf:about="http://example.org/test#TestClass"/>
				</rdf:RDF>
				""";

        var owlFile = tempDir.resolve("test.owl");
        Files.writeString(owlFile, owlContent);

        var result = ontologyLoader.loadOntologyFromFile(owlFile, true);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should load ontology with boolean parameter - includeImports=false")
    void loadOntologyFromFileWithImportsFalse() throws IOException {
        var owlContent =
                """
				<?xml version="1.0"?>
				<rdf:RDF xmlns="http://example.org/test#"
				         xml:base="http://example.org/test"
				         xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
				         xmlns:owl="http://www.w3.org/2002/07/owl#"
				         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#">
				    <owl:Ontology rdf:about="http://example.org/test"/>
				    <owl:Class rdf:about="http://example.org/test#TestClass"/>
				</rdf:RDF>
				""";

        var owlFile = tempDir.resolve("test.owl");
        Files.writeString(owlFile, owlContent);

        var result = ontologyLoader.loadOntologyFromFile(owlFile, false);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Should throw NullPointerException when rootOntology path is null in loadOntologyWithImports")
    void throwExceptionWhenRootOntologyPathNullInLoadWithImports() {
        var exception = assertThrows(NullPointerException.class, () -> ontologyLoader.loadOntologyWithImports(null, new LoadedOntologyCache((path) -> Optional.empty())));

        assertEquals("rootOntology cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw NullPointerException when targetOntology path is null in loadOntologyWithoutImports")
    void throwExceptionWhenTargetOntologyPathNullInLoadWithoutImports() {
        var exception = assertThrows(NullPointerException.class, () -> ontologyLoader.loadOntologyWithoutImports(null, new LoadedOntologyCache((path) -> Optional.empty())));

        assertEquals("targetOntology cannot be null", exception.getMessage());
    }
}
