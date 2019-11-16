FROM jboss/keycloak:7.0.0 as keycloak

ARG KEYCLOAK_ADMIN_LOGIN=admin
ARG KEYCLOAK_ADMIN_PASSWORD=password

COPY --chown=1000:0 keycloak.mv.db /opt/jboss/keycloak/standalone/data/keycloak.mv.db
COPY --chown=1000:0 keycloak.trace.db /opt/jboss/keycloak/standalone/data/keycloak.trace.db

RUN /opt/jboss/keycloak/bin/add-user.sh ${KEYCLOAK_ADMIN_LOGIN} ${KEYCLOAK_ADMIN_PASSWORD}

EXPOSE 8180

ENTRYPOINT [ "/opt/jboss/tools/docker-entrypoint.sh" ]

CMD ["-b", "0.0.0.0", "-Djboss.socket.binding.port-offset=100"]

