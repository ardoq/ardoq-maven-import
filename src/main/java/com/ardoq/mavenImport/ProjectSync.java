package com.ardoq.mavenImport;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;
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
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;

import com.ardoq.mavenImport.util.Booter;
import com.ardoq.mavenImport.util.ConsoleDependencyGraphDumper;
import com.ardoq.model.Component;
import com.ardoq.model.Reference;
import com.ardoq.util.SyncUtil;

public class ProjectSync {
	

	final SyncUtil ardoqSync;
	final ArtifactSync artifactSync;
	final String COMPONENT_TYPE_PROJECT;

	Map<String, String> componentNameIdMap;

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

		componentNameIdMap = new HashMap<String, String>();
	}

	public void syncProjects(List<String> projects) throws Exception {
		for (String project : projects) {
			syncProject(project);
		}
		syncRelations();
	}

	private String syncProject(String projectStr) throws ArtifactResolutionException  {

		Artifact artifact = new DefaultArtifact(projectStr);
		Artifact pomArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion());
		ArtifactRequest artifactreq = new ArtifactRequest();
		artifactreq.setArtifact(pomArtifact);
		artifactreq.setRepositories(repos);
		ArtifactResult artifactres = system.resolveArtifact(session, artifactreq);
		File pomFile = artifactres.getArtifact().getFile();
		MavenProject mavenProject = loadProject(pomFile);

		return syncProject(mavenProject);
	}

	/**
	 * Returns Ardoq project component ID
	 * @param project
	 * @return
	 */
	private String syncProject(MavenProject project) {
		String componentName = project.getName();

		if (componentNameIdMap.containsKey(componentName)) {
			return componentNameIdMap.get(componentName);
		}

		Component ardoqProjectComponent = new Component(componentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_PROJECT);

		ardoqProjectComponent.setDescription(buildProjectDescription(project));

		Map<String, Object> fields = new HashMap<String, Object>();
		fields.put("groupId", project.getGroupId());
		fields.put("artifactId", project.getArtifactId());
		fields.put("version", project.getVersion());

		ardoqProjectComponent.setFields(fields);
		ardoqSync.addComponent(ardoqProjectComponent);
		componentNameIdMap.put(componentName, ardoqProjectComponent.getId());

		Map<String, Integer> refTypes = ardoqSync.getModel().getReferenceTypes();

		syncProjectArtifact(project, ardoqProjectComponent, refTypes);
		syncProjectParent(project, ardoqProjectComponent, refTypes);
		syncProjectModules(project, ardoqProjectComponent, refTypes);

		return ardoqProjectComponent.getId();
	}

	private String buildProjectDescription(MavenProject project) {
		// TODO: add url, organization, developers, contributors, mailing lists, etc..

		String description = "#Description1\n\n" + project.getDescription();

		if (!project.getLicenses().isEmpty()) {
			description += "\nLicenses\n----\n\n";
			for (License license : project.getLicenses()) {
				description += " * " + license.getName() + "\n";
			}
		}
		return description;
	}

	/**
	 * NB! only modules named the same as the artifact will be synced
	 * @param project
	 * @param ardoqProjectComponent
	 * @param refTypes
	 * @throws DependencyCollectionException
	 */
	private void syncProjectModules(MavenProject project, Component ardoqProjectComponent, Map<String, Integer> refTypes) {
		for (String module : project.getModules()) {
			try {
				String groupId = project.getGroupId();
				String artifactId = module;
				String version = project.getVersion();

				String id = groupId + ":" + artifactId + ":" + version;
				String moduleComponentId = syncProject(id);

				if(moduleComponentId!=null) {
					int refType = refTypes.get("Module");
					Reference ref = new Reference(ardoqSync.getWorkspace().getId(), "artifact", ardoqProjectComponent.getId(), moduleComponentId, refType);
					ardoqSync.addReference(ref);
				}
				else{
					System.err.println("Error adding reference from "+ardoqProjectComponent.getId()+ " "+moduleComponentId);
				}

			} catch (ArtifactResolutionException e) {
				System.out.println("***************************************************************");
				System.out.println("* Error syncing Maven module " + module + " of " + project.getName());
				System.out.println("* This tool assumes that the module name equals the artifactId. ");
				System.out.println("* -> ignoring and carrying on.. ");
				System.out.println("***************************************************************");
			}
		}
	}

	private void syncProjectArtifact(MavenProject project, Component ardoqProjectComponent, Map<String, Integer> refTypes) {
		int refType = refTypes.get("Dependency");
		DefaultArtifact artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "pom", project.getVersion());
		syncProjectDependencies(artifact);

		String sourceId = ardoqProjectComponent.getId();
		String targetId = artifactSync.getComponentIdFromArtifact(artifact);

		if(sourceId!=null && targetId!=null){
			System.out.println("adding reference from project to artifact " + sourceId + " " + ardoqProjectComponent.getName() + " " + targetId + " "+ artifact.getArtifactId());
			Reference ref = new Reference(ardoqSync.getWorkspace().getId(), "artifact", ardoqProjectComponent.getId(), targetId, refType);
			ardoqSync.addReference(ref);
		}
		else{
			System.err.println("Error creating reference from "+ardoqProjectComponent.getName()+" to "+artifact.getArtifactId()+".. sourceId: "+sourceId+", targetId: "+targetId);
		}
	}

	private void syncProjectParent(MavenProject project, Component ardoqProjectComponent, Map<String, Integer> refTypes) {
		Parent parent = project.getModel().getParent();
		if (parent != null) {
			try {
				String parentComponentId = syncProject(parent.getId());

				if(ardoqProjectComponent.getId()!=null && parentComponentId!=null) {
					System.out.println("reference relation from project to parent " + ardoqProjectComponent.getId() + " " + parentComponentId);
					int refTypeParent = refTypes.get("Parent");
					Reference parentRef = new Reference(ardoqSync.getWorkspace().getId(), "artifact", ardoqProjectComponent.getId(), parentComponentId, refTypeParent);
					ardoqSync.addReference(parentRef);
				}
				else{
					System.err.println("Error creating reference from "+ardoqProjectComponent.getId() + " to " + parentComponentId);
				}
			} catch (ArtifactResolutionException e) {
				throw new RuntimeException("Error reading Maven project parent: "+parent.getId(),e);
			}
		}
	}

	private void syncProjectDependencies(Artifact artifact) {
		try {
			CollectRequest collectRequest = new CollectRequest();
			collectRequest.setRoot(new Dependency(artifact, ""));
			collectRequest.setRepositories(repos);
			CollectResult collectResult = system.collectDependencies(session, collectRequest);

			collectResult.getRoot().accept(new ConsoleDependencyGraphDumper());
			collectResult.getRoot().accept(artifactSync);
		} catch (DependencyCollectionException e) {
			throw new RuntimeException(e);
		}
	}

	private void syncRelations() {
		artifactSync.syncReferences();
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
