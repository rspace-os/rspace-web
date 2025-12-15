# Property Files

- last updated Feb 2022 RA

Properties are loaded at runtime by Spring's Property Placeholder
mechanism, defined in `applicationContext-resources.xml` and injected by
@Value annotations. The 'order' attribute of the property declarations
defines which property files are read first, in ascending order. 
The first occurrence of a property 'wins' and is not overwritten by subsequent occurrences of that
property in later files. However, within a property file, a property occurring more than once will take the last
value. 

A `defaultDeployment.properties` file, always on the classpath provides default values for all properties.
Properties can be overridden and customised in a second file, which can also be on the classpath, or external.

-- Note if spring el is used to give default values to properties using @Value annotations, those properties
must not be defined in the defaultDeployment.properties file - they will be overriden by the default value
set using Spring EL. For example, using `@Value(“${pyrat.server.config:#{null}}“)` will always override
any value set for `pyrat.server.config` with null if pyrat.server.config` is defined in defaultDeployment.properties.
For local rSpace use dev/deployment.properties. Properties defined here will override any value such as null in the above example.
If you do not use a default value in spring-el, then you may define the property value in defaultDeployment.properties.

Some properties are used in JSPs - for example, some UI features may
only be accessible for particular deployments. These properties are
loaded up by StartUpListener class and made available in JSPs through
the application scope variable 'RS_DEPLOY_PROPS' which references the
'PropertyHolder' class.

E.g., `<c:if test="${applicationScope['RS_DEPLOY_PROPS']['userSignup']}">`

Alternatively, you can use the RST tag to access a property defined in 'PropertyHolder' class in a JSP:

`<rst:hasDeploymentProperty name="loginDirectoryOption" value="true">`

## How property file location is resolved.
Property files can be loaded from the classpath, or from external files.

### Development
To provide a uniform developer experience, property files  are located on the classpath in various subfolders of 
`src/main/resources/deployments`

At build time, the placeholder `${propertyFileDirPlaceholder}/deployment.properties` in
`applicationContext-resources.xml` is resolved by Maven
using values defined in pom.xml. Typically, this will resolve ${propertyFileDirPlaceholder} to `classpath:deployments/dev`

### Production (and AWS FeatureBranch builds)
Property files are externalised to enable easy editing and customization on a per-server basis.

The external property file could be in any location, and may differ between servers. (Typically it is 
`/etc/rspace/deployment.properties`, but this just convention). This means the location must be defined at runtime.

In this case, the Maven build flag `-DpropertyFileDirPlaceholder=\$\{propertyFileDir\}` replaces `${propertyFileDirPlaceholder}/deployment.properties`
with the name of a runtime system property `${propertyFileDir}`. On the production server, `${propertyFileDir}` is
defined in Tomcat configuration file. E.g. `-DpropertyFileDir=/etc/rspace`

## Adding a new property

If this is a property that may need to be altered after deployment, or
may be variable between different deployment builds, and might need to
be edited after installation (i.e., post-build)
then add it to defaultDeployment.properties. Otherwise, add it to
another property file. E.g., rs.properties.

For each deployment/subfolder, override if need be, if it is clear what
value should be used by different deployments.

If the value must be set, then add in a commented out value to each
deployment property file indicating that this property must be set.

### Documenting the new property
Add the property and an explanation of its purpose to RSpaceConfiguration.md.
Also add it to the ChangeLog (e.g.`DevDocs/public/Changelog-v1-to-v2.md`) so that people
installing are aware of the new property.

### Usage in JSPs
If the property is to be used in a JSP, then inject the property into
PropertyHolder class and provide a getter method for the property.
PropertyHolder is a normal Spring bean that can be used in
Controllers/StartupListener.
