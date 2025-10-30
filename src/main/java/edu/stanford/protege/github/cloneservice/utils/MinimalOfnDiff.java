package edu.stanford.protege.github.cloneservice.utils;

import java.util.*;
import static java.util.stream.Collectors.toCollection;

/** Build “minimal before/after” OFN documents from two FsDocs. */
public final class MinimalOfnDiff {

    /** A minimal document containing just enough to apply the diff. */
    public static final class FsMinimalDoc {
        public final List<FsParser.PrefixDecl> prefixes; // keep as-is
        public final String ontologyIri;   // as written (may be null)
        public final String versionIri;    // as written (may be null)
        public final List<String> imports; // as written; each already includes <...> or abbrev
        public final List<FsParser.Axiom> axioms; // verbatim head + content

        public FsMinimalDoc(List<FsParser.PrefixDecl> prefixes,
                            String ontologyIri,
                            String versionIri,
                            List<String> imports,
                            List<FsParser.Axiom> axioms) {
            this.prefixes   = List.copyOf(prefixes);
            this.ontologyIri = ontologyIri;
            this.versionIri  = versionIri;
            this.imports    = List.copyOf(imports);
            this.axioms     = List.copyOf(axioms);
        }
    }

    /** Holder for the two minimal docs. */
    public static final class BeforeAfter {
        public final FsMinimalDoc before;
        public final FsMinimalDoc after;
        public BeforeAfter(FsMinimalDoc before, FsMinimalDoc after) {
            this.before = before;
            this.after  = after;
        }
    }

    /** Compute minimal docs:
     *  - beforeMinimal: prefixes/ontology from BEFORE, plus (imports/axioms in BEFORE not in AFTER).
     *  - afterMinimal : prefixes/ontology from AFTER,  plus (imports/axioms in AFTER  not in BEFORE).
     */
    public static BeforeAfter build(FsParser.FsDoc before, FsParser.FsDoc after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after,  "after");

        // ---- Imports: compute set difference, preserve original order ----
        Set<String> afterImportsSet  = new HashSet<>(after.imports);
        List<String> removedImports  = before.imports.stream()
                .filter(x -> !afterImportsSet.contains(x))
                .collect(toCollection(LinkedHashSet::new))  // keep order & uniqueness
                .stream().toList();

        Set<String> beforeImportsSet = new HashSet<>(before.imports);
        List<String> addedImports    = after.imports.stream()
                .filter(x -> !beforeImportsSet.contains(x))
                .collect(toCollection(LinkedHashSet::new))
                .stream().toList();

        // ---- Axioms: compute set difference using a stable printable key, preserve order ----
        Set<String> afterAxiomKeys   = axiomKeySet(after.axioms);
        List<FsParser.Axiom> removedAxioms = before.axioms.stream()
                .filter(ax -> !afterAxiomKeys.contains(axKey(ax)))
                .collect(toCollection(LinkedHashSet::new))
                .stream().toList();

        Set<String> beforeAxiomKeys  = axiomKeySet(before.axioms);
        List<FsParser.Axiom> addedAxioms   = after.axioms.stream()
                .filter(ax -> !beforeAxiomKeys.contains(axKey(ax)))
                .collect(toCollection(LinkedHashSet::new))
                .stream().toList();

        // ---- Prefixes: carry from each side as-is (you can change to union/intersection if you prefer) ----
        FsMinimalDoc beforeMinimal = new FsMinimalDoc(
                before.prefixes,
                before.ontologyIri,
                before.versionIri,
                removedImports,
                removedAxioms
        );

        FsMinimalDoc afterMinimal = new FsMinimalDoc(
                after.prefixes,
                after.ontologyIri,
                after.versionIri,
                addedImports,
                addedAxioms
        );

        return new BeforeAfter(beforeMinimal, afterMinimal);
    }

    // Key used for equality of axioms in diffs; uses final printed form (head + "(" + content + ")")
    private static String axKey(FsParser.Axiom ax) {
        return ax.head + "(" + ax.content + ")";
    }
    private static Set<String> axiomKeySet(List<FsParser.Axiom> axioms) {
        Set<String> s = new HashSet<>(axioms.size() * 2);
        for (FsParser.Axiom ax : axioms) s.add(axKey(ax));
        return s;
    }

    /** Render a minimal document back to OFN, preserving verbatim axiom bodies. */
    public static final class Renderer {
        private final String newline;

        public Renderer() { this(System.lineSeparator()); }
        public Renderer(String newline) { this.newline = newline; }

        public String render(FsMinimalDoc doc) {
            StringBuilder sb = new StringBuilder(4096);

            // Prefix declarations
            for (FsParser.PrefixDecl p : doc.prefixes) {
                sb.append("Prefix(")
                        .append(p.nameWithColon)
                        .append("=")
                        .append("<").append(p.iri).append(">)")
                        .append(newline);
            }

            // Ontology header
            sb.append("Ontology(");
            boolean wroteAnyHeaderIri = false;
            if (doc.ontologyIri != null && !doc.ontologyIri.isBlank()) {
                sb.append(doc.ontologyIri);
                wroteAnyHeaderIri = true;
            }
            if (doc.versionIri != null && !doc.versionIri.isBlank()) {
                if (wroteAnyHeaderIri) sb.append(' ');
                sb.append(doc.versionIri);
                wroteAnyHeaderIri = true;
            }
            if (wroteAnyHeaderIri) sb.append(newline);

            // Imports first (one per line)
            for (String imp : doc.imports) {
                sb.append("Import(").append(imp).append(")").append(newline);
            }

            // Axioms (verbatim bodies via Axiom.toString())
            for (FsParser.Axiom ax : doc.axioms) {
                sb.append(ax.toString()).append(newline);
            }

            // closing )
            sb.append(")");
            return sb.toString();
        }
    }
}

