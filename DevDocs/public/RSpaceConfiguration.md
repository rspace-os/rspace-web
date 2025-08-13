
Installation and configuration notes for RSpace (last updated: May 2023)
=================================================================

!! Please note that you should first go through the instructions in the Server Setup guides before attempting to install the RSpace application !!
In particular, you must have set up the database and imported the database schema before proceeding.

These notes give installation and configuration information for the latest  
release of RSpace.

These instructions assume Tomcat9, i.e. in command listings we use 'tomcat9'.
Please change this to 'tomcat8' if you are using it, but also note that Tomcat8 is EOL soon, and we recommend Tomcat9.

For user-oriented documentation, please see:

https://researchspace.helpdocs.io/

This document also enumerates all server-side deployment properties that may modulate RSpace behaviour.
There are many properties; and not all combinations are valid configurations.
Please proceed with caution and ask RSpace support for guidance if unsure.

Contents of the release bundle
-------------------------------

The unzipped release should contain:

* RSpace installation notes.
* Server pre-requisite installation notes.
* An example server log file illustrating successful server startup logs.
* The RSpace web application .war file
* A configuration file, deployment.properties
* A folder for 3rd party licenses
* A MySQL script to initialise the database.

What is covered in this document?

* Installing the Application
* Licensing
* Editing properties
* Configuring Tomcat
* Starting the application
* Login
* Application updates
* Troubleshooting
* Logging

Installing RSpace for the first time
--------------------------

The application install is a fairly simple deployment for a Tomcat .war file

First we download the package zip from www.researchspace.com using the username and password issued by ResearchSpace.

    cd ~
    wget --user=USERNAME --ask-password https://operations.researchspace.com/software/rspace/rspace-%VERSION%.zip
    unzip rspace-%VERSION%.zip (or version you just downloaded)

Copy config into place

    sudo mkdir /etc/rspace
    sudo cp deployment.properties /etc/rspace/
    sudo cp externalLicenses/license.cxl /etc/rspace/

The RSpace application will only run within the Tomcat webapp folder /ROOT so this should be cleared and the new war file copied into place as ROOT.war

E.g. on Ubuntu

    sudo rm -rf /var/lib/tomcat9/webapps/ROOT/
    sudo cp researchspace-<VERSION>-RELEASE.war /var/lib/tomcat9/webapps/ROOT.war

In addition,  RSpace must be able to create files in the Tomcat home folder.
E.g on Ubuntu:

    sudo chown tomcat:tomcat /var/lib/tomcat9

Configuring Tomcat
------------------

Some variables in Tomcat need to be set for the application to work properly; there are several ways of setting these. 
In our example, we will assume no other Tomcat applications are running on the server.

In Ubuntu these settings are set in the following file.

    /etc/default/tomcat9

So we edit this file and set the following;

    sudo vim /etc/default/tomcat9

    JAVA_OPTS="-Xms512m -Xmx2048m
      -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/media/rspace/logs-audit"
    CATALINA_OPTS="-DpropertyFileDir=file:/etc/rspace/
     -DRS_FILE_BASE=/PATH/TO/FILESTORAGE -Djava.awt.headless=true\
     -Dliquibase.context=run -Dspring.profiles.active=prod -Djmelody.dir=/media/rspace/jmelody"
    
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64

(Adjust java_home variable to be that of the installed java jdk)
PLEASE NOTE: "/PATH/TO/FILESTORAGE" should be the server path to your filestore eg. /data/rspace-filestore

The `-XX:+HeapDumpOnOutOfMemoryError` and `-XX:HeapDumpPath` are optional arguments that  set a  path to a folder that a heap dump can be written to in the event of an OutOfMemory error.
This is very useful for error diagnosis. A suitable folder would be writable by Tomcat, for example the folder holding the error logs.

The `jmelody.dir` holds a path to a folder that is writable by Tomcat and stores records of CPU/memory usage
etc for trouble-shooting and monitoring of the server. To create this folder:

     cd /etc/rspace
     mkdir jmelody
     chmod 755 jmelody
     sudo chown tomcat9:tomcat9 jmelody


NOTE: In CentOS these settings are in the following file.

    /etc/tomcat/tomcat.conf 

### Tomcat performance optimization

The default Tomcat configuration is designed for development usage, not production.
There are several changes you can make to configure Tomcat better for production usage. These are not compulsory but may result in better performance.

####AJP

If you are using AJP protocol in Apache, remember to uncomment the ajp/8009 connector in Tomcat `server.xml` file in `/var/lib/tomcat9/conf`

##### JSP compilationn

In file web.xml in $TOMCAT_HOME/conf folder, add the following init parameters to the 'jsp' servlet.
For more information see the 'JSPs' chapter of Tomcat documentation.

    <init-param>
        <param-name>development</param-name>
        <param-value>false</param-value>
    </init-param>
    <init-param>
        <param-name>trimSpaces</param-name>
        <param-value>true</param-value>
    </init-param>
    <init-param>
        <param-name>genStringAsCharArray</param-name>
        <param-value>true</param-value>
    </init-param>

