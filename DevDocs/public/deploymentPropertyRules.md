Deployment property rules
-------------------------

This document lists rules for valid combinations of properties.
Most properties don't interact with each other, but some do, and the value of one property
may require other properties to be set in order for the feature to work properly.

#### General Rules
1. If `deployment.cloud` is 'true', then `deployment.standalone` must be 'true'
2. If `ldap.enabled` is 'true', then ldap.url and `ldap.baseSuffix` must be set
3. If `authorised.signup` is 'true', then `user.signup` must be true, and `email.signup.authoriser.emails` must be set.
4. If `deployment.standalone` is 'false' then `deployment.sso.logout.url` must be set.

#### Integrations
The availability of these integrations is now configurable within RSpace itself ( see Changelog-1.33-to-1.34.md)

1. If OneDrive is available, then `onedrive.client.id` and `onedrive.redirect` must be set.
2. If Enterprise Box  is available, then `box.client.id` and `box.client.secret` must be set. (Note this specific for commercial users of Box, and  is not necessary for users to access Box on the cloud). 
  
#### Document previews
If 'aspose.app' is set, then:

1. `aspose.license` must be set
2. `aspose.logfile` must be set.
  
#### Analytics

1. If `analytics.enabled` is 'true' then `analytics.server.key` must be set.
2. If `livechat.enabled` is 'true' then `livechat.server.key` must be set.

####  File system properties

`netfilestores.export.enabled`, `netfilestores.extraSystemProps` and `netfilestores.auth.pubKey.passphrase` all require `netfilestores.enabled=true` in order to be active.
