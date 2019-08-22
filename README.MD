# ART-BPMS-REST

ART-BPMS-REST is a module which takes on the role of the layer between Camunda process engine services and Formio server 
for simplifying their communication. General aspects of ART-BPMS-REST:
* possibility to use a form key in BPMN/CMMN diagram components which points to Formio form
* converting data to Camunda and Formio specific formats before transmitting them between Formio server to Camunda services
* form data validation using both Formio and java validation
* logging 
* a form cache
* an interface and its base implementation of the file storage which is used by Formio file component, also there is an availability to provide your own implementation
* the implementation of Message Correlation and Signal services
* uploading all forms in process-archive to Formio server
* Forms versioning

## Prerequisites
* Java 1.8 or greater
* Apache Maven 3.6.1 or greater
* Git version 2.19.0 or greater

## Usage
1. Install the module
```
git clone https://github.com/Artezio/ART-BPMS-REST.git
cd ART-BPMS-REST
mvn install
``` 
2. Add the module to your project's POM
```
<dependencyManagement>
    <dependencies>
	    <dependency>
		    <groupId>com.artezio.bpm</groupId>
			<artifactId>bpm-bom</artifactId>
			<version>1.0version>
			<scope>import</scope>
			<type>pom</type>
		</dependency>
	</dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <artifactId>bpms-rest</artifactId>
        <packaging>war</packaging>
    </dependency>
<dependencies>
```

## Configuration
ART-BPMS-REST can be configured with the following **system properties** (passed as -Dxxx flags to JVM on startup):
* `FORM_VERSIONING`: Switches on and off form versioning, which will add process engine deployment's id as
suffix to form id, paths and machineName before its deploying on Formio server. By default `true`
* `FORMIO_URL`: a URL which points to Formio server. By default `http://localhost:3001`
* `FORMIO_LOGIN`: an email to login to formio server. Used during communication between Formio client and Formio server. By default `root@root.root`
* `FORMIO_PASSWORD`: password to login to formio server. Used during communication between Formio client and Formio server. By default `root`
* `FORMIO_JWT_EXPIRATION_TIME`: formio JWT token expiration time in seconds. By default `240`
* `FILE_STORAGE_URL`: sets the file storage URL for custom file storage implementations. By default used "The 'data' in URL" file storage.
See [File Storage](#file-storage) for more information about the storages

## Formio-related corner-cases
* `state` - property included in submission and then passed to process variables. It allows to determine submission state for further
control over process execution. Accepts any `String` value. Example: suppose we have `submitted` and `rejected` states, so
they may be considered as 'continue process' and 'suspend process' actions 
* `protected` - attribute on a subform. When this is `true`, all subform fields become disabled. Used to display read-only subforms
* after submitting all files are converted into Camunda `org.camunda.bpm.engine.variable.value.FileValue`, and their `url` fields are
set to point to file id in the file storage. Default file storage is "The 'data' in URL". See [File Storage](#file-storage) 
for more information about the storages

## Process-archive resources
To specify camunda engine which resources must be deployed, `processes.xml` is used. There are two ways to register your resources via `processes.xml`:
1. Add the following into `process-archive` element
```
<resource>{path_to_your_resource}</resource>
```
This allows to list resources explicitly. 
2. Add the following into `properties` element
```
<property name="resourceRootPath>{path_to_root_directory_of_your_resources}</property>
<property name="additionalResourceSuffixes">{comma_separated_resource_suffixes}</property>
```
This tells camunda engine to include all resources found in a given path (including subdirectories) and matched to given suffixes.
For more information about how to add resources to a deployment see [Camunda Documentation](https://docs.camunda.org/manual/7.10/reference/deployment-descriptors/tags/process-archive/).

## File Storage
By default "The 'data' in URL" file storage is used, it means content of all files are base64 encoded and placed directly in url (see 
[RFC 2397](https://tools.ietf.org/html/rfc2397) for more information about this scheme). Also it is possible to add a custom FileStorage implementation. To do it just implement 
`com.artezio.bpm.services.integration.FileStorage` interface and make it a CDI bean. Only one such implementation is allowed on the classpath.  
