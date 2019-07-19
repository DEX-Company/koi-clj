#!/bin/bash

set -ex

PARENT_DIR=$(basename "${PWD%/*}")
CURRENT_DIR="${PWD##*/}"
ORGNAME="dexcompany"
IMAGE_NAME="$ORGNAME/$CURRENT_DIR"
TAG="${1}"

REGISTRY="hub.docker.com"

docker build -t ${IMAGE_NAME}:${TAG} -t ${IMAGE_NAME}:latest .
docker push ${IMAGE_NAME}
