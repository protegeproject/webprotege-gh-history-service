package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.functional.parser.OWLFunctionalSyntaxOWLParserFactory;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.manchestersyntax.parser.ManchesterOWLSyntaxOntologyParserFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.oboformat.OBOFormatOWLAPIParserFactory;
import org.semanticweb.owlapi.owlxml.parser.OWLXMLParserFactory;
import org.semanticweb.owlapi.rdf.rdfxml.parser.RDFXMLParserFactory;
import org.semanticweb.owlapi.rdf.turtle.parser.TurtleOntologyParserFactory;
import org.semanticweb.owlapi.rio.RioBinaryRdfParserFactory;
import org.semanticweb.owlapi.rio.RioJsonLDParserFactory;
import org.semanticweb.owlapi.rio.RioNQuadsParserFactory;
import org.semanticweb.owlapi.rio.RioNTriplesParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyFactoryImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl;
import uk.ac.manchester.cs.owl.owlapi.concurrent.NoOpReadWriteLock;
import uk.ac.manchester.cs.owl.owlapi.concurrent.NonConcurrentOWLOntologyBuilder;

import java.util.Optional;

@Component
public class OntologyManagerProvider {

    private static final Logger logger = LoggerFactory.getLogger(OntologyManagerProvider.class);

    public OWLOntologyManager getEmptyOntologyManager() {
        return OWLManager.createOWLOntologyManager();
    }

    public OWLOntologyManager getOntologyManagerWithLoadImports() {
        return getOntologyManagerWithLoadImports(new LoadedOntologyCache((path) -> Optional.empty()));
    }

    public OWLOntologyManager getOntologyManagerWithLoadImports(LoadedOntologyCache loadedOntologyCache) {
        var man = getCustomOntologyManager(loadedOntologyCache);

        // Configure silent handling of missing/anonymous imports
        var config = new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
                        .setRepairIllegalPunnings(false);
        man.setOntologyLoaderConfiguration(config);

        return man;
    }

    public OWLOntologyManager getOntologyManagerWithIgnoredImports() {
        var man = getCustomOntologyManager(new LoadedOntologyCache((path) -> Optional.empty()));

        // Configure silent handling of missing/anonymous imports
        var config = new OWLOntologyLoaderConfiguration() {
            @Override
            public boolean isIgnoredImport(IRI iri) {
                return true;
            }

            @Override
            public boolean shouldRepairIllegalPunnings() {
                return false;
            }

            @Override
            public boolean isStrict() {
                return false;
            }
        };
        man.setOntologyLoaderConfiguration(config);

        return man;
    }

    private OWLOntologyManager getCustomOntologyManager(LoadedOntologyCache loadedOntologyCache) {
        var man = new OWLOntologyManagerImpl(new OWLDataFactoryImpl(), new NoOpReadWriteLock()) {

            private int cacheHits = 0;

            @Override
            public void makeLoadImportRequest(
                    OWLImportsDeclaration declaration, OWLOntologyLoaderConfiguration configuration) {
                var config = getOntologyLoaderConfiguration();
                super.makeLoadImportRequest(declaration, config);
            }

            @Override
            protected OWLOntology actualParse(OWLOntologyDocumentSource documentSource, OWLOntologyLoaderConfiguration configuration) throws OWLOntologyCreationException {
                // This is where the loading actually happens
                var loadedOnt = loadedOntologyCache.get(documentSource);
                if(loadedOnt.isPresent()) {
                    var copy = copyOntology(loadedOnt.get(), OntologyCopy.DEEP);
                    loadedOnt.get().getImportsDeclarations().forEach(decl -> makeLoadImportRequest(decl, configuration));
                    return copy;
                }
                var freshlyLoadedOnt = super.actualParse(documentSource, configuration);
                loadedOntologyCache.put(documentSource, freshlyLoadedOnt);
                return freshlyLoadedOnt;
            }
        };
        man.getOntologyFactories().add(new OWLOntologyFactoryImpl(new NonConcurrentOWLOntologyBuilder()));

        // Add parsers that we care about
        var ontologyParsers = man.getOntologyParsers();
        ontologyParsers.add(new RioBinaryRdfParserFactory());
        ontologyParsers.add(new RioNQuadsParserFactory());
        ontologyParsers.add(new RioJsonLDParserFactory());
        ontologyParsers.add(new RioNTriplesParserFactory());
        ontologyParsers.add(new OBOFormatOWLAPIParserFactory());
        ontologyParsers.add(new OWLFunctionalSyntaxOWLParserFactory());
        ontologyParsers.add(new ManchesterOWLSyntaxOntologyParserFactory());
        ontologyParsers.add(new TurtleOntologyParserFactory());
        ontologyParsers.add(new OWLXMLParserFactory());
        ontologyParsers.add(new RDFXMLParserFactory());

        return man;
    }
}
