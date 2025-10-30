package edu.stanford.protege.github.cloneservice.utils;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.functional.parser.OWLFunctionalSyntaxOWLParserFactory;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class TestingOfnPatser {

    public static void main(String[] args) throws IOException, OWLOntologyCreationException, ParseException {
        var content = Files.readString(Path.of("/tmp/hp-edit-2.owl"));
//        FastOfnDiff.OfnParser parser = new LineOrientedOfnParser();
//        var doc = parser.parse(content);
//        System.out.println(doc);
//        System.out.println(doc.bodyLines().size());
//        System.out.println(doc.ontologyIri());
//        doc.prefixLines().forEach(System.out::println);
//        doc.bodyLines().stream().filter(bl -> bl.contains("\n")).limit(50).map(bl -> "&" + bl +"~\n\n").forEach(System.out::println);


            FsParser p = new FsParser(new StringReader(content));
            var fsDoc = p.parse();
            System.out.println("PARSED");

            fsDoc.prefixes.stream().forEach(System.out::println);
            fsDoc.imports.forEach(System.out::println);
            System.out.println(fsDoc.ontologyIri);
            System.out.println(fsDoc.versionIri);
//            fsDoc.axioms.stream().filter(ax -> ax.head.equals("Declaration")).forEach(System.out::println);


        var diff = MinimalOfnDiff.build(fsDoc, fsDoc);
        var renderer = new MinimalOfnDiff.Renderer();
        var before = renderer.render(diff.before);
        var after = renderer.render(diff.after);
        Files.writeString(Path.of("/tmp/before.txt"), before);
        Files.writeString(Path.of("/tmp/after.txt"), after);
    }

    private static OWLOntologyManager createManager() {
        var man = OWLManager.createOWLOntologyManager();
        var parsers = man.getOntologyParsers();
        parsers.clear();
        parsers.add(new OWLFunctionalSyntaxOWLParserFactory());
        return man;
    }

}
