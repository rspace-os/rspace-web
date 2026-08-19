# Payload CMS impersonation and REST API v2

Research date: 2026-08-13

## Conclusion

Payload CMS core does not document or expose a native impersonation operation. Its generated auth
API supplies login, logout, refresh, current-user, password, verification, and unlock operations.
Payload then identifies one current user on the request from an HTTP-only cookie, JWT, API key, or
custom strategy.

The community `payload-plugin-masquerade` adds impersonation by replacing the browser's ordinary
Payload authentication cookie with a normal JWT for the target user. Consequently, subsequent
Admin Panel, REST, and GraphQL requests that include the cookie are authorized as the target user.
An explicit JWT or Bearer Authorization header takes precedence over that cookie, so a client that
keeps sending the administrator's header remains the administrator. The original administrator is
not part of Payload's normal request principal when the target cookie is used.

Better Auth's admin plugin provides a stronger reference design. It creates a distinct, short-lived
session for the target and stores the original administrator in that session's `impersonatedBy`
field. This retains the actor and the effective user in one server-side authentication record.

RSpace REST API v2 uses a first-class delegated principal when the legacy Shiro session is in
`runAs`. The signed UI JWT identifies the effective user in `sub` and original administrator in
RFC 8693 `act.sub`. It also contains the UI audience and opaque session context in `sid`.
Authorization uses the subject. Delegated security events retain both actor and subject.

## Payload CMS core

Payload authentication binds one `req.user` to each request. The documented core auth operations
do not include start-impersonation or stop-impersonation endpoints. Payload supports HTTP-only
cookies, JWTs, and API keys, and a browser REST request can authenticate by including its Payload
cookie.

This means that Payload core supplies the primitives needed to implement impersonation, but not an
actor-plus-subject impersonation model. A custom implementation must decide how to mint or resolve
the target identity and how to preserve the original actor.

Sources:

