FROM openjdk:8
RUN apt-get update
RUN apt-get -y install maven
WORKDIR /root
CMD cd /root/chronstore/Chord;mvn install -Dmaven.test.skip=true;cd /root/chronstore/Fusion;mvn install -Dmaven.test.skip=true;cd /root/chronstore/ObjectStore;mvn compile exec:java