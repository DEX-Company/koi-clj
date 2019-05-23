FROM clojure
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
COPY . /usr/src/app
ENV SURFER_URL='http://localhost:8080'
EXPOSE 3000
ENTRYPOINT ["/usr/src/app/docker-entrypoint.sh"]
