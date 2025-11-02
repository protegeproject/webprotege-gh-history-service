package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.Objects;

record LoadedPair(List<OWLOntology> baseline, List<OWLOntology> ancestor) {

    LoadedPair {
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(ancestor);
    }
}
