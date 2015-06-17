package com.ardoq.mavenImport;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
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
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import com.ardoq.mavenImport.util.Booter;
import com.ardoq.mavenImport.util.ConsoleDependencyGraphDumper;
import com.ardoq.model.Component;
import com.ardoq.util.SyncUtil;

public class ProjectSync {

    final SyncUtil ardoqSync;
    final ArtifactSync artifactSync;
    final String COMPONENT_TYPE_PROJECT;

    Map<String,String> componentNameIdMap;


	final RepositorySystem system;
	final RepositorySystemSession session;
	final List<RemoteRepository> repos;


	public ProjectSync(SyncUtil ardoqSync) {
    	this.ardoqSync = ardoqSync;
    	this.artifactSync = new ArtifactSync(ardoqSync);

		this.system = Booter.newRepositorySystem();
		this.session = Booter.newRepositorySystemSession(system);
		this.repos = Booter.newRepositories(system, session);

    	COMPONENT_TYPE_PROJECT = ardoqSync.getModel().getComponentTypeByName("Project");

    	componentNameIdMap = new HashMap<String,String>();
    }


	public void syncProjects(List<String> projects) throws Exception{
		for(String project:projects) {
			syncProject(project);
		}
		syncRelations();
	}




	private void syncProject(String projectStr) throws Exception{

		File pomFile = new File(projectStr);
		if(!pomFile.exists()){
			//try parsing input as an artifact descriptor
			try{
				Artifact artifact = new DefaultArtifact(projectStr);
				Artifact pomArtifact = new DefaultArtifact(artifact.getGroupId(),artifact.getArtifactId(),"pom",artifact.getVersion());
				ArtifactRequest artifactreq = new ArtifactRequest();
				artifactreq.setArtifact(pomArtifact);
				artifactreq.setRepositories(repos);
				ArtifactResult artifactres = system.resolveArtifact( session, artifactreq );
				pomFile = artifactres.getArtifact().getFile();
			}
			catch(IllegalArgumentException ignore){
				pomFile = null;
			}
		}
		if(pomFile==null || !pomFile.exists()){
			throw new IllegalArgumentException("Failed to locate Maven project '"+projectStr+"' as either file or artifactDescriptor.");
		}

		MavenProject mavenProject = loadProject(pomFile);
		syncProject(mavenProject);
	}


	private void syncProject(MavenProject project) throws Exception{
    	String componentName = project.getName();

    	if(componentNameIdMap.containsKey(componentName)){
    		return;
    	}

    	Component comp = new Component(componentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_PROJECT);
    	comp.setDescription(project.getDescription());

    	Map<String,Object> fields = new HashMap<String,Object>();
    	fields.put("groupId", project.getGroupId());
    	fields.put("artifactId", project.getArtifactId());
    	fields.put("version", project.getVersion());

		syncProjectDependencies(project);

    	Parent parent = project.getModel().getParent();
    	if(parent!=null){
    		syncProject(parent.getId());
    	}

//    	for(String module : project.getModules()){
//
//    	}

	}



	private void syncProjectDependencies(MavenProject project) throws DependencyCollectionException {
		CollectRequest collectRequest = new CollectRequest();
		DefaultArtifact artifact = new DefaultArtifact(project.getGroupId(),project.getArtifactId(),"pom",project.getVersion());
		collectRequest.setRoot(new Dependency(artifact,""));
		collectRequest.setRepositories(repos);
		CollectResult collectResult = system.collectDependencies(session, collectRequest);

		collectResult.getRoot().accept(new ConsoleDependencyGraphDumper());
		collectResult.getRoot().accept(artifactSync);
	}


	private void syncRelations() {
		artifactSync.syncReferences();
	}



	private static MavenProject loadProject(File pomFile) throws IOException, XmlPullParserException {
		MavenXpp3Reader mavenReader = new MavenXpp3Reader();
		FileReader reader = null;

		try {
			reader = new FileReader(pomFile);
			Model model = mavenReader.read(reader);
			model.setPomFile(pomFile);

			return new MavenProject(model);
		} finally {
			reader.close();
		}
	}


}
