package edu.stanford.protege.github.cloneservice;

import edu.stanford.protege.commitnavigator.GitHubRepository;
import edu.stanford.protege.commitnavigator.config.RepositoryConfig;
import edu.stanford.protege.commitnavigator.impl.GitHubRepositoryImpl;
import edu.stanford.protege.commitnavigator.model.BranchCoordinates;
import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.impl.AuthenticationManagerImpl;
import edu.stanford.protege.github.cloneservice.model.RelativeFilePath;
import edu.stanford.protege.github.cloneservice.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public class Testing {

    private static final Logger logger = LoggerFactory.getLogger(Testing.class);

    private static int counter = 0;

    public static void main(String[] args) throws Exception {

//        Enumeration<URL> e = Thread.currentThread().getContextClassLoader()
//                .getResources("META-INF/services/org.slf4j.spi.SLF4JServiceProvider");
//        while (e.hasMoreElements()) {
//            System.out.println("SLF4J provider file from: " + e.nextElement());
//        }
//        System.out.println("SimpleServiceProvider class from: " +
//                           org.slf4j.simple.SimpleServiceProvider.class.getProtectionDomain().getCodeSource().getLocation());
//

        System.out.println("STDOUT test");  // ensure console output works
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("probe");
        log.info("SLF4J test");

        String owner = "monarch-initiative";
        String repoName = "mondo";
        String acronym = "mondo";
        String extension = ".obo";
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
        OntologyDifferencesCalculator calc = new OntologyDifferencesCalculator();
        OntologyHistoryAnalyzer analyzer = new OntologyHistoryAnalyzer(loader, calc, Duration.ofHours(5), provider);
        analyzer.getCommitHistory(new RelativeFilePath("src/ontology/"+acronym+"-edit" + extension), repo, new OntologyHistoryAnalyzerProgressMonitor() {
//        analyzer.getCommitHistory(new RelativeFilePath("src/ontology/test-ont.owl"), repo, new OntologyHistoryAnalyzerProgressMonitor() {


            @Override
            public void processingHistoryStarted(String repositoryUrl, int numberOfCommits) {
                logger.info("Processing {} commits from {}", numberOfCommits, repositoryUrl);
            }

            @Override
            public void processingHistoryFinished() {

            }

            private long startTime, endTime;

            @Override
            public void processingCommitStarted(CommitMetadata commitMetadata, List<String> changedFilePaths) {
                counter++;
                startTime = System.currentTimeMillis();
                logger.info("Processing commit {} [changed files={}]", commitMetadata.commitHash(), changedFilePaths);
            }

            @Override
            public void processingCommitFinished(CommitMetadata commitMetadata) {
                endTime = System.currentTimeMillis();
                logger.info("Processed commit #{} in {}ms [{}]", counter, (endTime - startTime), commitMetadata.commitHash());
            }
        }, new OntologyChangesCollectingHandler());
    }
}
