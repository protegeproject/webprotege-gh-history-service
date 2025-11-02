package edu.stanford.protege.github.cloneservice.utils;

import com.google.common.collect.ImmutableList;
import edu.stanford.protege.github.cloneservice.model.AxiomChange;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

class OntologiesDifferenceCalculator {

    private final OntologyDifferencesCalculator differenceCalculator;

    private final OntologyLoader ontologyLoader;

    OntologiesDifferenceCalculator(OntologyDifferencesCalculator differenceCalculator, OntologyLoader ontologyLoader) {
        this.differenceCalculator = differenceCalculator;
        this.ontologyLoader = ontologyLoader;
    }

    /**
     * Calculates axiom changes between current and previous commit ontologies
     *
     * @param baselineCommitOntologies ontologies from the baseline commit
     * @param parentCommitOntologies   ontologies from the ancestor commit
     * @return list of axiom changes between commits
     */
    @Nonnull
    public List<AxiomChange> calculateAxiomChangesBetweenOntologies(
            @Nonnull List<OWLOntology> baselineCommitOntologies,
            @Nonnull List<OWLOntology> parentCommitOntologies) {

        var baselinesByIri = new HashMap<IRI, OWLOntology>();
        var ancestorsByIri = new HashMap<IRI, OWLOntology>();

        baselineCommitOntologies.forEach(ont -> baselinesByIri.put(ontologyKey(ont), ont));
        parentCommitOntologies.forEach(ont -> ancestorsByIri.put(ontologyKey(ont), ont));

        var pairs = new ArrayList<OntologyPair>();

        // 1) Matched pairs (remove matched keys from both maps)
        var matchedKeys = new HashSet<>(baselinesByIri.keySet());
        matchedKeys.retainAll(ancestorsByIri.keySet());
        for(var iri : matchedKeys) {
            pairs.add(new OntologyPair(baselinesByIri.remove(iri), ancestorsByIri.remove(iri)));
        }

        // 2) Parent-only → removed; baseline-only → added
        ancestorsByIri.values().forEach(p -> pairs.add(new OntologyPair(ontologyLoader.getEmptyOntology(), p)));
        baselinesByIri.values().forEach(c -> pairs.add(new OntologyPair(c, ontologyLoader.getEmptyOntology())));

        return pairs.stream()
                .flatMap(pair -> differenceCalculator
                        .calculateAxiomChanges(
                                pair.baseline,
                                pair.parent,
                                // Prefer baseline’s real ID if not anonymous; else use ancestor’s; else synthesize
                                effectiveOntologyId(pair.baseline, pair.parent))
                        .stream())
                .collect(ImmutableList.toImmutableList());
    }

    private record OntologyPair(OWLOntology baseline, OWLOntology parent) {

    }


    private static IRI ontologyKey(OWLOntology ont) {
        var id = ont.getOntologyID();
        return id.getOntologyIRI()
                .or(() -> id.getDefaultDocumentIRI().or(IRI.generateDocumentIRI()));
    }

    private static OWLOntologyID effectiveOntologyId(OWLOntology baseline, OWLOntology parent) {
        var cid = baseline.getOntologyID();
        if(!cid.isAnonymous()) {
            return cid;
        }
        var pid = parent.getOntologyID();
        if(!pid.isAnonymous()) {
            return pid;
        }
        // Fallback: derive from the baseline key to keep stable-ish identity
        var iri = baseline.getOntologyID().getDefaultDocumentIRI()
                .or(IRI::generateDocumentIRI);
        return new OWLOntologyID(iri, null);
    }


}
