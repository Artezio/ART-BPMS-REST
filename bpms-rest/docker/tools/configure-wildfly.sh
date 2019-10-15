#!/bin/bash

# Install Keycloak adapter
curl -SL "${KEYCLOAK_ADAPTER_DOWNLOAD_URL}" | tar xvz -C /opt/jboss/wildfly/
/opt/jboss/wildfly/bin/add-user.sh --user ${BPMS_REST_CLI_ADMIN_LOGIN} --password ${BPMS_REST_CLI_ADMIN_PASSWORD} --silent --enable
$JBOSS_CLI --file=/opt/jboss/wildfly/bin/adapter-install-offline.cli -Dserver.config=standalone.xml
$JBOSS_CLI --file=/opt/jboss/wildfly/bin/adapter-elytron-install-offline.cli -Dserver.config=standalone.xml

# Create mail session
#$JBOSS_CLI --commands="embed-server --server-config=standalone-full.xml"\
#,"/subsystem=mail/mail-session=com.artezio.bpm:add(jndi-name=\"java:jboss/mail/com.artezio.bpm\")"\
#,stop-embedded-server

# Apply Keycloak adapter to war deployment
$JBOSS_CLI --commands="embed-server --server-config=standalone.xml"\
,"/subsystem=keycloak/secure-deployment=bpms-rest.war:add( \
	realm=\${env.KEYCLOAK_REALM}, \
	resource=\${env.KEYCLOAK_CLIENT_ID}, \
    enable-basic-auth=true, \
	public-client=true, \
	auth-server-url=\${env.KEYCLOAK_SERVER_URL}, \
	ssl-required=EXTERNAL, \
	principal-attribute=\${env.KEYCLOAK_USERNAME_ATTRIBUTE})"\
,stop-embedded-server
