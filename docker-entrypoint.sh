#!/bin/sh

CURL_CMD="curl -w httpcode=%{http_code}"

# See retry options https://stackoverflow.com/a/42873372/845843
CURL_MAX_CONNECTION_TIMEOUT="--retry-max-time 480 --retry-connrefused --retry 10 "

# perform curl operation
CURL_RETURN_CODE=0
CURL_OUTPUT=`${CURL_CMD} ${CURL_MAX_CONNECTION_TIMEOUT} ${SURFER_URL} 2> /dev/null` || CURL_RETURN_CODE=$?
echo "Waiting for Surfer to be up at $SURFER_URL"
if [ ${CURL_RETURN_CODE} -ne 0 ]; then
    echo "Curl connection failed with return code - ${CURL_RETURN_CODE}"
else
    echo "Curl connection success"
    java -jar target/koi-clj-0.1.0-SNAPSHOT-standalone.jar
    tail -f /dev/null
fi


