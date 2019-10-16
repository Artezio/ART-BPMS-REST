# ART-BPMS-REST

ART-BPMS-REST is a module which adds [Formio] forms support to [Camunda] BPM engine, as well as some other useful features. 
It provides a set of REST endpoints for working with Deployments, Processes and Tasks. 
Forms are designed using Formio node.js application, stored in Camunda deployments and then could be displayed using any of Formio form players.

---

* [Features](#features)
* [Usage](#usage)
    * [Running as Standalone application](#standalone)
    * [Running within EAR subdeployment](#running-as-ear-subdeployment)
    * [Working with forms](#working-with-forms)
    * [Form versioning](#forms-versioning)
    * [Validation](#validation)
    * [Mapping submitted data to Java Classes](#mapping-submitted-data-to-java-classes)
    * [Internationalization and Localization](#internationalization-and-localization)
* [Steps to create and use forms in BPMN](#steps-to-create-and-use-forms-in-bpmn)
* [Configuration](#configuration)
* [Corner cases](#formio-related-corner-cases)
* [File storage](#file-storage)

---

## Features
* Simple integration of User tasks and forms: task form keys are direct references to Formio forms
* Robust Data validation using both Formio and java validation
* File uploading from Formio to Camunda, which can be customized or externalized by providing custom FileStorage implementations 
* Forms versioning
* Logging 
* Form cache
* Default implementation of Message Correlation and Signal services

ART-BPMS-REST uses Formio node.js servers for storing and validating user forms. 

Two Formio servers are required to use this module - Development server and Work server. 

The development server is used by forms designers for constructing forms. 
These forms are then downloaded using [formio-import-export-tool](https://github.com/artezio/) and deployed to Camunda along with BPMN diagrams. 
When the deployment is made, these forms are automatically versioned and uploaded to the work server. 
ART-BPMS-REST then uses the work server to load forms and validate submitted data.


## Usage

### Prerequisites

* [Java] 1.8 or greater
* [Maven] 3.6.1 or greater
* [Git] version 2.19.0 or greater
* [Wildfly 17]. You could use another JavaEE8-approved server, but there are no guarantees on its workability
* A relational database for Camunda. Supported databases are listed on [Camunda documentation](https://docs.camunda.org/manual/7.10/introduction/supported-environments/#supported-database-products) page 
* Configured JAAS Security provider, such as [Keycloak]. Refer to [Security](#security) section for more details

ART-BMS-REST could be run either as a standalone application or as part of EAR deployment.


### Running standalone

1. Build ART-BPMS-REST

    ```
    git clone https://github.com/Artezio/ART-BPMS-REST.git
    cd ART-BPMS-REST
    mvn install
    ```
   
1. Start Formio server as described in [Running Formio server](#running-formio-server) section

1. Start Wildfly server. Pass configuration options to the server as described in [Configuration](#configuration) section  

1. Using wildfly CLI (http://localhost:9990), Add the relational database datasource with jndi name `java:/datasources/CamundaDS` which will be used by Camunda engine

1. Install JAAS Security provider for `bpms-rest.war` deployment ([details](#security))

1. Deploy `bpms-rest.war` from `ART-BPMS-REST/target` directory to the server

1. REST API description is available at http://localhost:8080/bpms-rest

1. Refer to [Working with forms](#working-with-forms) section for details on how to deploy and use forms

1. Use REST API endpoints to deploy and run the processes and forms

### Running as EAR module

1. Build ART-BPMS-REST war archive

    ```
    git clone https://github.com/Artezio/ART-BPMS-REST.git
    cd ART-BPMS-REST
    mvn install
    ```

1. Add the module to your EAR project's POM

    ```xml
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.artezio.bpm</groupId>
                <artifactId>bpm-bom</artifactId>
                <version>1.0</version>
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
    </dependencies>
    ```

1. Declare bpms-rest as web module of your EAR deployment

    > EAR application.xml
    > ```xml
    > <module>
    >   <web>
    >     <web-uri>bpms-rest.war</web-uri>
    >     <context-root>/bpms-rest</context-root>
    >   </web>
    > </module>
    > ```

1. Run Formio server as described in [Running Formio server](#running-formio-server) section

1. Start Wildfly server. Pass configuration options to the server as described in [Configuration](#configuration) section  

1. Using wildfly CLI (http://localhost:9990), Add the relational database datasource with jndi name `java:/datasources/CamundaDS` which will be used by Camunda engine

1. Install JAAS Security provider for `bpms-rest.war` deployment ([details](#security))

1. Bundle process application JARs inside your EAR deployment or skip this step to deploy them later using REST API
 
1. Build EAR archive and deploy it to the server. All Process Application Archive will be automatically deployed along with included forms

1. Follow REST API descriptions at `http://localhost:8080/bpms-rest` to work with BPMS-REST backend

1. Refer to [Working with forms](#working-with-forms) section for details on how to deploy and use forms

1. Use REST API endpoints to deploy and run the processes and forms

## Working with forms

ART-BPMS-REST uses two Formio servers, Development and Working. 

Development server is used by form designers for creating forms. 
Forms are then downloaded from Development server to local filesystem before creating a deployment. By adding downloaded forms into Process Application Archive under resources folder, these forms become visible to ART-BPMS-REST so they will be automatically deployed with Camunda resources such as BPMN diagrams.

Work server is used by ART-BPMS-REST for uploading and storing versioned copies of forms. When a deployment is created and uploaded using ART-BPMS-REST endpoints, all forms' identifiers are modified prior to uploading in such way that they become unique across different deployments. Any forms which were removed from work server will be re-uploaded upon next ART-BPMS-REST statup.

Overall, the workflow looks like this:
1. Construct forms using Development Formio server
1. Download forms from Development Formio server using formio-import-export-tool and put forms into Camunda deployment along with other resources, such as BPMN diagrams
1. Set process start form key if necessary
1. Add BPMN User Tasks with form keys pointing to constructed forms
1. Deploy process with forms into Camunda
1. Start the process using the BPMS-REST by either submitting a start form or starting the process without the start form
1. Request and submit task forms using BPMS-REST
1. If a form requires updating and the form versioning is enabled, make changes to the form, download the form with formio-import-export-tool and make a deployment with the updated form. Processes started from this point will use the updated version of the form.

#### Forms versioning

Form versioning is controlled by property `FORM_VERSIONING`, which accepts values `true` and `false` and is enabled by default

When a deployment is made with form versioning enabled, its forms are given unique deployment-specific suffixes to keep them unique across different deployment versions. In this case, if the form was updated and deployed, its new version is uploaded to Work server, so older versions of this form aren't affected and running processes which use the old version of the form will continue to function without errors.

If form versioning is disabled then forms are uploaded to Work server as is, without identifier modifications. In this case, a form will only be uploaded once and will never be updated, and all deployments will be referencing the same form. If there are multiple forms with the same path, then there's no guarantees on which of them will be deployed and used by the rest of deployments. In this case, processes which use newer versions of the form might function with errors.

#### Validation

On every submit, form data is validated several times:
1. Before submitting, inside Formio player all data gets validated using constraints defined in form designer
1. After submitting, the data is sent to Formio Work server to perform dry cleanup and validation (i.e. all extra submitted data which was not present in the form will be removed)
1. After dry validation all Java Objects which have `javax.validation` constraints are validated using JavaBeans Validation service

#### Mapping submitted data to Java classes

When the data is submitted from Formio to Camunda, it's unknown which json object corresponds to which Java class, so all objects are stored as Maps in Process Variables.

To convert the submitted objects to Java classes, Process Extensions are used to define mapping between Process Variable and class which should be used for that variable.

To add class mapping for a process variable, add a Process Extension with name `entity.<variable_name>` and value of the required class:

![](doc/process-extensions.png)

In the picture above, two mappings are created: between process variable `userInfo` and java class `com.artezio.bpm.model.UserInfo`, between process variable `ticket` and java class `com.artezio.bpm.model.Ticket`

Let's assume the following data is submitted with Process Extensions set as in the picture above:

```
    {
        "data": {
            "userInfo": {
                "firstName": "John",
                ...
            },
            "marriageInfo": {
                ...
            }, 
            ...
        }
    }
```

Process variable `userInfo` will be stored as class `com.artezio.bpm.model.UserInfo`, and process variable `marriageInfo` will be stored as `Map`

#### Internationalization and localization

With ART-BPMS-REST it is possible to ship deployment with process (or case) specific l10n resources which can be fetched and used later.
Here is an explanation of how ART-BPMS-REST chooses l10n resources:
1. With process definition (or case definition) id also language range preferences are provided. Language range preferences
consist of language ranges which are RFC standardized set of language tags having info about country, language, locale and so on.
(For more info about language tags and language ranges see [RFC 5646] and [RFC 4647])
2. Basing on provided language ranges ART-BPMS-REST does the following:
    * tries to find a resource with name formed of underscore-separated diagram file name without extension and underscore-separated
      language range (e.g. for diagram file name `some-diagram-name` and language range `en-US` it would be `some-diagram-name_en_US.properties`):
        * if the resource is found, the next language range is processed
        * if the resource is not found, ART-BPMS-REST tries to find the most appropriate resource basing on the rule:
          *sequentially cut off the underscore-separated parts of the wished resource name until they run out*
    * if at the end no resource is found, empty `java.util.ResourceBundle` is returned
    
**Example**: Suppose a deployment has resource with name 'some-diagram-name_en.properties' and we are passing 'en-US' language range.
In accordance with the first step ART-BPMS-REST will try to find the resource with name 'some-diagram-name_en_US.properties'.
But the deployment does not have the resource with such a name, so ART-BPMS-REST will remove 'US' part from 'some-diagram-name_en_US.properties'
(extension .properties is ignored) and will search 'some-diagram-name_en.properties' resource. As the deployment has such
a resource, `java.util.ResourceBundle` with info from this resource will be returned.

**Please Note**: Language ranges are dash-separated, while suffixes in a resource name are underscore-separated.  

## Steps to create and use forms in BPMN

1. Log in to Formio Development server and build a form. Set form path to something meaningful, e.g. `forms/user/personal-information`

    ![](doc/create-form.png)

1. In a BPMN, create User task and set form key to path you just set, prepended by `deployment:`, e.g. `deployment:forms/user/personal-information`. Add candidate users or groups, so the task becomes startable

    ![](doc/user-task-form-key.png)
    
    ![](doc/user-task-candidate-groups.png)

1. You can also use forms for starting the process. To do this, specify form key with deployment prefix in a Start Event, e.g. `deployment:forms/user/personal-information`

1. Run formio-import-export-tool. Set connection params, credentials and output directory for forms. If you specify any tags, only forms with those tags will be downloaded.   
   > formio-import-export-tool can be executed in GUI mode or as command-line utility. Start it with -h argument to get command-line mode help
   
   Forms will be downloaded into the specified directory and put into corresponding subdirectories
   
1. Depending on whether you're deploying a Process Application Archive or making deployment through REST API, do the following:
    * In a Process Application Archive:
        1. Put downloaded forms to `/src/main/resources/` directory
        1. Make forms visible to Camunda resource scanner. `processes.xml` file is used for resources declaration. 
            There are two ways to register forms in `processes.xml`:
            * List each form in `process-archive` - `<resource/>` elements
                ```xml
                <process-archive ...>
                    <resource>/forms/form1</resource>
                    <resource>/forms/form2</resource>
                    ...
                </process-archive>
                ```
            * Automatically scan for all forms with specified prefixes
                ```xml
                <process-archive ...>
                    <properties>
                        <property name="resourceRootPath>/forms/</property>
                        <property name="additionalResourceSuffixes">json</property>
                        ...
                    </properties>
                    ...
                </process-archive>   
                ```
           This tells camunda engine to include all resources found in a given path (including subdirectories) and ending with any of enlisted suffixes.
           For more information about how to add resources to a deployment see [Camunda Documentation](https://docs.camunda.org/manual/7.10/reference/deployment-descriptors/tags/process-archive/).
        1. Build the Process Application archive and redeploy
         
    * When deploying using REST API:
        
        Send POST request to `/bpms-rest/api/deployment?name=<deployment_name>` endpoint. 
        * Specify deployment name in the query 
        * Add all forms into request body along with BPMN diagrams and other resources
        * Set each form `filename` to be the same as form `path` you set in Formio editor

1. Get task form:
    * Task id is required
    * Send GET request to `/bpms-rest/api/task/<task-id>/form`. 
    * The response will include deployed form with populated data fields. The data is filled from process variables, and only fields present on the form will be populated and will be accepted on submit. Data not represented as form's field will be discarded
    * Use any of numerous [Formio players](https://help.form.io/developer/frameworks/) to display and submit the form
    
1. Submit task form:
    * Make Formio player use endpoint `/bpms-rest/api/task/<taskId>/complete` and call its submit() function.
    * You can submit the data without Formio players by sending POST request with json which contains submitted data:
        ```
        {
            "data": {
                "field1": 1.0,
                ...
            }
        }
        ```
        
## Configuration
ART-BPMS-REST is configured by passing the following properties to the server on startup (as -Dxxx flags, i.e. `-DFORM_VERSIONING=true`):
* `FORM_VERSIONING`: Switches on and off [form versioning](#forms-versioning). `true` by default
* `FORMIO_URL`: Formio Work Server url. By default `http://localhost:3001`
* `FORMIO_LOGIN`: Login to Formio Work Server. It's used in communication between ART-BPMS-REST and Formio server. By default `root@root.root`
* `FORMIO_PASSWORD`: Password to Formio Work Server. Used in communication between ART-BPMS-REST and Formio server. By default `root`
* `FORMIO_JWT_EXPIRATION_TIME`: Formio JWT token expiration time in seconds. By default `240`. This is related to Formio own authentication mechanism and not to ART-BPMS-REST security configuration

The following environment variables can be set:

* `FILE_STORAGE_URL`: file storage URL for custom file storage implementations. By default, files are stored inside Base64 Data Urls.

### Formio-related corner-cases
* Each submission `state` property is included in data and then passed to process variables. It allows to determine submission state for further
control over process execution. Accepts any `String` value. Example: suppose we have `submitted` and `rejected` states, so
they may be considered as 'continue process' and 'suspend process' actions, and two buttons may reside on the form to set one or another state 
* `protected` attribute of a subform included in a parent form is treated as follows. When this attribute is set `true`, all subform fields become disabled. This attribute should be used to make entire subforms read-only
* After submitting all files are converted into Camunda `org.camunda.bpm.engine.variable.value.FileValue`, and their `url` fields are
set to point to file id in the file storage. Default file storage is base64 url. See [File Storage](#file-storage) 
for more information about the storage

## File Storage
By default all files are encoded in Base64 and stored in Camunda database as base64 data urls ([RFC 2397](https://tools.ietf.org/html/rfc2397)). 
It's possible to add a custom FileStorage implementation to store files anywhere else, including filesystem, external FTP server, etc. It could be done by implementing 
`com.artezio.bpm.services.integration.FileStorage` interface and making it a CDI bean visible to BPMS-REST module. Only one such implementation is allowed on the classpath. 

When a file gets deserialized into Camunda's engine, FileStorage implementation will be called to store the file. 
When a file gets serialized from Camunda's engine, FileStorage implementation will be called to retrieve the file.  

## Running Formio server

The simplest way of running Formio server is to run our Formio Docker image from `/formio` subdirectory

See [Formio Docker readme](formio/README.MD) to use our Formio Docker image

To run Formio without Docker, you would have to:
1. Clone Formio `v1.47.0` sources from [Formio Github repository](https://github.com/formio) and install required dependencies listed on their github page
2. Replace the file `src/middleware/submissionHandler.js` with the one provided in `ART-BPMS-REST/formio/src/middleware/submissionHandler.js`
3. Configure MongoDB connection settings, install Formio application with npm and run with nodejs

## Security

ART-BPMS-REST relies on EJB / JAAS security system. You must setup a Security Provider, for example [Keycloak], configure user roles and make sure the deployment `bpms-rest.war` uses your Security provider. Security is usually configured at the server side.

REST endpoints are secured as follows:
* A user accessing REST endpoints must be authenticated
* A user accessing Deployment REST endpoints must have role `BPMSAdmin`
* A user accessing other REST endpoints can have any roles
* A user without roles won't be able to start any process or complete any task 

BPMN diagrams are secured as follows:
* If a Task or a Process has no Candidate Starter Groups and no Candidate Started Users, then it cannot be started or completed by a user and can only be started programmatically
* If a Task or a Process has Candidate Starter Groups, then only users which has these roles will be able to start/complete the task or process
* If a Task or a Process has Candidate Started Users, then only users with corresponding usernames will be able to start/complete the task or process


[Camunda]: https://camunda.com/
[Formio]: https://www.form.io/
[Java]: https://java.com
[Maven]: https://maven.apache.org/
[Git]: https://git-scm.com/
[Keycloak]: https://www.keycloak.org/
[RFC 4647]: https://tools.ietf.org/html/rfc4647
[RFC 5646]: https://tools.ietf.org/html/rfc5646
[Wildfly 17]: https://wildfly.org/downloads/

