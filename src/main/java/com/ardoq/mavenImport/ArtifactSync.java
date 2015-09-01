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
    final String COMPONENT_TYPE_GROUP;
    final String COMPONENT_TYPE_ARTIFACT;
    final String COMPONENT_TYPE_VERSION;

    Map<String, String> componentNameIdMap;
    Map<String, Reference> references;

    public ArtifactSync(SyncUtil ardoqSync) {
        this.ardoqSync = ardoqSync;

        COMPONENT_TYPE_GROUP = ardoqSync.getModel().getComponentTypeByName("Group");
        COMPONENT_TYPE_ARTIFACT = ardoqSync.getModel().getComponentTypeByName("Artifact");
        COMPONENT_TYPE_VERSION = ardoqSync.getModel().getComponentTypeByName("Version");

        componentNameIdMap = new HashMap<String, String>();
        references = new HashMap<String, Reference>();
    }

    public boolean visitEnter(DependencyNode node) {
        Artifact artifact = node.getArtifact();
        String artifactVersionComponentName = getArtifactVersionComponentName(artifact);

        if (componentNameIdMap.containsKey(artifactVersionComponentName)) {
            return true;
        }

        String artifactCompId = addArtifact(artifact);
        Component versionComp = new Component(artifactVersionComponentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_VERSION);
        versionComp.setParent(artifactCompId);

        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("groupId", node.getArtifact().getGroupId());
        fields.put("artifactId", node.getArtifact().getArtifactId());
        fields.put("version", node.getArtifact().getVersion());
        versionComp.setFields(fields);

        System.out.println("Adding Version component "+artifactVersionComponentName+" of type "+COMPONENT_TYPE_VERSION+ ", parent: "+artifactCompId);
        versionComp = ardoqSync.addComponent(versionComp);

        String versionCompId = versionComp.getId();
        if(versionCompId == null){
            throw new RuntimeException("Version Component doesn't have an ID");
        }

        componentNameIdMap.put(artifactVersionComponentName, versionCompId);

        return true;
    }

    /**
     * Adds the artifact version (Version in Ardoq) as a child of an Artifact node.
     * @param artifact
     * @return artifact component ID
     */
    private String addArtifact(Artifact artifact) {
        String artifactComponentName = getArtifactComponentName(artifact);
        String artifactCompId = componentNameIdMap.get(artifactComponentName);
        if(artifactCompId!=null){
            return artifactCompId;
        }

        String artifactGroupCompId = addArtifactGroup(artifact);
        Component artifactComp = new Component(artifactComponentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_ARTIFACT);
        artifactComp.setParent(artifactGroupCompId);

        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("groupId", artifact.getGroupId());
        fields.put("artifactId", artifact.getArtifactId());
        artifactComp.setFields(fields);

        System.out.println("Adding Artifact component "+artifactComponentName+" of type "+COMPONENT_TYPE_ARTIFACT+", parent: "+artifactGroupCompId);
        artifactComp = ardoqSync.addComponent(artifactComp);

        artifactCompId = artifactComp.getId();
        if(artifactCompId == null){
            throw new RuntimeException("Artifact Component doesn't have an ID");
        }

        componentNameIdMap.put(artifactComponentName, artifactCompId);

        return artifactCompId;
    }

    /**
     * Adds an Artifact as a child of an Group node in Ardoq.
     * @param artifact
     * @return artifactGroup ID
     */
    private String addArtifactGroup(Artifact artifact) {
        String artifactGroupComponentName = getArtifactGroupComponentName(artifact);
        String groupCompId = componentNameIdMap.get(artifactGroupComponentName);
        if (groupCompId!=null){
            return groupCompId;
        }

        Component groupComp = new Component(artifactGroupComponentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_GROUP);

        Map<String, Object> fields = new HashMap<String, Object>();
        fields.put("groupId", artifact.getGroupId());
        groupComp.setFields(fields);

        System.out.println("Adding Artifact component "+artifactGroupComponentName+" of type "+COMPONENT_TYPE_GROUP);
        groupComp = ardoqSync.addComponent(groupComp);

        groupCompId = groupComp.getId();
        if(groupCompId == null){
            throw new RuntimeException("Artifact Component doesn't have an ID");
        }

        componentNameIdMap.put(artifactGroupComponentName, groupCompId);

        return groupCompId;
    }




    public boolean visitLeave(DependencyNode node) {
        Map<String, Integer> refTypes = ardoqSync.getModel().getReferenceTypes();
        int refTypeDep = refTypes.get("Dependency");
        int refTypeTest = refTypes.get("Test");

        Artifact sourceArtifact = node.getArtifact();
        String sourceName = getArtifactVersionComponentName(sourceArtifact);
        String sourceId = componentNameIdMap.get(sourceName);
        if(sourceId==null) {
            System.err.println("Source "+sourceName+ " not found.");
            return false;
        }

        for (DependencyNode child : node.getChildren()) {
            Artifact targetArtifact = child.getArtifact();
            String targetName = getArtifactVersionComponentName(targetArtifact);
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
        references.clear();
    }

    public static String getArtifactGroupComponentName(Artifact artifact) {
        return artifact.getGroupId();
    }

    public static String getArtifactComponentName(Artifact artifact) {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();

        String componentName = groupId + ":" + artifactId;
        return componentName;
    }

    public static String getArtifactVersionComponentName(Artifact artifact) {
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();
        String version = artifact.getVersion();

        String componentName = groupId + ":" + artifactId + ":" + version;
        return componentName;
    }

    public String getComponentIdFromArtifact(Artifact artifact) {
        String artifactName = getArtifactVersionComponentName(artifact);
        return componentNameIdMap.get(artifactName);
    }

}