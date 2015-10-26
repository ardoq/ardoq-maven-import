package com.ardoq.mavenImport;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.maven.project.MavenProject;

import com.ardoq.ArdoqClient;
import com.ardoq.model.Field;
import com.ardoq.model.FieldType;
import com.ardoq.model.Model;
import com.ardoq.model.Workspace;
import com.ardoq.service.FieldService;
import com.ardoq.util.SyncUtil;



/**
 * Collects the transitive dependencies of an artifact.
 */
public class ArdoqMavenImport {

    public enum ReferenceTypes {DEPENDENCY, TEST, PARENT, MODULE};

    String host;
    String workspaceName;
    String modelName;
    String org;
    String token;

    String workspaceID;

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {

        Options options = initOptions();

        CommandLine cmd;
        try {
            CommandLineParser parser = new BasicParser();
            cmd = parser.parse( options, args);

            if(cmd.hasOption("help")){
                printHelp(options);
                return;
            }

            if(cmd.getArgList().isEmpty()){
                System.out.println("One or more Maven artifact IDs required. For instance: 'io.dropwizard:dropwizard-core:0.8.1'");
                return;
            }

            String host = cmd.getOptionValue("h","https://app.ardoq.com");
            String token = cmd.getOptionValue("t");
            String modelName = cmd.getOptionValue("m", "Maven");
            String org = cmd.getOptionValue("o","ardoq");
            String workspace = cmd.getOptionValue("w");
            List<String> importList = cmd.getArgList();

            ArdoqMavenImport ardoqMavenImport = new ArdoqMavenImport(host, workspace, modelName, org, token);
            MavenUtil mavenUtil = new MavenUtil(System.out, "test", "provided");

            if(cmd.hasOption("r")){
                String extrarepo = cmd.getOptionValue("r");
                if(cmd.hasOption("u") && cmd.hasOption("p")){
                    String username = cmd.getOptionValue("u");
                    String password = cmd.getOptionValue("p");
                    mavenUtil.addRepository(extrarepo, username, password);
                }
                else{
                    mavenUtil.addRepository(extrarepo);
                }
            }

            ardoqMavenImport.startImport(importList,mavenUtil);

        }
        catch (MissingOptionException moe) {
            printHelp(options);
        }
    }

    public ArdoqMavenImport(String host, String workspaceName, String modelName, String org, String token){
        this.host = host;
        this.workspaceName = workspaceName;
        this.modelName = modelName;
        this.org = org;
        this.token = token;
    }


