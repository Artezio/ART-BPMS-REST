#!/bin/bash

############
# DB setup #
############

# Lower case DB_VENDOR
BPMS_REST_DB_VENDOR=`echo $BPMS_REST_DB_VENDOR | tr A-Z a-z`

# Default to h2 if DB type not detected
if [ "$BPMS_REST_DB_VENDOR" == "" ]; then
    export BPMS_REST_DB_VENDOR="h2"
fi

# Set DB name
case "$BPMS_REST_DB_VENDOR" in
    postgres)
        DB_NAME="PostgreSQL";;
    mysql)
        DB_NAME="MySQL";;
    mariadb)
        DB_NAME="MariaDB";;
    mssql)
        DB_NAME="MSSQL";;
    oracle)
        DB_NAME="Oracle";;
    h2)
        DB_NAME="embedded H2";;
    *)
        echo "Unknown DB vendor $BPMS_REST_DB_VENDOR"
        exit 1
esac

# Append '?' in the beggining of the string if JDBC_PARAMS value isn't empty
export JDBC_PARAMS=$(echo ${JDBC_PARAMS} | sed '/^$/! s/^/?/')

# Convert deprecated DB specific variables
function set_legacy_vars() {
  local suffixes=(ADDR DATABASE USER PASSWORD PORT)
  for suffix in "${suffixes[@]}"; do
    local varname="$1_$suffix"
    if [ ${!varname} ]; then
      echo WARNING: $varname variable name is DEPRECATED replace with DB_$suffix
      export DB_$suffix=${!varname}
    fi
  done
}
set_legacy_vars `echo $BPMS_REST_DB_VENDOR | tr a-z A-Z`

# Configure DB

echo "========================================================================="
echo ""
echo "  BPMS-REST is using $DB_NAME database"
echo ""
echo "========================================================================="
echo ""

/bin/sh /opt/jboss/tools/bpms-rest/databases/change-database.sh $BPMS_REST_DB_VENDOR


###################
# Start BPMS-REST #
###################

echo "========================================================================="


# Start Wildfly and return result
echo "Starting BPMS-REST"
export JAVA_OPTS="-server -Xms256m -Xmx${MAX_HEAP_SIZE_MB}m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=${MAX_METASPACE_SIZE_MB}m
-Djava.net.preferIPv4Stack=true -Djboss.modules.system.pkgs=org.jboss.byteman -Djava.awt.headless=true -Djboss.as.management.blocking.timeout=1200
-DFORMIO_URL=${FORMIO_URL} -DFORMIO_LOGIN=${FORMIO_LOGIN} -DFORMIO_PASSWORD=${FORMIO_PASSWORD} -DFORM_VERSIONING=${FORM_VERSIONING}
-agentlib:jdwp=transport=dt_socket,address=0.0.0.0:8787,server=y,suspend=n"
exec /opt/jboss/wildfly/bin/standalone.sh "-c" "standalone.xml" "-b" "0.0.0.0" "-bmanagement" "0.0.0.0"
exit $?
