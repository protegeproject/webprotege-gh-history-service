package edu.stanford.protege.github.cloneservice;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import edu.stanford.protege.commitnavigator.CommitNavigatorBuilder;
import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.config.RepositoryConfig;
import edu.stanford.protege.commitnavigator.exceptions.GitHubNavigatorException;
import edu.stanford.protege.commitnavigator.impl.GitHubRepositoryImpl;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.CommitNavigator;
import edu.stanford.protege.commitnavigator.utils.impl.AuthenticationManagerImpl;
import edu.stanford.protege.commitnavigator.utils.impl.CommitNavigatorImpl;
import edu.stanford.protege.github.cloneservice.exception.OntologyComparisonException;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.utils.*;
import org.semanticweb.owlapi.model.OWLOntology;
import uk.ac.manchester.cs.owl.owlapi.OWLOntologyFactoryImpl;
import uk.ac.manchester.cs.owl.owlapi.ParsableOWLOntologyFactory;
import uk.ac.manchester.cs.owl.owlapi.concurrent.NonConcurrentOWLOntologyBuilder;

import java.nio.file.Path;
import java.sql.ConnectionBuilder;
import java.time.Duration;

public class Testing {

    public static void main(String[] args) throws OntologyComparisonException, GitHubNavigatorException {
        String owner = "obophenotype";
        String repoName = "human-phenotype-ontology";
        String acronym = "hp";
        String extension = ".owl";
        Path workingDirectory = Path.of("/tmp/github-repos/9dd84be1-1701-4f12-9fe5-4bd5dc124d2a/" + owner + "/" + repoName);
        RepositoryConfig config = RepositoryConfig.builder(new BranchCoordinates(
                        owner,
                        repoName,
                "master"
        ))
                .authConfig(null)
                .localWorkingDirectory(workingDirectory).build();
        GitHubRepository repo = new GitHubRepositoryImpl(config, new AuthenticationManagerImpl());
        repo.initialize();

        OntologyManagerProvider provider = new OntologyManagerProvider();
        OntologyLoader loader = new OntologyLoader(provider);
        OntologyDifferenceCalculator calc = new OntologyDifferenceCalculator();
        OntologyHistoryAnalyzer analyzer = new OntologyHistoryAnalyzer(loader, calc, Duration.ofHours(5), provider);
        analyzer.getCommitHistory(new RelativeFilePath("src/ontology/"+acronym+"-edit" + extension), repo, new OntologyHistoryAnalyzerProgressMonitor() {
            @Override
            public void processingStarted(CommitMetadata commitMetadata) {

            }

            @Override
            public void processingFinished(CommitMetadata commitMetadata) {

            }
        });
    }
}
