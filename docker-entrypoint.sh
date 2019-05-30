#!/bin/sh

./scripts/wait_for_surfer.sh ${SURFER_URL}

if [ $? -eq 0 ]; then
    java -jar target/koi-clj-0.1.0-SNAPSHOT-standalone.jar
    tail -f /dev/null
fi
