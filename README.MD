# ART-BPMS-REST

ART-BPMS-REST is a wrapper for BPMS Camunda rest services. It is designed to provide simple communication between Camunda services and Formio server. General aspects of ART-BPMS-REST:
* possibility to use a form key in BPMN diagram components which points to Formio form
* converting data to Camunda and Formio specific formats before transmitting them between Formio server to Camunda services
* form data validation using both Formio and java validation
* logging 
* a form cache
* an interface and its base implementation of the file storage which is used by Formio file component, also there is an availability to provide your own implementation
* the implementation of Message Correlation and Signal services
* uploading the forms in process-archive to Formio server