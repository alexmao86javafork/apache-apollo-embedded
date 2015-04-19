Simple Apache Apollo Embedded Helper
=============

Requirements
======
To build, you need 

* Java 8 or higher 
* Maven 3 or higher (http://maven.apache.org/)

Setup
======
Before cloning this repository, be sure to enable automatic conversion of CRLF/LF on your machine using "git config --global core.autocrlf input". For more information, please  refer to http://help.github.com/dealing-with-lineendings/

Building
======
To build the server side, run "mvn package", this will build the program and place the generated jar file in the directory "target/".

Usage
======
Example using the configuration builder:

```
ApolloConfigurationBuilder configBuilder = new ApolloConfigurationBuilder()
		.openWire(9999)
		.queueNonPersistentNoExpiration("queue1")
		.externalAccess()
		.authenticatedHeader("authenticated-user-name")
		.jaasAuthentication("src/main/resources/demo.jaas")
		.alwaysTrueAuthorization();
		
Broker broker = ApolloEmbeddedFactory.start(configBuilder.build());
```

Maven configuration:

```
	<dependencies>
	[...]

		<!-- Apache Apollo Embedded Configuration Helper -->
		<dependency>
			<groupId>de.farberg.apollo</groupId>
			<artifactId>apache-apollo-embedded</artifactId>
			<version>0.1-SNAPSHOT</version>
		</dependency>

	[...]
	</dependencies>

	[...]
	
	<repositories>
	[...]

		<repository>
			<id>farberg-releases</id>
			<url>http://nexus.farberg.de/content/repositories/releases/</url>
			<releases>
				<enabled>true</enabled>
			</releases>
			<snapshots>
				<enabled>false</enabled>
			</snapshots>
		</repository>
		<repository>
			<id>farberg-snapshots</id>
			<url>http://nexus.farberg.de/content/repositories/snapshots/</url>
			<releases>
				<enabled>false</enabled>
			</releases>
			<snapshots>
				<enabled>true</enabled>
			</snapshots>
		</repository>

	[...]
	</repositories>

```