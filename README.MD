# ART-BPMS-REST

ART-BPMS-REST is a wrapper for BPMS Camunda rest services. It is designed to provide simple communication between Camunda services and Formio server. General aspects of ART-BPMS-REST:
* possibility to use a form key in BPMN/CMMN diagram components which points to Formio form
* converting data to Camunda and Formio specific formats before transmitting them between Formio server to Camunda services
* form data validation using both Formio and java validation
* logging 
* a form cache
* an interface and its base implementation of the file storage which is used by Formio file component, also there is an availability to provide your own implementation
* the implementation of Message Correlation and Signal services
* uploading the forms in process-archive to Formio server
* Forms versioning

## Configuration
ART-BPMS-REST can be configured with the following **system properties** (passed as -Dxxx flags to JVM on startup):
* `FORM_VERSIONING`: `true` by default. Switches on and off form versioning, which will add deployment-specific suffixes to form ids and paths on Formio server
* `FORMIO_URL`: `http://localhost:3001` by default - points to Formio server
* `FORMIO_LOGIN`: `root@root.root` by default - a user which will be used when uploading forms to Formio server
* `FORMIO_PASSWORD`: `root` by default - password for uploader user
* `FORMIO_JWT_EXPIRATION_TIME`: `240` by default - formio JWT token expiration time in seconds


Also, `FILE_STORAGE_URL` environment variable sets the file storage URL for custom file storage implementations
