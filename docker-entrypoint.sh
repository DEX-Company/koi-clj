#!/bin/sh

#./scripts/wait_for_surfer.sh ${AGENT_URL}

VERSION=0.1.7-SNAPSHOT

if [ $? -eq 0 ]; then
    port=${KOI_PORT} java -jar target/koi-clj-${VERSION}-standalone.jar
    tail -f /dev/null
fi
