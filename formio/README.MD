##Formio server Dockerfile

This Dockerfile is used for building Docker image with Formio server modified for supporting Dry validation and Cleanup features needed to run ART-BPMS

When using docker-compose, MongoDB is also started. If required, external MongoDB server could be used by setting corresponding [environment variable](#environment-variables)

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