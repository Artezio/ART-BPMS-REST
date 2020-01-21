# Docker variables

## Ports

`BPMS_REST_APP_PORT` - wildfly server public port <br/>
`BPMS_REST_MANAGEMENT_PORT` - wildfly server management port <br/>
`COCKPIT_PORT` - camunda cockpit port <br/>
`KEYCLOAK_PORT` - keycloak server public port <br/>
`BPMS_REST_DB_PORT` - application database port

## BPMS-REST variables

### Arguments

`JBOSS_CLI` - path to `jboss-cli.sh` of the server for bpms-rest application <br/>
`JBOSS_CLI_ADMIN_LOGIN` - jboss admin username <br/>
`JBOSS_CLI_ADMIN_PASSWORD` - jboss admin password <br/>
`KEYCLOAK_ADAPTER_VERSION` - keycloak adapter version <br/>
`KEYCLOAK_ADAPTER_DOWNLOAD_URL` - keycloak adapter download url. By default is commented. Uncomment and use it if you download 
keycloak adapter from url different to official site url <br/>

### Environment variables

`BPMS_REST_DB_VENDOR` - database vendor <br/>
`BPMS_REST_DB_HOST` - host of the database <br/>
`BPMS_REST_DB_NAME` - name of the database <br/>
`BPMS_REST_DB_LOGIN` - database username <br/>
`BPMS_REST_DB_PASSWORD` - database password <br/>
`FILE_STORAGE_URL` - file storage URL for custom file storage implementations. By default, files are stored inside Base64 Data Urls
`JBOSS_ARGS` - use this variable for additional custom wildfly arguments <br/>
`KEYCLOAK_HOST` - host of the keycloak server <br/>
`KEYCLOAK_CLIENT_ID` - keycloak client id to be used by bpms-rest <br/>
`KEYCLOAK_REALM` - keycloak realm to be used by bpms-rest <br/>
`KEYCLOAK_USERNAME_ATTRIBUTE` - OpenID Connect ID Token attribute to populate the UserPrincipal name with <br/>
`MAX_HEAP_SIZE_MB` - max size of heap is guaranteed to the server <br/>
`MAX_METASPACE_SIZE_MB` - max size of metaspace is guaranteed to the server <br/>
`JDBC_POSTGRES_VERSION` - version of postgres jdbc driver <br/>
`JDBC_MYSQL_VERSION` - version of mysql jdbc driver <br/>

## Camunda variables

`BPMS_REST_DB_HOST` - host of the database <br/> 
`BPMS_REST_DB_NAME` - name of the database <br/>
`BPMS_REST_DB_LOGIN` - database username <br/>
`BPMS_REST_DB_PASSWORD` - database password <br/>
`DB_CONN_MAXACTIVE` - the maximum number of active connections <br/>
`DB_CONN_MAXIDLE` - the maximum number of idle connections <br/>
`DB_CONN_MINIDLE` - the minimum number of idle connections <br/>
`DB_DRIVER` - the database driver class name, supported are mysql, postgresql:
    * mysql: `DB_DRIVER=com.mysql.jdbc.Driver` <br/>
    * postgresql: `DB_DRIVER=org.postgresql.Driver` <br/>
`DB_VALIDATE_ON_BORROW` - validate database connections before they are used <br/>
`DB_VALIDATION_QUERY` - the query to execute to validate database connections 
`JAVA_OPTS` - value of correspondent environment variable

## Keycloak variables

`KEYCLOAK_ADMIN_LOGIN` - keycloak admin username <br/>
`KEYCLOAK_ADMIN_PASSWORD` - keycloak admin password <br/>
