package com.ardoq.mavenImport.util;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.codehaus.plexus.util.Os;

class AetherUtils{

	final static String SETTINGS_XML = "settings.xml";
	
    public static File findGlobalSettings() {
        String mavenHome = getMavenHome();
        if(mavenHome == null){
        	return null;
        }
        return new File( new File( mavenHome, "conf" ), SETTINGS_XML);
    }

    public static String getMavenHome(){
        return System.getenv( "M2_HOME" );
    }
    
    public static String getUserHome(){
    	return System.getProperty("user.home");
    }

    public static File findUserSettings() {
        return new File( new File( getUserHome(), ".m2" ), SETTINGS_XML);
    }
    
    public static Properties getSystemProperties() {
        Properties props = new Properties();
        props.putAll(getEnvProperties());
        props.putAll(System.getProperties());
        return props;
    }    
    
    private static Properties getEnvProperties(){
    	Properties props = new Properties();
        boolean envCaseInsensitive = Os.isFamily( "windows" );
        for ( Map.Entry<String, String> entry : System.getenv().entrySet() ){
            String key = entry.getKey();
            if ( envCaseInsensitive ) {
                key = key.toUpperCase( Locale.ENGLISH );
            }
            key = "env." + key;
            props.put( key, entry.getValue() );
        }
        return props;
    }    
}
