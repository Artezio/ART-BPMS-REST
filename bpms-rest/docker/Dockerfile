# ----------------------------------------------------------------------------------
# Download and build bpms-rest from source code
FROM maven:3.5.2-jdk-8-alpine as maven_builder

RUN apk add --no-cache git
RUN cd / &&\
	git clone https://github.com/Artezio/ART-BPMS-REST.git &&\
	cd /ART-BPMS-REST/bpms-rest &&\
	mvn clean install -Dmaven.test.skip=true


# ----------------------------------------------------------------------------------
# Add built application to Wildfly
FROM jboss/wildfly:17.0.0.Final as wildfly

ARG JBOSS_CLI_ADMIN_USERNAME=admin
ARG JBOSS_CLI_ADMIN_PASSWORD=admin
ARG KEYCLOAK_ADAPTER_VERSION=4.8.3.Final
ARG KEYCLOAK_ADAPTER_DOWNLOAD_URL=https://downloads.jboss.org/keycloak/${KEYCLOAK_ADAPTER_VERSION}/adapters/keycloak-oidc/keycloak-wildfly-adapter-dist-${KEYCLOAK_ADAPTER_VERSION}.tar.gz
ARG JBOSS_CLI=/opt/jboss/wildfly/bin/jboss-cli.sh

# Possible values are: h2 (embedded db), postgres, mysql, mariadb
ENV BPMS_REST_DB_VENDOR h2
# The following DB settings are only applied when DB vendor is not h2
ENV BPMS_REST_DB_HOST localhost
ENV BPMS_REST_DB_PORT 5432
ENV BPMS_REST_DB_NAME bpms
ENV BPMS_REST_DB_LOGIN postgres
ENV BPMS_REST_DB_PASSWORD postgres
ENV KEYCLOAK_SERVER_URL http://localhost:9080/auth
ENV KEYCLOAK_CLIENT_ID bpms-rest
ENV KEYCLOAK_REALM master
ENV KEYCLOAK_USERNAME_ATTRIBUTE preferred_username

ENV MAX_HEAP_SIZE_MB 2048
ENV MAX_METASPACE_SIZE_MB 384
ENV JBOSS_ARGUMENTS ""

ENV JDBC_POSTGRES_VERSION 42.2.2
ENV JDBC_MYSQL_VERSION 5.1.46
ENV JDBC_MSSQL_VERSION 6.4.0.jre7
ENV JDBC_MARIADB_VERSION 2.2.3 
ENV JDBC_ORACLE_VERSION ojdbc6

# Deploy WAR
COPY --chown=jboss:0 --from=maven_builder /ART-BPMS-REST/bpms-rest/target/bpms-rest.war /opt/jboss/wildfly/standalone/deployments/bpms-rest.war

# Install database drivers
COPY --chown=jboss:0 --from=maven_builder /ART-BPMS-REST/bpms-rest/docker/tools /opt/jboss/tools/bpms-rest/
RUN chmod +x /opt/jboss/tools/bpms-rest/*.sh && \ 
    /opt/jboss/tools/bpms-rest/install-databases.sh

# Configure Wildfly	
RUN /opt/jboss/tools/bpms-rest/configure-wildfly.sh

# Setup demo database
USER root
RUN touch /opt/bpms-rest.h2.db && chown jboss:0 /opt/bpms-rest.h2.db

USER jboss

EXPOSE 8080
EXPOSE 9990

CMD ["/opt/jboss/tools/bpms-rest/docker-entrypoint.sh"]