##### Logging
Secondly, you can disable console logging: open $TOMCAT_HOME/conf/logging.properties and remove the logger 'java.util.logging.ConsoleHandler' from the list of handlers.
E.g.

    .handlers = 1catalina.org.apache.juli.AsyncFileHandler

By default tomcat makes daily logs of accesses and these build up over time to fill up the default disk, see https://tomcat.apache.org/tomcat-7.0-doc/config/valve.html#Access_Logging.  If you want to limit this behaviour, you can set the org.apache.catalina.valves.AccessLogValve to have the property 'rotatable = "false"', and use the logrotate utility to do some rotation and deletion of older logs.

Licensing
--------

RSpace uses an external license server to monitor and restrict the number of active users  on each installation.
In order to use RSpace effectively, the installation server should have external access over the internet to https://legacy.researchspace.com:8093 to ensure the license can be verified.

* Adding an RSpace license: please see section 'Editing Properties' below.

Editing properties
------------------

There is a property file  called `deployment.properties` in the release which contains some properties that MUST be set for your installation. They appear in that file at the top in the Mandatory Properties section.
Set these mandatory properties before going further. These properties include:

- **server.urls.prefix** The Base URL of the  RSpace instance - e.g. https://rspace.myworkplace.com
- **email.signup.title.from** Configures the 'title' field of email notifications sent by RSpace
- **license.key** The license key will be provided by ResearchSpace
- **rs.customer.name** Name of your company/institution, e.g. ResearchSpace
- **rs.customer.name.short** Short name of your company/institution, e.g. RSpace

By default, the `deployment.properties` file can be found in the `/etc/rspace` directory.
 
### Document previews

A separate application is used to generate  document previews of Office/OpenOffice documents. If the application is not installed, then these previews will not be available to users.

This application can either be installed as a standalone Java application, or as a Docker service (recommended)

#### As a standalone application

To install, download the application from the RSpace download site:

    wget --user=<username> --password=<password> https://operations.researchspace.com/software/rspace/aspose-app-VERSION.zip

replacing VERSION with the current release version and using your download credentials in place of <username> and <password>

Unzip and follow the installation instructions in the file `Usage.md`.

There are several deployment properties relating to document preview generation. These three are **mandatory** if you want document previewing enabled:

* **aspose.license** 	 	Absolute file path to Aspose license E.g.

  aspose.license=/etc/rspace/aspose/Aspose-Total-Java.lic

* **aspose.app** 	 	Absolute file path to Aspose standalone document converter E.g.

  aspose.app=/etc/rspace/aspose/aspose-app.jar

* **aspose.logfile**      Absolute path to Aspose document converter's log file. E.g.

  aspose.logfile=/etc/rspace/aspose/logs.txt

##### Optional Aspose logging

* **aspose.logLevel** The log level (default is INFO) e.g.

    aspose.logLevel=WARN

* **aspose.jvmArgs**  Optional jvm args to pass to application. E.g.

    aspose.jvmArgs=-Xmx1024m

#### As a Docker service

Please see https://researchspace.helpdocs.io/article/5k7qib0n3t-installation-of-rspace-add-on-services for details

### Snapgene service

To set a URL of a Snapgene server, use

* **snapgene.web.url** The web URL of the Snapgene server, e.g. `snapgene.web.url=https://my-snapgene-service.myedu.edu`

Instructions on setting up a Snapgene server is in our Help documentation https://researchspace.helpdocs.io/article/5k7qib0n3t-installation-of-rspace-add-on-services

### Chemistry service

Instructions on setting up a Chemistry server is in our Help documentation https://documentation.researchspace.com/article/1jbygguzoa-setting-up-the-new-rspace-chemistry-service-on-docker

### MySQL

You can override the default MySQL connection settings in `deployment.properties`

The properties are as follows:

* **jdbc.url**  	 The database URL, e.g., `jdbc:mysql://localhost:3306/rspace?useSSL=false`
* **jdbc.username** The MySQL username
* **jdbc.password** The MySQL password

### Email

For small trials, you are welcome to use our email server, but if this is not suitable or you wish to use your own email server, then please edit these settings.
You can edit these in `deployment.properties`

* **email.enabled** Will enable sending of emails. Default=`true`. If false, email content is merely logged.
* **mail.default.from** Value of 'from' field of email - i.e., where the email appears to come from
* **mail.transport.protocol** Mail protocol - default `smtp`
* **mail.emailAccount** The mail account of the email server,  e.g., support@xxx.com
* **mail.password** Mail account password
* **mail.emailHost** E.g., auth.smtp.1and1.co.uk
* **mail.from** Overrides mail.default.from
* **mail.port** Default `587`
* **mail.replyTo** The reply-to address
* **mail.ssl.enabled** true/false Default `false`. Whether SSL email is enabled or not 

