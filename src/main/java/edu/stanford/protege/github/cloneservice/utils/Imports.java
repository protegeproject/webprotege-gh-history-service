package edu.stanford.protege.github.cloneservice.utils;

import org.jetbrains.annotations.NotNull;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class Imports {

    public static @NotNull Set<OWLImportsDeclaration> getOwlImportsDeclarations(List<OWLOntology> baselineOnts) {
        return baselineOnts.stream().flatMap(o -> o.getImportsDeclarations().stream()).collect(Collectors.toSet());
    }

}
