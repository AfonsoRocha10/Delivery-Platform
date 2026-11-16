FROM jenkins/jenkins:2.580.2-lts-jdk21

USER root
RUN apt-get update \
       && apt-get install -y --no-install-recommends ca-certificates curl \
       && install -m 0755 -d /etc/apt/keyrings \
       && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
       && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
            > /etc/apt/sources.list.d/docker.list \
       && apt-get update \
       && apt-get install -y --no-install-recommends docker-ce-cli \
       && rm -rf /var/lib/apt/lists/*
User jenkins

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN  jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

COPY  casc/ /etc/jenkins/casc
ENV CASC_JENKINS_CONFIG=/etc/jenkins/casc

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false"