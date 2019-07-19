#!/bin/sh

./scripts/wait_for_surfer.sh ${AGENT_URL}

if [ $? -eq 0 ]; then
    port=${KOI_PORT} java -jar target/koi-clj-0.1.4-standalone.jar
    tail -f /dev/null
fi
