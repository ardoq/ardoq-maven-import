package com.ardoq.mavenImport;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingOptionException;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import com.ardoq.ArdoqClient;
import com.ardoq.model.Workspace;
import com.ardoq.util.SyncUtil;



/**
 * Collects the transitive dependencies of an artifact.
 */
public class ArdoqMavenImport {

    public enum ReferenceTypes {DEPENDENCY, TEST, PARENT, MODULE};


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

            ArdoqClient ardoqClient = new ArdoqClient(host,token);
            ardoqClient.setOrganization(org);

            ardoqClient.model().findOrCreate(modelName, ArdoqMavenImport.class.getResourceAsStream("/model.json"));

            SyncUtil ardoqSync = new SyncUtil(ardoqClient,workspace,modelName);

            Workspace workspaceInstance = ardoqSync.getWorkspace();
            String description = "This is an automatically imported workspace, "
                    + "based on information from the Maven Project Object Model (POM) with coordinates: ***"+cmd.getArgList()+"***\n"
                    + "\n"
                    + "> Please don't edit this workspace manually! Changes will be overwritten the next time the import is triggered. If you need more documentation, create a separate workspace and create implicit references into this workspace. \n"
                    + "\n"
                    + "Import timestamp: "+new SimpleDateFormat("yyyy.MM.dd HH:mm").format(new Date());

            workspaceInstance.setDescription(description);
            workspaceInstance.setViews(Arrays.asList("processflow","componenttree","tableview","reader","integrations"));
            MavenUtil mavenUtil = new MavenUtil(System.out, "test", "provided");
            ProjectSync projectSync = new ProjectSync(ardoqSync,mavenUtil);
            projectSync.syncProjects(cmd.getArgList());
            projectSync.addExclusions(mavenUtil);

            System.out.println("updating workspace");
            ardoqSync.updateWorkspaceIfDifferent(workspaceInstance);

            System.out.println("Deleting not synced references");
            ardoqSync.deleteNotSyncedItems();

        }
        catch (MissingOptionException moe) {
            printHelp(options);
        }
    }



    /*
    private synchronized Settings getSettings() {
        DefaultSettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setUserSettingsFile( getUserSettings() );
        request.setGlobalSettingsFile( AetherUtils.findGlobalSettings());
        request.setSystemProperties(AetherUtils.getSystemProperties());
//        request.setUserProperties( getUserProperties() );

        try
        {
            Settings settings = settingsBuilder.build( request ).getEffectiveSettings();
            SettingsDecryptionResult result = settingsDecrypter.decrypt( new DefaultSettingsDecryptionRequest( settings ) );
            settings.setServers( result.getServers() );
            settings.setProxies( result.getProxies() );
            return settings;
        }
        catch ( SettingsBuildingException e ) {
            e.printStackTrace();
            return null;
        }
    }
    */


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

        Option help = new Option("help", "print this help message");
        options.addOption(help);
        return options;
    }


    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "mavenimport [options] [pomfile|artifactId ...]", options);
    }


}