#### Optional email properties

* **mail.maxEmailsPerSecond** The max number of emails that can be sent per second, default is 5.
* **mail.addressChunkSize** max number of addresses to include as recipients in a single email, default is 25.
* **mail.ssl.enabled** Whether SSL encryption of emails is needed. Default=`false`

### User signup

These properties configure both the sign-up flow and some UI elements. Not all combinations are valid.

* **user.signup** true/false Whether users can sign themselves up for accounts (default is `false`).
* **picreateGroupOnSignup.enabled** true/false If users can sign up, then this enables them to sign up as a PI
* **username.length.min** * Minimum username length  for standalone RSpace (not for SSO or LDAP), default 6. Min = 2, Max = 50.
* **user.signup.signupCode** If your RSpace is on public internet, and you want to enable users to sign themselves up, but don't want other people or bots to signup, you can require your users to enter a code when they sign up. 
   The code is compared case-sensitively with the value of this property.

  This mechanism is activated if the property has  a non-blank value. E.g. `user.signup.signupCode=mydomain29876`
* **user.signup.acceptedDomains**  restricts self sign-up to specific domains, for users in SSO environments using a federated IdP. A comma-separated list.
* **authorised.signup** If self-signup is allowed, do these requests need to be authorised by an admin? Default is `false`
* **license.exceeded.custom.message** can be used to set custom contact details if the license number is exceeded. The message will be appended to the error message that is displayed.  This should be useful if users self-sign up, so they can be directed to a contact person to ask about getting additional licenses.
* **example.import.files**. Custom content can now be provided when a user first logs into RSpace. An RSpace XML zip file can be put on the server,
  in a location specified by this property. The imported content will be put in each user's 'Examples' folder.
  e.g.

    example.import.files=file:/etc/rspace/ExampleImport-RSPAC-1789.zip


You can provide multiple files using a comma-separated list. Remember to use an absolute path, and to prefix the path with 'file:'

### External application connections

The following optional properties enable RSpace to connect to OneDrive (if OneDrive integration is enabled)::
* **onedrive.client.id**  application id obtained from OneDrive developer site. Default is unset.
* **onedrive.redirect** Callback URL that is configured on OneDrive developer site for this RSpace. Default is unset.

The following optional properties enable RSpace to connect to Enterprise Box API (if this integration is enabled):
* **box.client.id** Client id of Box App registered for given RSpace instance
* **box.client.secret** Client secret of Box App registered for given RSpace instance

Properties related to ownCloud configuration:

If your organisation uses ownCloud, the following  properties are required to enable RSpace to connect to it:
* **owncloud.url** The base URL of the ownCloud, e.g. `owncloud.url=https://owncloud-test.researchspace.com`
* **owncloud.server.name=ownCloud** A display label, e.g. `owncloud.server.name=ownCloud`
* **owncloud.auth.type** Must be 'oauth' e.g `owncloud.auth.type=oauth`
* **owncloud.client.id** The client ID obtained through registering RSpace as an integration
* **owncloud.secret** The  secret obtained through registering RSpace as an integration

* **nextcloud.url** The base URL of the Nextcloud, e.g. `nextcloud.url=https://owncloud-test.researchspace.com`
* **nextcloud.server.name=Nextcloud** A display label, e.g. `nextcloud.server.name=ownCloud`
* **nextcloud.auth.type** Must be 'oauth' e.g `nextcloud.auth.type=oauth`
* **nextcloud.client.id** The client ID obtained through registering RSpace as an integration
* **nextcloud.secret** The  secret obtained through registering RSpace as an integration

The following optional properties enable RSpace to connect to Orcid  API (if this integration is enabled):
* **orcid.client.id** Client id of Orcid App registered for given RSpace instance
* **orcid.client.secret** Client secret of Orcid App registered for given RSpace instance

The following optional properties enable RSpace to connect to Github API (if this integration is enabled):
* **github.client.id** Client id of Github registered for given RSpace instance
* **github.secret** Client secret of Github registered for given RSpace instance

The following optional properties enable RSpace to connect to Figshare API (if this integration is enabled):
* **figshare.id** Client id of Figshare App registered for given RSpace instance
* **figshare.secret** Client secret of Figshare App registered for given RSpace instance
* **figshare.categories.path** An optional file path of a static json file representing custom figshare categories selectable when making an export to figshare
* **figshare.licenses.path** The optional file path of a static json file representing custom figshare licenses selectable when making an export to figshare

The following optional properties enable RSpace to connect to Slack (if this integration is enabled):
* **slack.client.id** Client id of Slack App registered for given RSpace instance
* **slack.secret** Client secret of Slack App registered for given RSpace instance
* **slack.verification.token** Verification token.

