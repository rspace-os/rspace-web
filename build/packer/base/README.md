Builds a base docker image with Tomcat, Java and a few linux utility commands.

To run:

 - ensure you have Docker daemon running (e.g. Docker Desktop on Mac)
 - Install `packer`
 - Edit `packer-rspace-base-docker.json` for a new tag name to reflect the new version
 
     packer build packer-rspace-base-docker.json
     
This will generate a tagged image on the builld machine.

To push to Dockerhib:


    docker push rspaceops/rspace-services:TAGNAME
    
e.g. 

    docker push rspaceops/rspace-services:rspace-base_ubuntu2004_java8_tomcat8.5