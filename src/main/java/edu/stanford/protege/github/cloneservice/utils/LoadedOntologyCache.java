package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;

public class LoadedOntologyCache {

    private static final Logger logger = LoggerFactory.getLogger(LoadedOntologyCache.class);

    private final Table<IRI, String, OWLOntology> cache = HashBasedTable.create();

    private final BlobIdResolver blobIdResolver;

    private int cacheHits = 0;

    public LoadedOntologyCache(BlobIdResolver blobIdResolver) {
        this.blobIdResolver = blobIdResolver;
    }

    public void put(OWLOntologyDocumentSource documentSource, OWLOntology ontology) {
        var docIri = documentSource.getDocumentIRI();
        if(!docIri.getScheme().equals("file")) {
            return;
        }
        var rows = cache.row(docIri);
        rows.forEach((blobId, ont) -> {
            cache.remove(docIri, blobId);
        });
        var blobId = getBlobFromDocumentSource(docIri);
        if(blobId.isPresent()) {
            cache.put(docIri, blobId.orElseThrow(), ontology);
        }
    }

    private Optional<String> getBlobFromDocumentSource(IRI docIri) {
        if(!docIri.getScheme().equals("file")) {
            return Optional.empty();
        }
        return blobIdResolver.getBlobId(Path.of(docIri.toURI()));
    }

    public Optional<OWLOntology> get(OWLOntologyDocumentSource documentSource) {
        var docIri = documentSource.getDocumentIRI();
        var blobId = getBlobFromDocumentSource(docIri);
        if(blobId.isEmpty()) {
            return Optional.empty();
        }
        Optional<OWLOntology> owlOntology = Optional.ofNullable(cache.get(docIri, blobId.orElse("" )));
        if(owlOntology.isPresent()) {
            cacheHits++;
            logger.info("Hit cache for load request. [cacheHits={}, documentSource={}]", cacheHits, documentSource);
        }
        return owlOntology;
    }

}
