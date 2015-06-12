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
public class ArdoqDependencyGraphDumper implements DependencyVisitor {

    final SyncUtil ardoqSync;
    final String COMPONENT_TYPE_PROJECT;
    final String COMPONENT_TYPE_ARTIFACT;
    
    Map<String,String> componentNameIdMap;

    public ArdoqDependencyGraphDumper(SyncUtil ardoqSync) {
    	this.ardoqSync = ardoqSync;
    	
    	COMPONENT_TYPE_PROJECT = ardoqSync.getModel().getComponentTypeByName("Project");
    	COMPONENT_TYPE_ARTIFACT = ardoqSync.getModel().getComponentTypeByName("Artifact");
    	
    	componentNameIdMap = new HashMap<String,String>();
    }
    
    


    public boolean visitEnter( DependencyNode node ){
    	String componentName = getComponentName(node);
    	Component comp = new Component(componentName, ardoqSync.getWorkspace().getId(), "", COMPONENT_TYPE_ARTIFACT);
    	ardoqSync.addComponent(comp);
    	componentNameIdMap.put(componentName,comp.getId());
    	
        return true;
    }


    public boolean visitLeave(DependencyNode node) {
    	
        Map<String, Integer> refTypes = ardoqSync.getModel().getReferenceTypes();
        int refType = refTypes.get("Dependency");
        
        String sourceName = getComponentName(node);
        String sourceId = componentNameIdMap.get(sourceName);
        for(DependencyNode child:node.getChildren()){
        	String targetName = getComponentName(child);
        	String targetId = componentNameIdMap.get(targetName);
        	Reference ref = new Reference(ardoqSync.getWorkspace().getId(), "", sourceId, targetId, refType);
        	ardoqSync.addReference(ref);
        }
    	return true;
    }

    private String getComponentName(DependencyNode node) {
    	Artifact artifact = node.getArtifact();
    	String groupId = artifact.getGroupId();
    	String artifactId = artifact.getArtifactId();
    	String version = artifact.getVersion();
    	
    	String componentName = groupId+":"+artifactId+":"+version;
    	return componentName;
    }
    
}