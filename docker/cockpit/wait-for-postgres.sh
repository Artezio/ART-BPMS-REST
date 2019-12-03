#!/bin/sh

host=$1
port=$2
db=$3
username=$4
export PGPASSWORD=$5

shift 5
cmd="$@"

until psql -h $host -p $port -U $username -w -c "select 1" -d $db> /dev/null 2>&1; do
  echo "Waiting for postgres server..."
  sleep 1
done

>&2 echo "Postgres is up - executing command"
exec $cmd