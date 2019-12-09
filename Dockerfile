FROM clojure
RUN mkdir -p /usr/src/app
RUN lein uberjar
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
COPY . /usr/src/app
ENV AGENT_URL='http://localhost:8080'
ENV KOI_PORT=8191
EXPOSE 8191
ENTRYPOINT ["/usr/src/app/docker-entrypoint.sh"]
