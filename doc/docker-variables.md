# Docker variables

## BPMS-REST variables

### Arguments

`JBOSS_CLI` - path to `jboss-cli.sh` of the server for bpms-rest application <br/>
`JBOSS_CLI_ADMIN_LOGIN` - jboss admin username <br/>
`JBOSS_CLI_ADMIN_PASSWORD` - jboss admin password <br/>
`KEYCLOAK_ADAPTER_VERSION` - keycloak adapter version <br/>
`KEYCLOAK_ADAPTER_DOWNLOAD_URL` - keycloak adapter download url. By default is commented. Uncomment and use it if you download <br/>
keycloak adapter from url different to official site url <br/>

### Environment variables

`BPMS_REST_DB_VENDOR` - database vendor for bpms-rest application <br/>
`BPMS_REST_DB_HOST` - host of the database for bpms-rest <br/>
`BPMS_REST_DB_NAME` - name of the database for bpms-rest <br/>
`BPMS_REST_DB_LOGIN` - database username <br/>
`BPMS_REST_DB_PASSWORD` - database password <br/>
`FORMIO_HOST` - host of the Formio server <br/>
`FORM_VERSIONING` - indicates if formio versioning is enabled or not <br/>
`JBOSS_ARGUMENTS` - use this variable to override standard wildfly arguments <br/>
`KEYCLOAK_HOST` - host of the keycloak server <br/>
`KEYCLOAK_CLIENT_ID` - keycloak client id to be used by bpms-rest <br/>
`KEYCLOAK_REALM` - keycloak realm to be used by bpms-rest <br/>
`KEYCLOAK_USERNAME_ATTRIBUTE` - OpenID Connect ID Token attribute to populate the UserPrincipal name with <br/>
`MAX_HEAP_SIZE_MB` - max size of heap is guaranteed to the server <br/>
`MAX_METASPACE_SIZE_MB` - max size of metaspace is guaranteed to the server <br/>
`JDBC_POSTGRES_VERSION` - version of postgres jdbc driver <br/>
`JDBC_MYSQL_VERSION` - version of mysql jdbc driver <br/>
`JDBC_MSSQL_VERSION` - version of mssql jdbc driver <br/>
`JDBC_MARIADB_VERSION` - version of mariadb jdbc driver <br/>
`JDBC_ORACLE_VERSION` - version of oracle jdbc driver <br/>

## Keycloak variables

`KEYCLOAK_ADMIN_LOGIN` - keycloak admin username <br/>
`KEYCLOAK_ADMIN_PASSWORD` - keycloak admin password <br/>

## Formio variables

`FORMIO_ROOT_EMAIL` - formio admin email <br/>
`FORMIO_ROOT_PASSWORD` - formio admin password <br/>
`MONGODB_URL` - a url to mongo database <br/>