# docker build -t android-ai .
docker run --rm -v $(pwd):/workspace -v ./.gradle:/root/.gradle android-ai
