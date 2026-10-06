## Notes for App creations

### Naming conventions

There are various transformations applied to names and assumptions made about the names of system properties 
and App names.

* App name (referred to as <app_name> below) should be all lower case and be alphanumeric, e.g.  `pubmed` 
* The property name used to determine availability should be <app_name>.available, E.g. `pubmed.available`
* The `name` field of the `App` table must be app.<app_name>, e.g. `app.pubmed`
* The `label` field of the `App` table should be a human readable standard name for the app, e.g. `Pubmed`.
      It can contain spaces and other non-alpha characters.

#### Conventions for `Communication` apps posting to webhooks

* Property name for a webhook option is `<CAPITALIZED_APP_NAME>_WEBHOOK_URL`, e.g. `MSTEAMS_WEBHOOK_URL` for Microsoft Teams.
  Teams and Slack webhook URLs are credentials kept in the encrypted `UserConnection`, not deployment properties.
* Property name for a channel name is `<CAPITALIZED_APP_NAME>_CHANNEL_LABEL` e.g. for Slack, is `SLACK_CHANNEL_LABEL`

User-specific credentials must be stored in the encrypted `UserConnection` table rather than as plaintext
`AppConfigElement` values. Dataverse API keys and Teams webhook URLs are associated with the user's config-set ID;
use that ID as the connection discriminant so each configuration keeps its own credential. See
[Creating a new integration](/DevDocs/DeveloperNotes/CreatingNewIntegration.md) for the storage pattern.

These conventions enable a generic UI to choose where to post
