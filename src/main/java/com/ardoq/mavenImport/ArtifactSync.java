package com.ardoq.mavenImport;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import com.ardoq.model.Component;
import com.ardoq.model.Reference;
import com.ardoq.util.SyncUtil;

/**
 * A dependency visitor that dumps the graph to Ardoq.
 */
public class ArtifactSync implements DependencyVisitor {

	final SyncUtil ardoqSync;
	final String COMPONENT_TYPE_ARTIFACT;

	Map<String, String> componentNameIdMap;
	Map<String, Reference> references;

	public ArtifactSync(SyncUtil ardoqSync) {
		this.ardoqSync = ardoqSync;

		COMPONENT_TYPE_ARTIFACT = ardoqSync.getModel().getComponentTypeByName("Artifact");

		componentNameIdMap = new HashMap<String, String>();
		references = new HashMap<String, Reference>();
	}

	public boolean visitEnter(DependencyNode node) {
		String componentName = getArtifactComponentName(node.getArtifact());

		if (componentNameIdMap.containsKey(componentName)) {
			return true;
		}

		Component comp = new Component(componentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_ARTIFACT);

		Map<String, Object> fields = new HashMap<String, Object>();
		fields.put("groupId", node.getArtifact().getGroupId());
		fields.put("artifactId", node.getArtifact().getArtifactId());
		fields.put("version", node.getArtifact().getVersion());

		comp.setFields(fields);
		System.out.println("Adding component "+componentName+" of type "+COMPONENT_TYPE_ARTIFACT);
		ardoqSync.addComponent(comp);
		componentNameIdMap.put(componentName, comp.getId());

		return true;
	}

	public boolean visitLeave(DependencyNode node) {
		Map<String, Integer> refTypes = ardoqSync.getModel().getReferenceTypes();
		int refTypeDep = refTypes.get("Dependency");
		int refTypeTest = refTypes.get("Test");

		Artifact sourceArtifact = node.getArtifact();
		String sourceName = getArtifactComponentName(sourceArtifact);
		String sourceId = componentNameIdMap.get(sourceName);
		if(sourceId==null) {
			System.err.println("Source "+sourceName+ " not found.");
			return false;
		}
		
		for (DependencyNode child : node.getChildren()) {
			Artifact targetArtifact = child.getArtifact();
			String targetName = getArtifactComponentName(targetArtifact);
			String targetId = componentNameIdMap.get(targetName);
			String scope = child.getDependency().getScope();
			int refType = refTypeDep;
			if ("test".equals(scope)) {
				refType = refTypeTest;
			}
			Reference ref = new Reference(ardoqSync.getWorkspace().getId(), scope, sourceId, targetId, refType);
			references.put(sourceId + "," + targetId, ref);
		}
		return true;
	}

	public void syncReferences() {
		for (Reference ref : references.values()) {
			System.out.println("adding ref to sync " + ref.getSource() + " " + ref.getTarget());
			ardoqSync.addReference(ref);
		}
	}

	public static String getArtifactComponentName(Artifact artifact) {
		String groupId = artifact.getGroupId();
		String artifactId = artifact.getArtifactId();
		String version = artifact.getVersion();

		String componentName = groupId + ":" + artifactId + ":" + version;
		return componentName;
	}

	public String getComponentIdFromArtifact(Artifact artifact) {
		String artifactName = getArtifactComponentName(artifact);
		return componentNameIdMap.get(artifactName);
	}

}