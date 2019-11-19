# hub.docker.com/r/_/node/
FROM node:lts-alpine

ARG FORMIO_VERSION=v1.59.0
ARG ART_FORMIO_EXT_VERSION=1.0
ARG FORMIO_CLIENT_VERSION=v1.5.0

# "bcrypt" requires python/make/g++, all must be installed in alpine
# (note: using pinned versions to ensure immutable build environment)
RUN apk update && \
    apk upgrade && \
    apk add python=2.7.16-r1 && \
    apk add make=4.2.1-r2 && \
    apk add g++=8.3.0-r0 && \
    apk add git

# Set this to inspect more from the application. Examples:
#   DEBUG=formio:db (see index.js for more)
#   DEBUG=formio:*
#ENV DEBUG=""

RUN git clone -b ${FORMIO_VERSION} --depth 1 https://github.com/formio/formio.git
RUN git clone -b ${ART_FORMIO_EXT_VERSION} --depth 1 https://github.com/Artezio/ART-FORMIO-EXT.git
RUN git clone -b ${FORMIO_CLIENT_VERSION} https://github.com/formio/formio-app-formio.git /formio/client

WORKDIR /formio

COPY client_patch .
RUN cp server.js server-origin.js
RUN cp /ART-FORMIO-EXT/server.js .
RUN cp -r /ART-FORMIO-EXT/src .
RUN npm install

ENTRYPOINT [ "npm", "start" ]
