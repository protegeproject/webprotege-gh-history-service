FROM openjdk:21
LABEL maintainer="protege.stanford.edu"

ARG JAR_FILE
COPY target/${JAR_FILE} webprotege-gh-history-service.jar
ENTRYPOINT ["java","-jar","/webprotege-gh-history-service.jar"]