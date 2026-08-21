# OMERO credentials live in UserConnection, not the session

Status: accepted

OMERO has no OAuth flow: `POST /apps/omero/connect` (RSDEV-779) takes an OMERO
username and password from a plain form submission. Those credentials were held
in a `@SessionScope` `Map<String, UserConnection>` bean, keyed
`"omero_" + username`, so a connection evaporated on logout or session expiry
and the Apps page could never report one. `IntegrationsHandlerImpl` had no
`OMERO` case either, so `/integration/integrationInfo` never set
`oauthConnected`, and the OMERO card rendered the credentials form
unconditionally with no way to disconnect.

We decided to store the credentials in the existing `UserConnection` table like
every other credential-based app. The OMERO username and password go into the
access token field as a `username_,_password` delimited string, which
`UserConnectionDaoHibernate` encrypts at rest and decrypts on read. `connect`
replaces the row through a single `UserConnectionManager.replaceConnection`
call, which deletes any existing row for the authenticated user and provider
`OMERO` before saving, in one transaction. The delete is what makes reconnecting
under a *different* OMERO username work at all: the username is the
`providerUserId` half of the composite primary key, so a plain save would insert
a second row, and the baseline's `UNIQUE KEY UserConnectionRank (userId,
providerId, rank)` plus a hard-coded `rank` of 1 would then reject it with a
constraint violation. Doing both in one transaction is deliberate: two
transactions would let a failed save leave the user with no connection at all. `postProcessInfo`
now reports the connection through `setSingleOAuthConnectionStatus`, which
publishes only `MASKED_TOKEN`; the stored username and password never reach the
browser, and the card offers a Disconnect button rather than re-showing them.

## Considered options

* **Keep the session map, and have the Apps page read it**: rejected. It fixes
  the display but not the reported bug, which is that the connection does not
  survive leaving RSpace.
* **A revocable OMERO session key instead of the password**: OMERO issues
  session UUIDs that expire and can be revoked server-side, which would be a
  smaller at-rest liability than a reusable account password. Rejected for now
  because the existing `connect` form, `OmeroClientImpl.loginJsonClient`, and
  the TinyMCE dialog all authenticate with username and password on every
  request, and RSDEV-779 is a regression fix. Worth revisiting as its own ticket.
* **A dedicated OMERO credentials table**: rejected. `UserConnection` is
  already generic over provider, already encrypts tokens, and already carries
  the `INTEGRATION_INFO` cache eviction that `getIntegration` depends on. A new
  table would need a Liquibase changeset and its own encryption for no gain.
* **Re-display the stored OMERO username in the form when connected**: rejected.
  No other app re-displays credentials, and the password must never be sent back.

## Consequences

* No Liquibase change: `UserConnection` already exists and the session map was
  transient, so there is nothing to migrate. Existing users must reconnect once,
  which they had to do every session anyway.
* `apps.omero.errors.authenticationExpired` became
  `apps.omero.errors.notConnected`: with durable storage a missing row means
  "never connected or disconnected", not "expired".
* The `userNameToUserConnection` session bean is deleted; the two OMERO
  controllers were its only users.
* `OmeroAuthController.newJsonClient()` exists purely as an overridable seam so
  `connect` can be unit-tested without a live OMERO server.
* Unlike a revocable OAuth token, the stored value is a reusable third-party
  account password. It is encrypted at rest, but recoverable from a database
  dump plus the single `apitoken.encryption.key` deployment property, and there
  is no key-rotation or re-encryption path. `connect` is annotated
  `@IgnoreInLoggingInterceptor(ignoreRequestParams = {"omeropassword"})` because
  `LoggingInterceptor` is mapped to `/**` and would otherwise write the password
  to the request log in cleartext.
* `providerUserId` is `varchar(50)`, so an OMERO username longer than 50
  characters (an email address, at some sites) fails the insert. The single
  transaction means such a failure rolls back and leaves the previous connection
  intact, and the user sees the login error; it is not silently truncated.
* Credentials are read from the database on each of the eight OMERO endpoints,
  where the session map was a bare lookup. That is one indexed select plus one
  decrypt per call, uncached; accepted as the cost of durability.
* `connect` validates that neither half is blank and that neither contains the
  `_,_` delimiter, because the storage format cannot round-trip either: a blank
  password stores `"user_,_"`, which splits back into one element and breaks
  every later OMERO request. `JSONClient.login` returns `null` rather than
  throwing when OMERO refuses the credentials, so `connect` checks the result
  before storing; otherwise a refused password would be saved and replayed
  against the user's real OMERO account on every subsequent call.
