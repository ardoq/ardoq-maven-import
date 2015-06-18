package com.ardoq.mavenImport;


public class Test {

	public static void main(String[] args) throws Exception{
	    String apiToken = "71846c3f580a45d19f276fa5e6e589db";
	    String apiHost = "https://app.ardoq.com";
	    String workspaceName = "Maventest";
	    String modelName = "Maven";


		ArdoqMavenImport.main(new String[]{ "-t",apiToken,"-w",workspaceName,"-m",modelName,"io.dropwizard:dropwizard-core:0.8.1"});;
//		ArdoqMavenImport.main(new String[]{ "-t",apiToken,"-w",workspaceName,"-m",modelName,"pom.xml"});;


	}
}
