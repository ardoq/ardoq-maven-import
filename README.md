This project serves as an example to how to synchronize a number of Maven projects into an Ardoq workspace. 


Please note that the workspace will be overwritten when running the sync, so use a different workspace with implicit references for your manually entered docs.


# Ardoq Model setup

Use the provided Maven template model in Ardoq directly, or as a template.

Requires a workspace with only two top level page types

* Project
* Group
  * Artifact
    * Version

Projects can link to Project and Version. Version can link to Version

Fields:
* artifactId
* groupId
* version


Relations:
* Dependency (includes run time and comile time dependencies)
* Test scope dependency
* Parent (only applies to Project)
* Module (only applies to Project)



# Example usage
```
mvn install
java -jar target/ardoq-maven-import-0.1.jar -t <apiToken> -w <workspace> -m <modelName> -o <organization> io.dropwizard:dropwizard-core:0.8.1
```





