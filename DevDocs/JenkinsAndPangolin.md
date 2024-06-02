# Jenkins and Pangolin Build server notes

## Configurations and builds

These are stored in subfolders of `/var/lib/jenkins`. Most of the time you
don't want to edit any files, but you may need to get hold of build artifacts.

## MySQL

On Jenkins, several MySQL databases are needed to support the various builds:
  - `RS_SeleniumTests`
  - `RS_releasebranch`
  - `testLiquibaseUpdate`
Feature branch builds are creating own database based on branch name.

## Scripts

RSpace-related scripts are in `/home/builder/rspace-scripts`.

## Pangolin

Pangolin test server runs Ubuntu16, MySQL 5.7, Tomcat 8.5 and is thus
representative of customer server configs.

Pangolin runs various Tomcats. All RSpace related stuff is in
`/home/builder/rspace`, including Tomcats and file stores.
Currently, there are scripts to redeploy `acceptance/Selenium` builds.

pangolin is set up to `scp` build from Jenkins using passphrase-less
public/private key.

Using `crontab -e` you can edit the nightly time, that these update
scripts are executed.

P8085 and P8087 are persistent and have been used since 2014. They are
used for manual testing and to simulate a real server with lots of data.

```bash
# port 8085
./deployRSpace.ch [-arestart] rs_acceptance.sh

# port 8087
./deployRSpace.ch [-arestart] cloudConfig.sh
```

These can also be stopped started using SystemD e.g.

```bash
sudo systemctl start p85
sudo systemctl start p87
```

These services will start on boot but not on shut-down or crash.
