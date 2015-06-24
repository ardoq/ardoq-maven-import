This project serves as an example to how to synchronize a number of Maven projects into an Ardoq workspace. 


Please note that the workspace will be overwritten when running the sync, so use a different workspace with implicit references for your manually entered docs.


# Ardoq Model setup

Use the provided Maven template model in Ardoq directly, or as a template.

Requires a workspace with only two top level page types

* Project
* Artifact

Fields:
* artifactId
* groupId
* version


Relations:
* Dependency (includes run time and comile time dependencies)
* Test scope dependency
* Parent (only applies to Projects)
* Module (only applies to Projects)



# Example usage
```
mvn install
java -jar target/ardoq-maven-import-0.1.jar -t <apiToken> -w <workspace> -m <modelName> io.dropwizard:dropwizard-core:0.8.1
```





