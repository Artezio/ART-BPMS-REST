##Formio server Dockerfile

This Dockerfile is used to build Docker image with Formio server modified for supporting Dry validation and Cleanup features needed to run ART-BPMS

MongoDB container is started with Formio server. If required, external MongoDB server could be used by setting corresponding [environment variable](#environment-variables)

### Prerequisites

* [Docker]

### Build and run with docker-compose
1. Edit file `.env` to change default environment variables or pass variables via the command line
1. Run `docker-compose up`

### Environment variables

* `MONGODB_URL` - URL to MongoDB server. Default is `mongodb://mongo:27017/formioapp`
* `ROOT_EMAIL` - email of root user. Default is `root@root.root`
* `ROOT_PASSWORD` - password of root user. Default is `root`

[Docker]: https://www.docker.com/