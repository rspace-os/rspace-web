**Install the java 17 package**
`sudo apt install openjdk-17-jre-headless`

**Modify the Setenv file**

Before starting, gracefully stop the tomcat9 service. As a result, this will stop RSpace from running while you perform the steps below.

You will be working on this file "/usr/share/tomcat9/bin/setenv.sh", please take a backup of the file before making the changes below. This is the location of the file on Ubuntu 20.04 / 22.04 running tomcat9. Open this file with your preferred text editor.

1. Replace "**JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64**" at the top of that file with "**JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64**". If it's not already there, add the latter to the top of your file.

2. There are certain values / flags inside the setenv.sh file that are no longer supported in Java 17. We used to use these when running RSpace on Java 11 so you'll need to remove them. Perform a search for the following flags and remove them if they are found inside the file.
    1. -XX:+CMSClassUnloadingEnabled
    2. -XX:+UseConcMarkSweepGC
    3. -XX:MaxPermSize=256m

   Some customers may be running other non-supported flags, if tomcat fails to start because of another unsupported flag, systemd will tell you the flag causing the issue when you perform a "`sudo systemctl status tomcat9`". If this is the case, remove the flag causing issues and try starting tomcat again.

3. There is a file at "/usr/libexec/tomcat9/tomcat-locate-java.sh" that needs to be updated. Open this file in a text editor and add the following number to this line. Add the number 17, before the number 11 as seen in the example below.

   	Before:  for java_version in 11 10 9 8
   	After:  for java_version in 17 11 10 9 8

You can now start the tomcat service back up, monitor the systemctl status to make sure tomcat is starting correctly. After tomcat has started and RSpace is up, you can check your Java version by running this command:

`sudo bash /usr/share/tomcat9/bin/version.sh`

It should tell you that you're running Java (JVM) 17. With that, the update is complete. If you encounter any issues you can email support@researchspace.com for support.