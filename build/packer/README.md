## Packer build notes

This folder contains Packer files to build Docker images for RSpace

Each image definition is in a subfolder.

1. `base` is a base image of OS, Java and Tomcat. 
2. `db` builds a ready-to-run RSpace database image of MySQL 5.7 with pre-populated baseline tables
3. `web`  depends on `base` and adds web application and other files.

Assuming that `base` and `db` images are fairly stable, only `web` will need to rebuilt for a new version of RSpace.

The Docker-README file contains information on how to launch the containers. 

## Building on Jenkins

A Jenkins RSpace build can be configured to build RSpace-web Image, deploy it on AWS (eu-west-2 london) and deploy the built RSpace, and launch it. 

There is one error-prone step, which is to add the AWS target group to our listener - this does not always work. You will need to go into the console and add the target group matching the instance name to the ALB listener manually.

Other notes:

See project `aws-scripts/rspace-docker-launch.sh` for details of the AWS ids and resources used

To ssh into the EC2 Docker host instance, ssh from bastion\_host (*not* kudu) using the _private_ IP address of the instance. 

e.g.

    ssh -i builder.pem ubuntu@10.0.2.222
    
 You can then access the RSpace container. Logs are in usual place e.g. `/media/rspace/logs-audit`
 
    docker ps
    docker exec -it  <container_id>  bash
    

    

