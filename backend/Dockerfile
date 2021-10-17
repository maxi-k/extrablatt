FROM clojure
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
# fetch dependencies
COPY project.clj /usr/src/app/
RUN lein deps
# build jar file
COPY . /usr/src/app
RUN mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" app-standalone.jar
# execute it
EXPOSE 3000
CMD ["java", "-jar", "app-standalone.jar"]
