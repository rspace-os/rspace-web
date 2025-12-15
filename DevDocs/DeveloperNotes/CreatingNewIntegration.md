# Creating a new integration

This document describes the steps required to add a new integration (App) to RSpace.

## Backend changes

The backend changes required for adding a new integration involve:

1. Adding deployment properties (e.g., URLs) for the integration, if required
2. Adding a system property to enable/disable the integration at the deployment level
3. Adding a user preference to enable/disable the integration for each user
4. Handling authentication for the integration

#### 1) Deployment properties (where applicable)

Some integrations require configuration that varies by deployment, such as API endpoints for the customer instance of the integration. These are defined in `PropertyHolder.java`.

Add similar properties for your new integration as required. These properties are then set in deployment property files:

```properties
newplugin.api.url=https://api.newplugin.com
newplugin.api.key=your-api-key-here
```

Always add the property to the `defaultDeployment.properties` file at a minimum. See the [Property files docs](/DevDocs/DeveloperNotes/PropertyFiles.md) for more info.

#### 2) Sysadmin toggle

System administrators need the ability to enable or disable the integration at the deployment level. This is managed through system properties stored in the database.

The system property name follows the convention `{integrationName}.available`, where `{integrationName}` is the uppercase constant defined in `IntegrationsHandler`.

Steps to add:

1. Define the constant in `IntegrationsHandler.java` following the convention.
2. Add a database migration (Liquibase changeset) to create the system property entry. Use an existing SystemProperty changeset as a template. Add a new entry named `new_plugin.available` of type BOOLEAN with default `false` in the appropriate changelog under `src/main/resources/sqlUpdates`.

#### 3) User toggle

Once an integration is available (sysadmin toggle is on), individual users can enable or disable it via the Apps page for their own use.

Some integrations require additional configuration (for example, user API keys or tokens).

• App only needs to be enabled/disabled

Some integrations are simply toggled on or off per user. These integrations use the `Preference` enum, which should be updated in the `rspace-core-model` project. Add the new `Preference` to the `booleanIntegrationPrefs` `EnumSet` in `IntegrationsHandlerImpl`.

• App requires individual configuration

Most integrations use `UserAppConfig` to store information specific to the user for that integration. If that is the case, add your integration to the list in `IntegrationsHandlerImpl.isAppConfigIntegration()`.

For integrations with configuration options that apply to all users, add an entry to `isSingleOptionSetAppConfigIntegration()`.

#### 4) Authentication

• OAuth flow

For integrations using OAuth 2.0, implement both of the following:

1. Add OAuth handling in the `postProcessInfo()` method in `IntegrationsHandlerImpl`.
2. Create an OAuth controller (see `FigshareOAuthController`, `DMPToolOAuthController` for examples) that:
   - Redirects users to the third-party OAuth authorization page
   - Handles the OAuth callback
   - Stores the access token using `UserConnectionManager`

• Single-user token/API key

For integrations that use a simple API key or token per user:

1. Define a token constant in `IntegrationsHandler`.
2. Add token handling in `postProcessInfo()` in `IntegrationsHandlerImpl`.
3. Store tokens using `UserConnectionManager`, which saves them to the `UserConnection` table with:
   - `userId`: The user's username
   - `providerId`: Your integration's app name
   - `accessToken`: The actual token/API key

## Frontend changes

For UI guidance, see:

1. [Apps](/src/main/webapp/ui/src/eln/apps/README.md)
2. [Adding a New Integration](/src/main/webapp/ui/src/eln/apps/AddingANewIntegration.md)