- [Payload auth operations](https://payloadcms.com/docs/authentication/operations)
- [Payload core auth endpoint list at the reviewed revision](https://github.com/payloadcms/payload/blob/4f7f9f250231efb5ed1335cfb422a44a799634e4/packages/payload/src/auth/endpoints/index.ts#L24-L75)
- [Payload authentication overview](https://payloadcms.com/docs/authentication/overview)
- [Payload REST API and generated auth operations](https://payloadcms.com/docs/rest-api/overview)
- [Payload cookie strategy](https://payloadcms.com/docs/authentication/cookies)
- [Payload JWT strategy](https://payloadcms.com/docs/authentication/jwt)
- [Payload Local API access behavior](https://payloadcms.com/docs/local-api/overview)

## `payload-plugin-masquerade`

`payload-plugin-masquerade` is a community plugin rather than Payload core. Version 2.1.0 supports
Payload 3.44 and later.

It adds these collection endpoints:

- `POST /api/<authCollection>/:id/masquerade`
- `POST /api/<authCollection>/unmasquerade`
- `GET /api/<authCollection>/masquerade-search`

Starting masquerade performs these steps:

1. Require an authenticated `req.user`.
2. Load the target user.
3. Apply `canMasquerade`, or by default require `roles` to include `admin`.
4. Sign a normal Payload JWT for the target user, using the auth collection's normal token
   expiration and optionally creating a normal target-user session.
5. Replace the normal Payload authentication cookie with that target JWT.
6. Set a separate HTTP-only, HMAC-signed `payload-masquerade` cookie containing
   `originalUserId`, `targetUserId`, `startedAt`, and a nonce.
7. Invoke an optional `onMasquerade` callback and redirect.

While masquerading, Payload's standard authentication sees the target JWT and therefore supplies
the target as `req.user`. The signed side cookie is used by the plugin UI and the unmasquerade
endpoint. It is not incorporated into ordinary Payload access-control decisions. Payload's default
strategy order checks an explicit `JWT` or `Bearer` header before the cookie. Masquerade therefore
changes cookie-authenticated browser calls, but it does not override an Authorization header that
an API client continues to send.

Stopping masquerade verifies the current user against the target in the signed side cookie. It then
signs a normal JWT for the original user and restores the Payload authentication cookie. Finally,
it deletes the side cookie, invokes an optional callback, and redirects.

The plugin offers optional start and stop callbacks for an audit trail. It does not provide
built-in per-request attribution of mutations to both the administrator and the target. A caller
that only sees `req.user` sees the target. Its documented session duration is also the auth
collection's normal duration rather than a separately limited impersonation lifetime. Ending
masquerade mints a new original-user token or session. It does not revoke the target session it
created, so that target session remains valid until normal expiry or separate revocation.

There is also a community-plugin-specific access caveat: its user-search endpoint requires only an
authenticated caller and uses Payload's Local API without `overrideAccess: false`. Payload's Local
API bypasses access control by default. As written, the endpoint can therefore return auth
collection data outside the collection's normal read policy unless `targetUserWhere` and returned
fields make that safe. This is another reason to treat the plugin as an implementation example,
not a security baseline.

Stable source links for the reviewed version:

- [Plugin README at v2.1.0](https://github.com/manutepowa/payload-plugin-masquerade/tree/1b545f51945bffa154828be4cdf285484b1e34eb)
- [Start endpoint](https://github.com/manutepowa/payload-plugin-masquerade/blob/1b545f51945bffa154828be4cdf285484b1e34eb/src/endpoints/masqueradeEndpoint.ts)
- [Stop endpoint](https://github.com/manutepowa/payload-plugin-masquerade/blob/1b545f51945bffa154828be4cdf285484b1e34eb/src/endpoints/unmasqueradeEndpoint.ts)
- [Signed side-cookie implementation](https://github.com/manutepowa/payload-plugin-masquerade/blob/1b545f51945bffa154828be4cdf285484b1e34eb/src/cookies/masqueradeCookie.ts)
- [Payload authentication strategy order](https://github.com/payloadcms/payload/blob/4f7f9f250231efb5ed1335cfb422a44a799634e4/packages/payload/src/config/defaults.ts#L102-L105)
- [Payload credential extraction precedence](https://github.com/payloadcms/payload/blob/4f7f9f250231efb5ed1335cfb422a44a799634e4/packages/payload/src/auth/extractJWT.ts#L8-L78)
- [Payload target-user JWT resolution](https://github.com/payloadcms/payload/blob/4f7f9f250231efb5ed1335cfb422a44a799634e4/packages/payload/src/auth/strategies/jwt.ts#L104-L149)
- [Plugin user-search endpoint](https://github.com/manutepowa/payload-plugin-masquerade/blob/1b545f51945bffa154828be4cdf285484b1e34eb/src/endpoints/searchUsersEndpoint.ts#L13-L54)
- [Payload Local API access-control default](https://payloadcms.com/docs/local-api/access-control)

## Better Auth admin plugin

Better Auth can be connected to Payload through community adapters and plugins. Its admin plugin
models impersonation explicitly:

- `POST /admin/impersonate-user` creates a session that represents the target user.
- The session row stores the administrator's ID in `impersonatedBy`.
- The impersonation session lasts until the browser session ends or one hour by default. The
  duration is configurable separately.
- Administrators cannot impersonate another administrator by default. A distinct
  `impersonate-admins` permission enables that operation.
- `POST /admin/stop-impersonating` returns to the administrator.

The important design property is not the endpoint naming. The impersonation state is a first-class
session record that carries both subject and actor. It is not a normal target-user credential with
browser-only context.

Sources:

- [Better Auth admin plugin](https://better-auth.com/docs/plugins/admin)
- [Better Auth community Payload adapters](https://better-auth.com/docs/adapters/community-adapters)

## Comparison with the current RSpace design

RSpace REST API v2 deliberately separates browser authentication from API authentication:

- `POST /api/v2/oauth/tokens` resolves the current subject and original actor from the browser
  session.
- Normal REST API v2 routes authenticate API keys or OAuth bearer tokens. A signed UI-audience JWT
  must also match the live browser session. API keys and external OAuth remain stateless.
- During `runAs`, the session's effective `User` is the target user.
- Starting or releasing `runAs` rotates the browser authentication context, invalidating tokens
  from the previous identity immediately.
- The legacy UI-token endpoint refuses token creation during `runAs`.
- REST API v1 rejects a session-bound REST API v2 UI token.
- The frontend keeps REST API v2 UI tokens in query memory, not shared session storage.

This is stricter than `payload-plugin-masquerade`. A copied UI token cannot act as the target
without the same live browser session, and it always retains the administrator who initiated the
delegation. API-v2-backed UI can therefore operate with the target's permissions during `runAs`.

## Implemented RSpace behavior

The implemented behavior follows a Better Auth-style delegated identity:

1. Represent the authenticated caller as both an actor and a subject. Authorization uses the
   subject's roles and resource access. The delegated-request security event records both identities.
2. Mint a distinct session-bound UI token shape rather than an ordinary target-only credential.
3. Sign the subject ID and, during delegation, the actor ID. Also sign the opaque session context,
   audience, issued time, and expiry.
4. Keep it non-refreshable and require the live browser session, bounding its useful lifetime by
   that session even when the JWT expiry is later.
5. Rotate the session context when `runAs` starts or ends, and reject disabled subjects.
6. Derive `users/me.session.operatedAs` from the authenticated actor and subject.
7. Attribute throttling and security events consistently. Prefer the actor or delegation for abuse
   controls, while retaining the subject for authorization and user-facing audit context.

Domain mutation code must retain both identities in its audit event. Booking Configuration and
Feature Flag mutations record the actor normally. They add the subject to the event data during
delegation.

The delegated token does not define which users an administrator can target. The legacy `runAs`
authorization remains authoritative for that decision. Review privileged-target rules separately.
The Better Auth default rejects impersonation of another administrator and is a useful baseline.

An alternative is to authenticate same-origin REST requests directly with the browser session.
That approach would preserve Shiro's `runAs` stack. It would remove REST API v2's isolation from
ambient sessions and require a clear CSRF strategy for mutations. An explicit delegated principal
is a better fit.
