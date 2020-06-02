FROM jboss/keycloak:7.0.1 as keycloak

ARG KEYCLOAK_ADMIN_LOGIN=admin
ARG KEYCLOAK_ADMIN_PASSWORD=password

COPY --chown=1000:0 keycloak.mv.db /opt/jboss/keycloak/standalone/data/keycloak.mv.db

RUN /opt/jboss/keycloak/bin/add-user.sh ${KEYCLOAK_ADMIN_LOGIN} ${KEYCLOAK_ADMIN_PASSWORD}

ENV KEYCLOAK_PORT 8180
ENV KEYCLOAK_HTTPS_PORT 8444

EXPOSE ${KEYCLOAK_PORT}
EXPOSE ${KEYCLOAK_HTTPS_PORT}

ENTRYPOINT ["/bin/sh", "-c"]

CMD ["/opt/jboss/tools/docker-entrypoint.sh -b 0.0.0.0 -Djboss.http.port=$KEYCLOAK_PORT -Djboss.https.port=$KEYCLOAK_HTTPS_PORT"]