The following optional property enables RSpace to connect to your PyRAT database instance (if this integration is enabled):
* **pyrat.server.config** configures the pyrat server alias associated to *server url* and server *access token* (API-Client-Token provided by Scionics - developers of PyRAT).
  For example:
  ```
  pyrat.server.config={ \
      "mice server": {"url": "https://mice.pyrat.cloud/mypyrat/api/v3/", "token": "x-xxxxxxxx"}, \
      "frogs server": {"url": "https://frogs.pyrat.cloud/mypyrat/api/v3/", "token": "x-xxxxxxxx"} \
  }
  ```
4
The following optional properties enable RSpace to connect to Clustermarket :
* **clustermarket.api.url** URL of the exposed Clustermarket API. For example
  `https://api.staging.clustermarket.com/v1/`.
* **clustermarket.web.url** URL of the Clustermarket website (eg staging/or real) . For example
  `https://staging.clustermarket.com/` (for staging) and `https://app.clustermarket.com/` (for the real website).
* **clustermarket.client.id** - Client id of Clustermarket App registered for given RSpace instance
* **clustermarket.secret** - Client secret of Clustermarket App registered for given RSpace instance

The following properties enable RSpace access to JoVE content you have a subscription to:
* **jove.api.access.enabled** set this to true in order to embed the full article/video you subscribe to via the JoVE integration, default is false. Please see our [documentation](https://researchspace.helpdocs.io/article/mopbqzzdf5-setting-up-jove-integration) for more details.

The new DMP integration, if enabled, requires a client ID and secret for OAuth connection.
* **dmptool.client.id**
* **dmptool.client.secret**
* **dmptool.base.url** The URL of DMPTool. This prototype integration defaults to `https://dmptool-stg.cdlib.org`

The GoogleDrive integration, if enabled, requires a client ID and developer key
* **googledrive.developer.key** The developer key. Defaults to RSpace developer key
* **googledrive.client.id** The developer client id. Defaults to RSpace client id

Properties for configuring MSOffice Online integration:
* **msoffice.wopi.enabled** true/false. Default is `false`
* **msoffice.wopi.redirect.server.url** This must be a researchspace.com proxy server. Ask ResearchSpace for details
* **msoffice.wopi.discovery.url** Default is `https://onenote.officeapps.live.com/hosting/discovery`. This must not be changed.
* **msoffice.wopi.proofKey.validation.enabled** Must be `true` in production usage. Default is `true`.

Properties for configuring Collabora Online integration:
* **collabora.wopi.enabled** true/false. Default is `false`
* **collabora.wopi.discovery.url** The discovery url for your Collabora instance, this has to be set and accessible for RSpace to be able to connect. Default is empty.

Properties for configuring Research Organisation Registry (ROR) integration:
* **ror.enabled** true/false. Whether ROR Registry panel should be visible on System -> Configuration page. Default is `false`.
* **ror.api.url** Default is `https://api.ror.org/v2/organizations`.

#### Google's reCAPTCHA on 'Sign up' page

If you're worried about spam accounts being created on your RSpace instance you can enable captcha field on RSpace 'Sign up' page. We are using [Google's reCAPTCHA](https://www.google.com/recaptcha/intro/index.html) technology and you need a Google account to register the  URL of your instance and to obtain API credentials for captcha.

To enable reCAPTCHA on 'Sign up' page:
1. Go to [https://www.google.com/recaptcha/admin](https://www.google.com/recaptcha/admin). Register a new site for your domain (i.e. myresearchspace.com). Note down the values of 'Site key' and 'Secret key'.
2. Add/update following RSpace deployment properties:
* user.signup.captcha.enabled=true
* user.signup.captcha.site.key=<your Site key>
* user.signup.captcha.secret=<your Secret key>
3. Restart RSpace instance, go to 'Sign up' page. The captcha field should appear, and should be required for signup.

### LDAP connections
These optional settings will enable you to import user data from LDAP, or enable LDAP authentication:

* **ldap.enabled** enabling user creation with help of LDAP, true/false
* **ldap.authentication.enabled** enabling user authentication through LDAP, true/false
* **ldap.authentication.sidVerification.enabled** enabling user SID verification through LDAP, true/false
* **ldap.url** ldap server URL, only needed if ldap.enabled is true. E.g. ldaps://kudu.rspace.com
* **ldap.baseSuffix** ldap server url, only needed if ldap.enabled is true. E.g. 'dc=test,dc=kudu,dc=axiope,dc=com'
* **ldap.ignorePartialResultException** if set to 'true' suppresses PartialResultException on search queries
* **ldap.fallbackDnCalculationEnabled** if set to 'true' RSpace will run 'sh -c ldapsearch' command to retrieve user's dn

* **ldap.bindQuery.dn** user to use for non-anonymous LDAP bind
* **ldap.bindQuery.password** password to use for non-anonymous LDAP bind
* **ldap.anonymousBind** whether anonymous bind should be used

* **ldap.userSearchQuery.uidField** name of LDAP attribute that is supposed to match the searched username ('uid' by default)
* **ldap.userSearchQuery.dnField** name of LDAP attribute that is holding full DN of the user, to be used during authentication
* **ldap.userSearchQuery.objectSidField** name of LDAP attribute that is holding binary SID value, to be used during SID verification

### SSO configuration
Set these properties to configure RSpace to run in SSO mode e.g. for Shibboleth integration. There may be further integration work needed with Apache headers/redirects etc. to get this working.
* **deployment.standalone** true /false. Set to false to enable SSO integration. Default is true.
* **deployment.sso.type** if single sign-on is configured (if deployment.standalone=false), this property must switch authentication filter to 'SAML' or 'openid'.
* **deployment.sso.logout.url** the URL to redirect to after logout from RSpace. Default is 'You're logged out' page.
* **deployment.sso.idp.logout.url** the URL presented to the user on 'You're logged out' page, which should point to a link that ends the global SSO session with IDP. No default. 
* **user.signup.acceptedDomains** restricts self sign-up for users in SSO environments. Only users with a username ending with the accepted domain will be allowed to sign up, and other users will be redirected to an information page. There is no default. E.g., @uni.ac.uk.
* **deployment.sso.signup.username.suffixToReplace** optional, SSO username suffix that should be replaced by suffix defined in **.suffixReplacement** deployment property, when signing up a new RSpace user; the original SSO username will be saved as username alias 
* **deployment.sso.signup.username.suffixReplacement** optional, a replacement suffix that should be used when signing up a new RSpace user with SSO username matching suffix defined in **.suffixToReplace** deployment property     
* **deployment.sso.ssoInfoVariant** Sets a custom "RSpace doesn't know you " page when self-signup is disabled. Default is unset. Requires custom page for RSpace.
* **deployment.sso.adminEmail** Sets the support email address for matters relating to accounts managed by SSO.

#### SSO configuration - backdoor admin login functionality
The following two properties can be enabled to allow creation and use of special System Admin backdoor account(s), i.e. accounts that work independently from SSO identity.
* **deployment.sso.adminLogin.enabled** true/false. Enables SSO backdoor login workflow (`/adminLogin` page) available for users with SignupSource=SSO_BACKDOOR. Default is false.
* **deployment.sso.backdoorUserCreation.enabled** true/false. Shows the option to create SSO backdoor accounts (with SignupSource=SSO_BACKDOOR) on 'Create System Admin' system page. Default is false.

If you never want to use the backdoor admin login feature, then keep these both `false` (this is the default anyway).
If you want to be able to create new backdoor login users, but not let them login yet, set *only* `deployment.sso.internalUserCreation.enabled=true`. This could be useful if you want to set up these accounts during initial deployment, but perhapsSSO is not yet activated
If you want to backdoor admin users to be able to login, but you don't them to be able to create more backdoor accounts, set *only* `deployment.sso.adminLogin.enabled=true`. This can be useful once the initial account creation is performed, to prevent backdoor admins creating more backdoor admin accounts.
If you want all backdoor login functionality, set both properties to `true`.

### External file system configuration

RSpace can link to external file systems via Samba or SFTP protocols. To enable this, set these properties:

* **netfilestores.enabled** - true/false Enables UI and functionality to support file systems.
* **netfilestores.auth.pubKey.passphrase** - If authenticating to a file system by public/private keys, this is the the passphrase securing it. Otherwise this property need not be set.
* **netfilestores.extraSystemProps** - takes a comma-separated list defining additional System Properties that should be set before initialising nfs clients
* **netfilestores.export.enabled**. Activates the option to enable filesystem-linked files to be included in the export

  If set to `true`, a checkbox will appear in the export dialog to optionally include linked files.  Default is `false`.

* **netfilestores.login.directory.option** Enables SFTP filesystem users to specify the remote directory they log into. For example, connect to `<USER>`, instead of root dir.
The property exposes an option to allow SFTP filesystem users to have target directories in the configuration page for Institutional File Systems. Leave this property as false if SFTP filesystem users do not need to specify the remote directory they log into. 

The following properties are specific to SMB filesystems:

* **netfilestores.smbj.download** Whether jcifs client should delegate download actions to smbj. Default is `false`
* **netfilestores.smbj.shareName**The name of the SMB share. This is no longer required as it is defined in the user interface. 
   It is kept for legacy systems

* **netfilestores.smbj.withDfsEnabled** decides if SMBClient option `withDfsEnabled` should be used on initialization. Default is `false`
* **netfilestores.smbj.name.match.path**  when true this will cause samba names to be superseded by file path names for the SMBv2/3 connector. It should normally be left as false.

### Application behaviour
These optional settings configure  behaviour of the RSpace application.

#### File upload and processing
* **files.maxUploadSize** The maximum individual file size upload, in bytes. Default is 10Mb
* **max.tiff.conversionSize** The max file size in bytes at which conversion of TIFF images to .png working images will be attempted. The default is 8192000 (8Mb)
* **rs.attachment.lucene.index.dir** The directory where Lucene indexes are stored. Must be writable by Tomcat.  Default is `$TOMCAT_HOME/LuceneFTsearchIndices`
* **rs.hibernate.searchIndex.folder** The directory where database full-text indexes are stored. Must be writable by Tomcat.  Default is `$TOMCAT_HOME/FTsearchIndices`
* **rs.filestore** Whether you are using RSpace's local file-store or Egnyte. Default is 'LOCAL'. Don't change this.
* **rs.indexOnstartup**  true/false. Default is `true`. Controls whether text-data is re-indexed or not at application start-up. Setting to false will result in faster startup times.

#### UI customization
* **ui.bannerImage.path** A URI to a png, gif or jpg that can be used to replace the RSpace logo. E.g. file:/etc/rspace/mylogo.png. Default is standard RSpace logo.
* **ui.bannerImage.url**. A URL to navigate to after clicking on banner image, when user is logged in. Default is the Workspace, making the banner image act as a "Home" button.
* **ui.bannerImage.loggedOutUrl**. A URL to navigate to after clicking on banner image, when user is not logged in. Default is https://www.researchspace.com/
* **ui.footer.urls** A JSON Object that represents a map of links and urls to display in the footer of every page, e.g. `{'linkName1':'http://www.url.com/example1','linkName2':'http://www.url.com/example2'}`
* **profile.email.editable** true/false. If true, user can edit their email address in their profile page. If false, they cannot. Default is true.
* **profile.firstlastname.editable** true/false. If true, user can edit their display name in their profile page. If false, they cannot. Default is true.
* **profile.hiding.enabled** All user profile information is hidden to other users.
* **login.customLoginContent** A line of text to appear on login page. Should be 1 or 2 sentences maximum. (not available for SSO installations). Default is empty string
* **signup.customSignupContent** An optional short message can be put on the RSpace signup page. Default is unset.

#### Archiving and export
* **archive.folder.location** (default = $TOMCAT_HOME/archive) Path to a directory where exports will be assembled and stored. Must be readable and writable by Tomcat.
* **archive.folder.storagetime** Time, in hours, that an exported archive will be available for download before it is considered for physical deletion from the server. Default is 24.
* **archive.minSpaceRequiredToStartMB** Minimum available disk space on temp folder partition required for RSpace to start/continue archive export process. In megabytes, default is 1000 (1 GB).
* **archive.maxExpandedSizeMB** Maximum size of constructed archive export. In megabytes, default is 10000 (10 GB).
* **importArchiveFromServer.enabled**. true/false.  If true, enhances import of RSpace XML archives by being able to specify a file path on the server. This enables archives stored on the server to be re-imported without tedious download and re-upload through web interface. Default is false.
* **pdf.defaultPageSize** Default PDF export page size . Valid values are UNKNOWN,A4,LETTER. Default is A4.

### AWS S3 configuration

RSpace can send exports to AWS S3 for long-term storage.
In order for any of this to work, an environment variables must be set in Tomcat configuration (e.g. in `/etc/default/tomcat9`)
indicating where is the AWS credentials file, and the credentials must enable creating and adding to S3 buckets. E.g.

    AWS_SHARED_CREDENTIALS_FILE=/path/to/.aws/credentials

* **aws.s3.hasS3Access** - true/false value indicating whether your deployment has access to an S3 bucket.
* **aws.s3.bucketName** - the name of the S3 bucket RSpace will export archives to.
* **aws.s3.archivePath** - the S3 bucket prefix where RSpace will export archives to.
* **aws.s3.region** - the S3 region where RSpace will export archives to.
* **aws.s3.removalPolicy** - the number of hours RSpace will wait to delete archives from S3.

#### AWS internal config

These shouldn't need changing in production

* **aws.s3.chunk.threshold.mb** Threshold at which to start using chunked upload. Default is 1Gb
* **aws.s3.chunk.size.mb** Size of individual chunks in a chunked upload. Default is 500, must be > 5

#### Admin
* **sysadmin.delete.user** true/false If true, Sysadmin user can permanently and irreversibly delete user and all their work from the database. Useful for removing wrongly created user accounts. *Use with caution*. Default is false
* **sysadmin.delete.user.resourceListings.folder** Location for storing files listing filestore resources of deleted user. Default is archive/deletedUserResourceListings
* **sysadmin.delete.user.deleteResourcesImmediately** Whether successful user deletion from DB should be immediately followed by filestore resources deletion. Default is `true`
* **sysadmin.limitedIpAddresses.enabled** true/false. If true, will only allow sysadmin login from a whitelist of IP addresses. Default is false.
  You could also consider a Captcha or similar mechanism.
* **sysadmin.nodeletenewerthan.days** Default=366. Only applies if `sysadmin.delete.user=true` and if the API is being used to delete users.
  Prevents user accounts created in last N days being deleted.

## Viewing and reporting error logs
In system panel, it is possible to view latest error log and send to ResearchSpace
* **sysadmin.errorfile.path** The path to the error log file. This file will be in the directory specified by `logging.dir` property.
* **sysadmin.rspace.support.email** Email address to send error logfiles to. Default is `operations@researchspace.com`
* **slow.request.time** A duration, in milliseconds, default = 2000. Requests taking longer than this duration will be logged to SlowRequests.txt
# 
* **csrf.filters.enabled** true/false, default=`true`. Forces correct Origin and Referer headers to protect against CSRF.
* **csrf.filters.acceptedDomains** If `csrf.filters.enabled=true`, this specifies additional URLs that will be accepted by RSpace's CSRF filter, e.g. https://myrspace-alias.myserver.com

### Inventory behaviour
These optional settings tweak behaviour of RSpace Inventory part.

* **inventory.import.containersLimit** The maximum number of containers that a single import operation can create. Default is 500.
* **inventory.import.samplesLimit** The maximum number of samples that a single import operation can create. Default is 1000.
* **inventory.import.subSamplesLimit** The maximum number of subSamples that a single import operation can create. Default is 2000.
 
### Other settings

Depending on your installation, you might be advised by ResearchSpace to set the following properties.
All of these options have sensible defaults.

* **velocity.ext.dir** the path to a folder where customized Velocity templates are kept. This is only needed if you have customer-specific, bespoke email messages
* **rs.postbatchsignup.emailtemplate** The path of a customised Velocity template to be used for sending to users following batch upload. The template file should be in the folder indicated by **velocity.ext.dir**
* **rs.postsignon.emailtoadmin.template** Only applicable if  `authorised.signup` is true. Path to a Velocity template of email that will be sent to admins on a user signing up.
* **rs.postsignon.genericAccountAuthorisation** Only applicable if  `authorised.signup` is true. Path to a custom Velocity template of an email sent to users, if their signup request is approved.
* **email.signup.authoriser.emails** Only applicable if  `authorised.signup` is true. A comma separated list of email addresses
* **liquibase.context** If your installation needs to have  specific data pre-loaded into the database, you may need to set this property.
* **licenseserver.poll.cron** Crontab value defining how often to poll license server for updates. Default is every 30 minutes.

### Using an external file store

RSpace can now use a 3rd party file storage service as its backing file store. Initially we are supporting Egnyte file store, which requires the following properties to be set:

**rs.filestore=EGNYTE** Tells RSpace to expect to use Egnyte as file store
**rs.ext.filestore.baseURL** The URL of your Egnyte instance, e.g. `rs.ext.filestore.baseURL=https://apprspace.egnyte.com`
**rs.ext.filestore.root** The top-level folder under which RSpace will save files, e.g. `rs.ext.filestore.root=/Shared/RSpaceFileStore`
**egnyte.internal.app.client.id** The client ID obtained after registering RSpace as an integration on Egnyte

This feature is currently in beta, please ask if you are interested in using this feature.

### Cache configuration
This is an advanced topic and on initial set up will not require configuration. Regular monitoring
can detect if and when the caches become full.
These  caches can become full when using the default settings. You can see the cache usage in the
System->Monitoring tab. If either of these caches is at, or near, 100% capacity it is a good idea to resize.
Try doubling the cache size until the cache is no longer full. Resetting these properties requires a restart to take effect. Population of caches depends on usage and may take some time (hours or days) before the  caches are being used fully.
The values are the number of items to store in the cache.

* **cache.com.researchspace.model.FileProperty** (default 2500)
* **cache.com.researchspace.model.ImageBlob** (default 1000)
* **cache.com.researchspace.model.User.roles** (default 1000)
* **cache.com.researchspace.model.field.FieldForm** (default 1000)
* **cache.com.researchspace.model.record.RSForm** (default 1000)
* **cache.com.researchspace.model.record.BaseRecord** (default 100, individual objects in this cache can take a lot of space, be careful when extending it)

### API throttling

Settings to rate-limit the number of API calls made to the server. 
* **api.throttling.enabled** Whether any throttling is applied. Default is `false` 
* **api.global.limit.day** Max API calls per day, default=100000
* **api.global.limit.hour** Max API calls per hour, default=10000
* **api.global.limit.15s** Max API calls per 15 seconds, default=75

* **api.global.minInterval**. Minimum interval between requests, in millis. Default = 1. Set to 0 for no throttling.

* **api.user.limit.day** Max API calls per user per day, default=5000
* **api.user.limit.hour** Max API calls per user per hour, default=1000
* **api.user.limit.15s** Max API calls per user per 15 seconds, default=15
* **api.user.minInterval** Minimum interval between requests, in millis. Default = 0, which means no minimal interval.

* **api.beta.enabled** Whether beta API is enabled or not. Default=true

### Monitoring

RSpace contains an embedded monitoring capability to monitor CPU usage, memory, server load etc at `/monitoring` . The login username is
`rs_monitoring`.

### OAuth 'password' grant flow.

Please see our [Github documentation](https://github.com/rspace-os/api-tutorial/blob/master/oauth.md) for instructions.

### Configuring updates (AWS /SaaS deployments only)

This documentation is only relevant to ResearchSpace staff performing updates.

In top level folder:

    git clone https://github.com/ResearchSpace-ELN/rspace-update.git

to install the update script, see the readme file for details

### Configuring backups
If ResearchSpace is responsible for backups:

* Create a folder `scripts` in top level folder (usually /home/builder/)
* `cd scripts` and clone Backup code:  `git clone https://github.com/ResearchSpace-ELN/rspace-update.git`
* `cd rspace-update` and follow the instructions in `readme.md`  to set up a backup procedure to AWS S3

If ResearchSpace  is *not* responsible for backups this section can be ignored.

Starting the application
------------------------
Starting the application is simple but the command will vary slightly from OS to OS


In Centos we use

    sudo /etc/init.d/tomcat start

Ubuntu / Debian

    sudo systemctl start tomcat.service

Testing the installation
------------------------

Login
--------

There is a hard-coded admin account set up for you, the first time RSpace is run:

Username	 Password  	Role

sysadmin1	sysWisc23!	system

Depending upon your configuration, new users can create a new account, in a default user role.
The emails and the name associated with this account can be set in the Profile page
for these accounts (in MyRspace tab)

You *must* change this password immediately for servers that are open to the world (the password is in documentation, and widely known).

System users can view usage activity of the application and
batch-upload new users via the 'System' tab.

### Browser requirements:

A modern browser that supports HTML5 is essential; we support recent (< 1 year old) versions of Chrome, Firefox and Safari

We do not recommend using Edge or Explorer browsers or any Microsoft browser..


Troubleshooting
---------------

Should you have any trouble installing the application you can contact support by email at  support@researchspace.com

If you have followed the steps details above but are still receiving errors in Tomcat during startup please send the last stack trace to the support email address listed above.

E.g.

    tail -2000 $RSpaceLogFile/error.log > logs.txt

replacing '$RSpaceLogFile' with the actual location of the logfiles,  and attach 'logs.txt' to your email.

### Cleaning the search index

Occasionally, after restarting, there may be a lock on the search index file, causing an error like this:

ERROR | Exception occurred org.apache.lucene.store.LockObtainFailedException: Lock obtain timed out: SimpleFSLock@FTsearchIndices/com.researchspace.model.record.Folder/lucene-

In this case:

* shutdown RSpace
* remove the folder FTSearchIndices via `rm -rf FTSearchIndices`
* restart RSpace

Logging
--------

### Overview
There are 6 logfiles generated by  RSpace:

* error.log - general logging and errors, that used to go to catalina.out
* SecurityEvents.txt - login/authentication/authorisation events
* RSLogs.txt - audit trail logs
* httpRequests.log - a complete record of URLs and the user/timestamp
* emailErrors.txt - dedicated log for failed emails sent by RSpace.
* SlowRequests.txt - dedicated log for requests that take > 5s to complete on the server.

We log all access to services using log4j. All logs (prior to 1.30) used to be logged to catalina.out as well. By default, these log files are written to Tomcat's home folder.

### Setting the folder location of the log files
If you wish, you can set a deployment property, `logging.dir`, to set a folder where these  log files will be located. E.g.

    logging.dir=rspacelogs 

will create a subfolder in Tomcat folder called 'rspacelogs' and all logfiles will be written there.
Even better, you can externalise the log folder to be wherever you like - just as long as it's writable by Tomcat. E.g.

    logging.dir=/absolute/path/to/logfolder

#### Migrating logs from existing log location
If you are updating an existing RSpace installation, and want to set `logging.dir`, there are two things you must do.
Firstly, move all your audit trail logs to the new folder location (this is so that audit trail search will still work).

E.g. `cd` to Tomcat home folder - there will be some files with names starting with `RSLogs.txt`

    mv RSLogs*  /absolute/path/to/logfolder/ 

will transfer the files to the new location ( this *must* be the folder  you set as value of `logging.dir`.

Other logs can be moved across as well, or archived, or deleted, as you see fit. Only RSLog files are consumed by RSpace itself.

Secondly, update the value of the property `sysadmin.errorfile.path` to point to the new error log location. E.g. change

    sysadmin.errorfile.path=/path/to/logs/error.log

to

    sysadmin.errorfile.path=/absolute/path/to/logfolder/error.log

### Setting the log level
The current log level is WARN; this generates  logging on application performance. You can set this to INFO to get more verbose logging.

End of file
