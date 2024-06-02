## Notes for App creations

### Naming conventions

There are various trnsformations applied to names nad assumptions made about the names of ssytem properties 
and App names.

* App name (referred to as <app_name> below) should be all lower case and be alphanumeric, e.g.  `pubmed` 
* The property name used to determine availability should be <app_name>.available, E.g. `pubmed.available`
* The `name` field of the `App` table must be app.<app_name>, e.g. `app.pubmed`
* The `label` field of the `App` table should be a human readable standard name for the app, e.g. `Pubmed`.
      It can contain spaces and other non-alpha characters.

#### Conventions for `Communication` apps posting to webhooks

* Property name for webhook is `<CAPITALIZED_APP_NAME>_WEBHOOK_URL` e.g. for Slack, is `SLACK_WEBHOOK_URL`
* Property name for a channel name is `<CAPITALIZED_APP_NAME>_CHANNEL_LABEL` e.g. for Slack, is `SLACK_CHANNEL_LABEL`

These conventions enable a generic UI to choose where to post 