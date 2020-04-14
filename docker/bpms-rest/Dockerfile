# Download and build bpms-rest from source code
FROM maven:3.6.3-jdk-11-openj9 as maven_builder

RUN apt-get update &&\
    apt-get install -y git &&\
    apt-get clean
RUN cd / &&\
	git clone https://github.com/Artezio/ART-BPMS-REST.git &&\
	cd /ART-BPMS-REST &&\
	mvn clean install -DskipTests=true

# Add built application to Wildfly
FROM jboss/wildfly:17.0.1.Final as wildfly

ARG JBOSS_CLI_ADMIN_LOGIN=admin
ARG JBOSS_CLI_ADMIN_PASSWORD=admin
ARG KEYCLOAK_ADAPTER_VERSION=7.0.0
ARG KEYCLOAK_ADAPTER_DOWNLOAD_URL=https://downloads.jboss.org/keycloak/${KEYCLOAK_ADAPTER_VERSION}/adapters/keycloak-oidc/keycloak-wildfly-adapter-dist-${KEYCLOAK_ADAPTER_VERSION}.tar.gz
ARG JBOSS_CLI=/opt/jboss/wildfly/bin/jboss-cli.sh

ARG NODEJS_VERSION=13.x

#Possible values are: h2 (embedded db), postgres, mysql, mariadb
ENV BPMS_REST_DB_VENDOR h2
#The following DB settings are only applied when DB vendor is not h2
ENV BPMS_REST_DB_HOST localhost
ENV BPMS_REST_DB_PORT 5432
ENV BPMS_REST_DB_NAME bpms
ENV BPMS_REST_DB_LOGIN sa
ENV BPMS_REST_DB_PASSWORD sa
ENV FILE_STORAGE_URL ""

ENV KEYCLOAK_HOST localhost
ENV KEYCLOAK_PORT 8180
ENV KEYCLOAK_SERVER_URL http://${KEYCLOAK_HOST}:${KEYCLOAK_PORT}/auth
ENV KEYCLOAK_CLIENT_ID bpms-rest
ENV KEYCLOAK_REALM master
ENV KEYCLOAK_USERNAME_ATTRIBUTE preferred_username

ENV BPMS_REST_CLI_ADMIN_LOGIN ${JBOSS_CLI_ADMIN_LOGIN}
ENV BPMS_REST_CLI_ADMIN_PASSWORD ${JBOSS_CLI_ADMIN_PASSWORD}
ENV MAX_HEAP_SIZE_MB 2048
ENV MAX_METASPACE_SIZE_MB 384
ENV JBOSS_ARGS ""

ENV JDBC_POSTGRES_VERSION 42.2.2
ENV JDBC_MYSQL_VERSION 5.1.46

# Deploy WAR
COPY --chown=jboss:0 --from=maven_builder /ART-BPMS-REST/bpms-rest/target/bpms-rest.war /opt/jboss/wildfly/standalone/deployments/bpms-rest.war

# Install database drivers
COPY --chown=jboss:0 tools /opt/jboss/tools/bpms-rest/
COPY ./wait-for-postgres.sh /
RUN chmod +x /opt/jboss/tools/bpms-rest/*.sh && \
    /opt/jboss/tools/bpms-rest/install-databases.sh

# Configure Wildfly	
RUN /opt/jboss/tools/bpms-rest/configure-wildfly.sh

USER root

# Setup demo database
RUN mkdir -p /opt/bpms-rest-h2-db && chown jboss:0 /opt/bpms-rest-h2-db
RUN yum -y install postgresql.x86_64

# Install NodeJS server
RUN cd /opt && \
    curl -sL https://rpm.nodesource.com/setup_$NODEJS_VERSION | bash -
RUN yum -y install nodejs

# Install formio libs
RUN npm install -g jsdom-global jsdom formiojs

USER jboss

EXPOSE 8080
EXPOSE 9990
EXPOSE 8787

CMD ["/opt/jboss/tools/bpms-rest/docker-entrypoint.sh"]
