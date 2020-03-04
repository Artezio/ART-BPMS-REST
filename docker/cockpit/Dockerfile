FROM camunda/camunda-bpm-platform:7.10.0

ENV BPMS_REST_DB_HOST host.docker.internal
ENV BPMS_REST_DB_PORT 5432
ENV BPMS_REST_DB_NAME bpms
ENV BPMS_REST_DB_LOGIN postgres
ENV BPMS_REST_DB_PASSWORD root

COPY ./wait-for-postgres.sh /

USER root

RUN apk add postgresql

USER camunda

RUN rm -fr /camunda/webapps/camunda-invoice

EXPOSE 8080
