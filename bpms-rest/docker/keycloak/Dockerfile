FROM jboss/keycloak:4.8.3.Final as keycloak

ARG KEYCLOAK_ADMIN_LOGIN=admin
ARG KEYCLOAK_ADMIN_PASSWORD=password

RUN /opt/jboss/keycloak/bin/add-user.sh ${KEYCLOAK_ADMIN_LOGIN} ${KEYCLOAK_ADMIN_PASSWORD}

EXPOSE 8080

ENTRYPOINT [ "/opt/jboss/tools/docker-entrypoint.sh" ]

CMD ["-b", "0.0.0.0"]
