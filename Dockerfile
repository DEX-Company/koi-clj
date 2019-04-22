FROM clojure
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
COPY . /usr/src/app
ENV SURFER_URL='http://13.67.33.157:8080'
EXPOSE 3000
CMD ["java", "-jar", "target/koi-clj-0.1.0-SNAPSHOT-standalone.jar"]
