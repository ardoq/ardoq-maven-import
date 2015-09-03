package com.ardoq.mavenImport;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Developer;
import org.apache.maven.model.License;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactResolutionException;

import com.ardoq.mavenImport.util.ArdoqExclusionDependencySelector;
import com.ardoq.mavenImport.util.ConsoleDependencyGraphDumper;
import com.ardoq.model.Component;
import com.ardoq.model.Reference;
import com.ardoq.util.SyncUtil;

public class ProjectSync {


    final SyncUtil ardoqSync;
    final ArtifactSync artifactSync;
    final String COMPONENT_TYPE_PROJECT;

    Map<String, String> componentNameIdMap;

    final MavenUtil mavenUtil;

    public ProjectSync(SyncUtil ardoqSync, MavenUtil mavenUtil) {
        this.ardoqSync = ardoqSync;
        this.artifactSync = new ArtifactSync(ardoqSync);
        this.mavenUtil = mavenUtil;

        COMPONENT_TYPE_PROJECT = ardoqSync.getModel().getComponentTypeByName("Project");

        componentNameIdMap = new HashMap<String, String>();
    }


    public void addExclusions(MavenUtil mavenUtil) {
        Map<Artifact,ArdoqExclusionDependencySelector> dependencySelectors = mavenUtil.getDependencySelectors();
        for(Artifact a:dependencySelectors.keySet()){

            ArdoqExclusionDependencySelector depsel = dependencySelectors.get(a);
            for(Artifact ex:depsel.getExcluded()){
                artifactSync.addArtifactVersion(ex,false);
                artifactSync.addReference(a, ex, "Exclusion");
            }
        }
    }


    public void syncProjects(List<String> projects) throws Exception {
        for (String project : projects) {
            syncProject(project);
        }
    }

    public String syncProject(String projectStr) throws ArtifactResolutionException  {
        MavenProject mavenProject = mavenUtil.loadProject(projectStr);
        String ret = syncProject(mavenProject);
        syncRelations();
        return ret;
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
        ardoqProjectComponent = ardoqSync.addComponent(ardoqProjectComponent);
        componentNameIdMap.put(componentName, ardoqProjectComponent.getId());

        Map<String, Integer> refTypes = ardoqSync.getModel().getReferenceTypes();

        syncProjectArtifact(project, ardoqProjectComponent, refTypes);
        syncProjectParent(project, ardoqProjectComponent, refTypes);
        syncProjectModules(project, ardoqProjectComponent, refTypes);

        return ardoqProjectComponent.getId();
    }

    private String buildProjectDescription(MavenProject project) {
        // TODO: add url, organization, developers, contributors, mailing lists, etc..


        String description = "";
        if( project.getDescription()!=null && project.getDescription().trim().length()>0) {
            description += "#Description\n\n" + project.getDescription();
        }

        if (!project.getLicenses().isEmpty()) {
            description += "\nLicenses\n----\n\n";
            for (License license : project.getLicenses()) {
                description += " * " + license.getName() + "\n";
            }
        }

        if( !project.getDevelopers().isEmpty()) {
            description += "\nDevelopers\n----\n\n";
            for (Developer developer : project.getDevelopers()) {
                description += " * "+developer.getName()+" ("+developer.getEmail()+")\n";
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
            collectRequest.setRepositories(mavenUtil.getRepos());
            CollectResult collectResult = mavenUtil.getSystem().collectDependencies(mavenUtil.getSession(), collectRequest);

            collectResult.getRoot().accept(new ConsoleDependencyGraphDumper(mavenUtil.getPrintStream()));
            collectResult.getRoot().accept(artifactSync);
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        }
    }

    private void syncRelations() {
        artifactSync.syncReferences();
    }


}
