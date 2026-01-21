# Creating a new integration

This document describes the steps required to add a new integration (App) to RSpace.

## Backend changes

The backend changes required for adding a new integration involve:

1. Adding a system property to enable/disable the integration at the deployment level
2. Adding a user-level toggle by creating an App row (and optional per-user config)
3. Adding deployment properties (e.g., URLs) for the integration, if required
4. Handling authentication for the integration

### Database migration
Adding a new integration requires various updates to the database, which are handled via Liquibase migrations. See [Database Change Guidelines](/src/main/resources/sqlUpdates/DatabaseChangeGuidelines.md) for more information on how to create and run Liquibase changesets.

In sections 1 and 2 below, we'll create a changeset and add the necessary data to the database to enable/disable the integration at the deployment and user levels and to store any specific configuration required by the integration.

### 1) Sysadmin toggle

System administrators need the ability to enable or disable the integration at the deployment level. This is managed through system properties stored in the database.

1. Define the constant for your integration in `IntegrationsHandler.java` (e.g., `NEW_INTEGRATION_APP_NAME`).
2. Create a changeset (see [this changeset](/src/main/resources/sqlUpdates/changeLog-rsdev-855.xml) for an example):
   1. Insert a `PropertyDescriptor` with `name` = `<integration>.available` and `defaultValue` = `DENIED`
   2. Insert a `SystemProperty` that references the above descriptor.
   3. Insert a `SystemPropertyValue` row for the initial value (usually `DENIED`).

### 2) User settings

Once an integration is available at the system level (sysadmin toggle is on), individual users can enable or disable it and (optionally) configure it.

[Example](/src/main/resources/sqlUpdates/changeLog-rsdev-850.xml)

1. Create a changeset following the example to insert a row into the `App` table:
   - `label` = human-readable name
   - `name` = `app.<integrationName>`
   - `defaultEnabled` = `false` defining initial user-level state
2. Determine if your integration requires per-user configuration options:
   a. If the App only needs to be enabled/disabled:
   No extra per-user options are required. The App row plus classification in code is enough (see classification below).
   b. If the App requires per-user configuration options:
   Define one or more `PropertyDescriptor` rows for your option keys and link them to your App via `AppConfigElementDescriptor` rows. Example: [adding an API key](/src/main/resources/sqlUpdates/changeLog-rsdev-369.xml).
3. Code changes:
   - Add your integration to `IntegrationsHandlerImpl.isAppConfigIntegration()` if it has more than one option set per user.
   - Otherwise, if it has only a single option set per user, add it to `isSingleOptionSetAppConfigIntegration()`.

### 3) Deployment properties (where applicable)

Some integrations require configuration that varies by deployment, such as API endpoints for the customer instance of the integration. These are defined in `PropertyHolder.java`.

Add similar properties for your new integration as required. These properties are then set in deployment property files:

```properties
newplugin.api.url=https://api.newplugin.com
newplugin.api.key=your-api-key-here
```

Always add the property to the `defaultDeployment.properties` file at a minimum. See the [Property files docs](/DevDocs/DeveloperNotes/PropertyFiles.md) for more info. Deployment-level options go in `PropertyHolder`/property files; user-level options go via `AppConfigElementDescriptor`.

### 4) Authentication

#### OAuth flow

For integrations using OAuth 2.0, implement both of the following:

1. Add OAuth handling in the `postProcessInfo()` method in `IntegrationsHandlerImpl`.
2. Create an OAuth controller (see `FigshareOAuthController`, `DMPToolOAuthController` for examples) that:
   - Redirects users to the third-party OAuth authorization page
   - Handles the OAuth callback
   - Stores the access token using `UserConnectionManager`

#### Single-user token/API key

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