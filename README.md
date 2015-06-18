Requires a workspace with only two top level page types

* Project
* Artifact

Fields:
* artifactId
* groupId
* version


# Example run
```
mvn install
java -jar target/ardoq-maven-import-0.1.jar -t <apiToken> -w <workspace> -m <modelName> io.dropwizard:dropwizard-core:0.8.1
```




