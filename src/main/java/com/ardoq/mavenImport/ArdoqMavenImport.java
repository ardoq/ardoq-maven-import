package com.ardoq.mavenImport;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.examples.util.Booter;
import org.eclipse.aether.examples.util.ConsoleDependencyGraphDumper;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.installation.InstallRequest;

import com.ardoq.ArdoqClient;
import com.ardoq.util.SyncUtil;

/**
 * Collects the transitive dependencies of an artifact.
 */
public class ArdoqMavenImport {
	
    String apiToken = "71846c3f580a45d19f276fa5e6e589db";
    String apiHost = "https://app.ardoq.com";
    String workspaceName = "Maventest";
    String modelName = "Maven";

	SyncUtil ardoqSync;


	public static void main(String[] args) throws Exception {
		ArdoqMavenImport ardoqMavenImport = new ArdoqMavenImport();
		ardoqMavenImport.syncProject();
	}
	
	
	public ArdoqMavenImport() throws Exception {
		ArdoqClient ardoqClient = new ArdoqClient(apiHost,apiToken);
    	ardoqSync = new SyncUtil(ardoqClient,workspaceName, modelName);
    	

	}
	
	public void syncProject() throws Exception{
		System.out.println("------------------------------------------------------------");
		System.out.println(ArdoqMavenImport.class.getSimpleName());

//		MavenProject proj = loadProject(new File("./pom.xml"));
//		System.out.println(proj);

		RepositorySystem system = Booter.newRepositorySystem();

		RepositorySystemSession session = Booter.newRepositorySystemSession(system);

		Artifact artifact = new DefaultArtifact("org.apache.maven:maven-aether-provider:3.1.0");

		MavenProject proj = loadProject(artifact.getFile());
		
		System.out.println("project: "+proj+" "+artifact.getFile());
		
		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(new Dependency(artifact, ""));
		collectRequest.setRepositories(Booter.newRepositories(system, session));

		CollectResult collectResult = system.collectDependencies(session, collectRequest);

		collectResult.getRoot().accept(new ConsoleDependencyGraphDumper());
		
		
		collectResult.getRoot().accept(new ArdoqDependencyGraphDumper(ardoqSync));
		ardoqSync.deleteNotSyncedItems();
		
	}

	
	
	private static MavenProject loadProject(File pomFile) throws IOException,
			XmlPullParserException {
		MavenProject ret = null;
		MavenXpp3Reader mavenReader = new MavenXpp3Reader();

		if (pomFile != null && pomFile.exists()) {
			FileReader reader = null;

			try {
				reader = new FileReader(pomFile);
				Model model = mavenReader.read(reader);
				model.setPomFile(pomFile);

				ret = new MavenProject(model);
			} finally {
				reader.close();
			}
		}

		return ret;
	}

}