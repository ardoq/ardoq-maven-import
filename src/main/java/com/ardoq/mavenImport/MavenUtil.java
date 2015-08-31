package com.ardoq.mavenImport;

import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.List;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import com.ardoq.mavenImport.util.Booter;

public class MavenUtil {

    final RepositorySystem system;
    final RepositorySystemSession session;
    final List<RemoteRepository> repos;

    public MavenUtil(PrintStream out){
        this.system = Booter.newRepositorySystem();
        this.session = Booter.newRepositorySystemSession(system, out);
        this.repos = Booter.newRepositories(system, session);
    }

    public RepositorySystem getSystem() {
        return system;
    }

    public RepositorySystemSession getSession() {
        return session;
    }

    public List<RemoteRepository> getRepos() {
        return repos;
    }

    public void addRepository(String url){
        RemoteRepository.Builder b = new RemoteRepository.Builder("custom","default",url);
        this.repos.add(b.build());
    }

    public void addRepository(String url, String username, String password) {
        RemoteRepository.Builder b = new RemoteRepository.Builder("custom","default",url);
        AuthenticationBuilder authBuilder = new AuthenticationBuilder();
        authBuilder.addUsername(username);
        authBuilder.addPassword(password);
        b.setAuthentication(authBuilder.build());
        this.repos.add(b.build());
    }

    public MavenProject loadProject(String projectStr) throws ArtifactResolutionException {
        Artifact artifact = new DefaultArtifact(projectStr);
        Artifact pomArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion());
        ArtifactRequest artifactreq = new ArtifactRequest();
        artifactreq.setArtifact(pomArtifact);
        artifactreq.setRepositories(repos);
        ArtifactResult artifactres = system.resolveArtifact(session, artifactreq);
        File pomFile = artifactres.getArtifact().getFile();
        MavenProject mavenProject = loadProject(pomFile);
        return mavenProject;
    }

    private static MavenProject loadProject(File pomFile) {
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        FileReader reader = null;

        try {
            reader = new FileReader(pomFile);
            Model model = mavenReader.read(reader);
            model.setPomFile(pomFile);

            return new MavenProject(model);
        } catch (Exception e) {
            throw new RuntimeException("Error reading Maven project from file",e);
        } finally {
            IOUtil.close(reader);
        }
    }


}
