#### Formio special properties
The following properties are used to outline corner-cases from Formio client to back-end:
* `state` property - is used to determine submission state (e.g. `rejected`, `submitted`)
* `protected` attribute on a subform - when this is `true`, all subform fields become disabled. Use to display read-only subforms
* Submitted files are serialized into Camunda FileValue, and their `url` fields are set to point to file id in the file storage. Default file storage is Base64 Data URL 

---

#### Environment
The environment is configured through the following JVM arguments:

* `FORMIO_LOGIN` - an email to login to formio server. Default: `root@root.root`
* `FORMIO_PASSWORD` - a password to login to formio server. Default: `root` 
* `FORMIO_URL` - a URL to formio server. Default: `http://localhost:3001`
* `FORMIO_JWT_EXPIRATION_TIME_SECONDS` - formio auth token expiration time in seconds. Default: `3s`

---

### Process-archive resources
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

---

#### FileStorage
To add an external FileStorage service, just implement `com.artezio.bpm.services.integration.FileStorage` interface and make the implementation class a CDI bean. Only one such implementation is allowed on the classpath.  
