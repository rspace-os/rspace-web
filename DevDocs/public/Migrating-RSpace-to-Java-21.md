RSpace runs on Java 17 or Java 21. Moving to Java 21 is optional today and will become required in a later release; we will announce that in advance. This page covers a self-hosted install on Ubuntu with the distribution `tomcat10` package. If you run RSpace with [rspace-docker](https://github.com/rspace-os/rspace-docker), the image manages the JDK for you and there is nothing to do here.

Before starting, gracefully stop the Tomcat service. RSpace will be unavailable while you perform the steps below.

    sudo systemctl stop tomcat10

**Install the Java 21 package**

    sudo apt install openjdk-21-jre-headless

On Ubuntu 24.04 this is the default Java and may already be installed.

**Point Tomcat at Java 21**

Tomcat reads its JVM settings from `/etc/default/tomcat10`. Take a backup of that file, then open it in a text editor.

1. Replace `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. If `JAVA_HOME` is not set in that file, add the latter line.

2. None of the flags we recommend in `JAVA_OPTS` or `CATALINA_OPTS` were removed between Java 17 and Java 21, so most installs need no further edits. Flags that other tooling may have added and that Java 21 rejects include the biased-locking family (`-XX:+UseBiasedLocking`, `-XX:BiasedLockingStartupDelay=...`). If Tomcat fails to start, `sudo systemctl status tomcat10` names the unrecognised flag; remove it and try again.

3. Check `/usr/libexec/tomcat10/tomcat-locate-java.sh`. It contains a line like `for java_version in 21 17 11 ...`. If `21` is missing from that list, add it at the front.

**Start Tomcat and verify**

    sudo systemctl start tomcat10
    sudo systemctl status tomcat10

Once RSpace is up, confirm the Java version Tomcat is using:

    sudo bash /usr/share/tomcat10/bin/version.sh

It should report JVM version 21. If you encounter any issues you can email support@researchspace.com for support.
