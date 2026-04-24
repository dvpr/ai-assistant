#!/bin/bash

case "$1" in
    build)
        docker build -t android-ai .
        ;;
    publish)
        docker run --rm -v "$(pwd)":/workspace -v ./.gradle:/root/.gradle android-ai
        ;;
    *)
        echo "Usage: $0 {build|publish}"
        exit 1
        ;;
esac
