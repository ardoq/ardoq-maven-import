package com.ardoq.mavenImport;

public class Test {

    public static void main(String[] args) throws Exception {
//		String apiToken = "71846c3f580a45d19f276fa5e6e589db";
//		String apiHost = "https://app.ardoq.com";
//		String workspaceName = "Maventest";
//		String modelName = "Maven";

        String apiToken = "435cf12a225747cb9678b8050d2c0887";
        String apiHost = "http://dockerhost";
        String workspaceName = "Dropwizard";
        String artifact = "io.dropwizard:dropwizard-core:0.8.1";
        String modelName = "Maven";

//        ArdoqMavenImport.main(new String[] { "-h", apiHost, "-t", apiToken, "-w", workspaceName, "-m", modelName, "org.apache.kafka:contrib_2.10:0.8.0" });
        ArdoqMavenImport.main(new String[] { "-h", apiHost, "-t", apiToken, "-w", workspaceName, "-m", modelName, artifact });


//		ArdoqMavenImport.main(new String[]{"-t",apiToken,"-w",workspaceName,"-m",modelName,"pom.xml"});;

    }
}
