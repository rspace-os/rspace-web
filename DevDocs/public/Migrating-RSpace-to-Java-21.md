Java 17 remains the recommended version. RSpace also runs on Java 21, and Java 21 will become required in a later release, which we will announce in advance. This page covers a self-hosted install on Ubuntu with the distribution `tomcat10` package. If you run RSpace with [rspace-docker](https://github.com/rspace-os/rspace-docker), the image manages the JDK for you and there is nothing to do here.

Before starting, gracefully stop the Tomcat service. RSpace will be unavailable while you perform the steps below.

    sudo systemctl stop tomcat10

**Install the Java 21 package**

    sudo apt install openjdk-21-jre-headless

On Ubuntu 24.04 this is the default Java and may already be installed.

**Point Tomcat at Java 21**

Tomcat reads its JVM settings from `/etc/default/tomcat10`. Take a backup of that file, then open it in a text editor.

Replace `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`. If `JAVA_HOME` is not set in that file, add the latter line.

**Start Tomcat and verify**

    sudo systemctl start tomcat10
    sudo systemctl status tomcat10

If Tomcat fails to start, the status output names any JVM flag Java 21 rejects (for example `-XX:+UseBiasedLocking`); remove it from `/etc/default/tomcat10` and start again.

Once RSpace is up, confirm the Java version of the running Tomcat service by using its process's executable:

    tomcat_pid=$(systemctl show tomcat10 --property=MainPID --value)
    sudo "/proc/$tomcat_pid/exe" -version

It should report Java version 21. (Don't use Tomcat's `version.sh`; it starts a separate JVM.) If you encounter any issues you can email support@researchspace.com for support.
