# hub.docker.com/r/_/node/
FROM node:lts-alpine

ARG FORMIO_VERSION=v1.47.0

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

RUN git clone https://github.com/formio/formio.git

WORKDIR /formio

RUN git checkout tags/${FORMIO_VERSION}
COPY src/middleware/submissionHandler.js .
RUN npm install

ENTRYPOINT [ "npm", "start" ]
