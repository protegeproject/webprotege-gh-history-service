package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class LoadedOboDocCache {

    private static final Logger logger = LoggerFactory.getLogger(LoadedOboDocCache.class);

    private final Table<IRI, String, FastOboDiff.OboDoc> cache = HashBasedTable.create();

    private final BlobIdResolver blobIdResolver;

    private int cacheHits = 0;

    public LoadedOboDocCache(BlobIdResolver blobIdResolver) {
        this.blobIdResolver = blobIdResolver;
    }

    public void put(OWLOntologyDocumentSource documentSource, FastOboDiff.OboDoc doc, int index) {
        var docIri = documentSource.getDocumentIRI();
        if(!Objects.equals(docIri.getScheme(), "file" )) {
            return;
        }
        var rows = cache.row(docIri);
        rows.forEach((blobId, ont) -> {
            cache.remove(docIri, blobId);
        });
        var blobId = getBlobFromDocumentSource(docIri, index);
        blobId.ifPresent(theBlobId -> cache.put(docIri, theBlobId, doc));
    }

    private Optional<String> getBlobFromDocumentSource(IRI docIri, int index) {
        if(!Objects.equals(docIri.getScheme(), "file" )) {
            return Optional.empty();
        }
        return blobIdResolver.getBlobId(Path.of(docIri.toURI()), index);
    }

    public Optional<FastOboDiff.OboDoc> get(OWLOntologyDocumentSource documentSource, int index) {
        var docIri = documentSource.getDocumentIRI();
        var blobId = getBlobFromDocumentSource(docIri, index);
        if(blobId.isEmpty()) {
            return Optional.empty();
        }
        Optional<FastOboDiff.OboDoc> doc = Optional.ofNullable(cache.get(docIri, blobId.orElse("" )));
        if(doc.isPresent()) {
            cacheHits++;
            logger.debug("Cache hit for OBO doc load request. [cacheHits={}, documentSource={}]", cacheHits, documentSource);
        }
        else {
            logger.debug("Cache miss for OBO doc load request. [documentSource={}]", documentSource);
        }
        return doc;
    }
}
