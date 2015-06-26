package com.ardoq.mavenImport;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
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
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

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

	private String syncProject(String projectStr) throws Exception {

		File pomFile = new File(projectStr);
		boolean pomFromFile = pomFile.exists();

		if (!pomFile.exists()) {
			// try parsing input as an artifact descriptor
			try {
				Artifact artifact = new DefaultArtifact(projectStr);
				Artifact pomArtifact = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), "pom", artifact.getVersion());
				ArtifactRequest artifactreq = new ArtifactRequest();
				artifactreq.setArtifact(pomArtifact);
				artifactreq.setRepositories(repos);
				ArtifactResult artifactres = system.resolveArtifact(session, artifactreq);
				pomFile = artifactres.getArtifact().getFile();
			} catch (IllegalArgumentException ignore) {
				pomFile = null;
			}
		}
		if (pomFile == null || !pomFile.exists()) {
			throw new IllegalArgumentException("Failed to locate Maven project '" + projectStr + "' as either file or artifactDescriptor.");
		}

		MavenProject mavenProject = loadProject(pomFile);

		if (pomFromFile) {
			InstallRequest installRequest = new InstallRequest();
			Artifact pomArtifact = new DefaultArtifact(mavenProject.getGroupId(), mavenProject.getArtifactId(), "pom", mavenProject.getVersion());
			pomArtifact = pomArtifact.setFile(pomFile);
			installRequest.addArtifact(pomArtifact);
			system.install(session, installRequest);
		}

		return syncProject(mavenProject);
	}

	private String syncProject(MavenProject project) throws Exception {
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

		// TODO: process modules
		// for(String module : project.getModules()){
		//
		// }

		return ardoqProjectComponent.getId();
	}

	private String buildProjectDescription(MavenProject project) {
		// TODO: add url, organization, developers, contributors, mailing lists,
		// etc..

		String description = "#Description1\n\n" + project.getDescription();

		if (!project.getLicenses().isEmpty()) {
			description += "\nLicenses\n----\n\n";
			for (License license : project.getLicenses()) {
				description += " * " + license.getName() + "\n";
			}
		}
		return description;
	}

	private void syncProjectArtifact(MavenProject project, Component ardoqProjectComponent, Map<String, Integer> refTypes) throws DependencyCollectionException {
		int refType = refTypes.get("Dependency");
		DefaultArtifact artifact = new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "pom", project.getVersion());
		syncProjectDependencies(artifact);

		String targetId = artifactSync.getComponentIdFromArtifact(artifact);

		System.out.println("adding relation from project to artifact " + ardoqProjectComponent.getId() + " " + ardoqProjectComponent.getName() + " " + targetId + " "
				+ artifact.getArtifactId());
		Reference ref = new Reference(ardoqSync.getWorkspace().getId(), "artifact", ardoqProjectComponent.getId(), targetId, refType);
		ardoqSync.addReference(ref);
	}

	private void syncProjectParent(MavenProject project, Component ardoqProjectComponent, Map<String, Integer> refTypes) throws Exception {
		Parent parent = project.getModel().getParent();
		if (parent != null) {
			String parentComponentId = syncProject(parent.getId());

			System.out.println("adding relation from project to parent " + ardoqProjectComponent.getId() + " " + parentComponentId);
			int refTypeParent = refTypes.get("Parent");
			Reference parentRef = new Reference(ardoqSync.getWorkspace().getId(), "artifact", ardoqProjectComponent.getId(), parentComponentId, refTypeParent);
			ardoqSync.addReference(parentRef);
		}
	}

	private void syncProjectDependencies(Artifact artifact) throws DependencyCollectionException {
		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(new Dependency(artifact, ""));
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
