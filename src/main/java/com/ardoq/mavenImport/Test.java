package com.ardoq.mavenImport;

public class Test {

    public static void main(String[] args) throws Exception {
//		String apiToken = "71846c3f580a45d19f276fa5e6e589db";
//		String apiHost = "https://app.ardoq.com";
//		String workspaceName = "Maventest";
//		String modelName = "Maven";

        String apiToken = "8463d09f39f542b4a8718c2fadd9b991";
        String apiHost = "http://dockerhost";
        String workspaceName = "Kafka";
        String modelName = "Maven";

        ArdoqMavenImport.main(new String[] { "-h", apiHost, "-t", apiToken, "-w", workspaceName, "-m", modelName, "org.apache.kafka:contrib_2.10:0.8.0" });
//		ArdoqMavenImport.main(new String[]{"-t",apiToken,"-w",workspaceName,"-m",modelName,"pom.xml"});;

    }
}
