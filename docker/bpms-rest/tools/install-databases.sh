mkdir -p /opt/jboss/wildfly/modules/system/layers/base/com/mysql/jdbc/main
cd /opt/jboss/wildfly/modules/system/layers/base/com/mysql/jdbc/main
curl -L https://repo1.maven.org/maven2/mysql/mysql-connector-java/$JDBC_MYSQL_VERSION/mysql-connector-java-$JDBC_MYSQL_VERSION.jar > mysql-jdbc.jar
cp /opt/jboss/tools/bpms-rest/databases/mysql/module.xml .

mkdir -p /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/jdbc/main
cd /opt/jboss/wildfly/modules/system/layers/base/org/postgresql/jdbc/main
curl -L https://repo1.maven.org/maven2/org/postgresql/postgresql/$JDBC_POSTGRES_VERSION/postgresql-$JDBC_POSTGRES_VERSION.jar > postgres-jdbc.jar
cp /opt/jboss/tools/bpms-rest/databases/postgres/module.xml .