    public List<String> startImport(List<String> importList, MavenUtil mavenUtil) throws IOException, Exception {
        if(importList.isEmpty()) {
            throw new Exception("At least one artifact must be specified!");
        }

        ArdoqClient ardoqClient = new ArdoqClient(host,token);
        ardoqClient.setOrganization(org);

        if(workspaceName==null || workspaceName.trim().length()==0){
            String artifactStr = importList.get(0);
            MavenProject mavenProject = mavenUtil.loadProject(artifactStr);
            workspaceName = "Maven project "+mavenProject.getName();
        }


        Model model = ardoqClient.model().findOrCreate(modelName, ArdoqMavenImport.class.getResourceAsStream("/model.json"));
        String COMPONENT_TYPE_PROJECT = model.getComponentTypeByName("Project");
        String COMPONENT_TYPE_GROUP = model.getComponentTypeByName("Group");
        String COMPONENT_TYPE_ARTIFACT = model.getComponentTypeByName("Artifact");
        String COMPONENT_TYPE_VERSION = model.getComponentTypeByName("Version");

        Map<String, Field> fields = getModelFields(ardoqClient, model);
        ensureFieldExist(ardoqClient.field(), model, fields, "license", Arrays.asList(COMPONENT_TYPE_PROJECT, COMPONENT_TYPE_GROUP, COMPONENT_TYPE_ARTIFACT, COMPONENT_TYPE_VERSION));
        ensureFieldExist(ardoqClient.field(), model, fields, "groupId", Arrays.asList(COMPONENT_TYPE_PROJECT, COMPONENT_TYPE_GROUP, COMPONENT_TYPE_ARTIFACT, COMPONENT_TYPE_VERSION));
        ensureFieldExist(ardoqClient.field(), model, fields, "artifactId", Arrays.asList(COMPONENT_TYPE_PROJECT, COMPONENT_TYPE_ARTIFACT, COMPONENT_TYPE_VERSION));
        ensureFieldExist(ardoqClient.field(), model, fields, "version", Arrays.asList(COMPONENT_TYPE_PROJECT, COMPONENT_TYPE_VERSION));

        SyncUtil ardoqSync = new SyncUtil(ardoqClient,workspaceName,modelName);

        Workspace workspaceInstance = ardoqSync.getWorkspace();
        String description = "This is an automatically imported workspace, "
                + "based on information from the Maven Project Object Model (POM) with coordinates: ***"+importList+"***\n"
                + "\n"
                + "> Please don't edit this workspace manually! Changes will be overwritten the next time the import is triggered. If you need more documentation, create a separate workspace and create implicit references into this workspace. \n"
                + "\n"
                + "Import timestamp: "+new SimpleDateFormat("yyyy.MM.dd HH:mm").format(new Date());

        workspaceInstance.setDescription(description);
        workspaceInstance.setViews(Arrays.asList("processflow","componenttree","tableview","reader","integrations"));
        ProjectSync projectSync = new ProjectSync(ardoqSync,mavenUtil);
        workspaceID = workspaceInstance.getId();
        List<String> projectIDs = projectSync.syncProjects(importList);
        projectSync.addExclusions(mavenUtil);

        System.out.println("updating workspace");
        ardoqSync.updateWorkspaceIfDifferent(workspaceInstance);

        System.out.println("Deleting not synced references");
        ardoqSync.deleteNotSyncedItems();

        return projectIDs;
    }



    public static void ensureFieldExist(FieldService fieldService, Model model, Map<String, Field> fields, String fieldName, List<String> componentTypes) {
        Field field = fields.get(fieldName);
        if(field==null){
            field = fieldService.createField(new Field(fieldName,fieldName,model.getId(),componentTypes,FieldType.TEXT));
            return;
        }
        // ensure that the field is associated with the correct component types
        List<String> missingComponentTypes = new LinkedList<String>(componentTypes);
        missingComponentTypes.removeAll(field.getComponentType());
        if(missingComponentTypes.isEmpty()){
            return;
        }
        field.getComponentType().addAll(missingComponentTypes);
        fieldService.updateField(field.getId(), field);
    }


    public static Map<String, Field> getModelFields(ArdoqClient ardoqClient, Model model) {
        Map<String,Field> fields = new HashMap<String,Field>();
        for(Field field:ardoqClient.field().getAllFields()){
            if(!field.getModel().equals(model.getId())){
                continue;
            }

            for( String key:Arrays.asList("license","groupId","artifactId","version")){
                if(key.equals(field.getName())){
                    fields.put(key,field);
                }
            }
        }
        return fields;
    }

    public String getWorkspaceID() {
        return workspaceID;
    }

    private static Options initOptions() {
        Options options = new Options();

        Option host = new Option("h","host",true,"Ardoq host name");
        options.addOption(host);

        Option token = new Option("t","token",true,"Ardoq access token");
        token.setRequired(true);
        options.addOption(token);

        Option workspace = new Option("w","workspace",true,"Ardoq workspace name");
        workspace.setRequired(true);
        options.addOption(workspace);

        Option model = new Option("m","model",true,"Ardoq model name - defaults to Maven");
        options.addOption(model);

        Option organization = new Option("o","organization",true,"Ardoq organization name");
        options.addOption(organization);

        Option extrarepo = new Option("r","repository",true,"Extra repository URL");
        options.addOption(extrarepo);

        Option extrarepouser = new Option("u","username",true,"Extra repository user name");
        options.addOption(extrarepouser);

        Option extrarepopass = new Option("p","password",true,"Extra repository password");
        options.addOption(extrarepopass);

        Option help = new Option("help", "print this help message");
        options.addOption(help);
        return options;
    }


    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "mavenimport [options] [pomfile|artifactId ...]", options);
    }


}