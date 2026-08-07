# Add a REST API v2 collection

REST API v2 uses resource configuration.

A resource definition specifies these items:

- The public fields.
- The access rules.
- The relationships.
- The default sort.

A resource operations class connects the definition to a domain manager. An
`ApiV2ResourceSpec` bean registers the resource. Registration adds the routes, error mappings, and
resource schemas to OpenAPI.

Use a concrete controller for an operation that is not collection CRUD. Concrete routes, such as
`/api/v2/users/me`, can exist with registered resources.

## Current concrete routes

The current implementation supplies these routes outside the collection controller:

| Route | Access | Result |
| --- | --- | --- |
| `GET /api/v2/config` | Public | Application version, branding, help links, description, and support email. |
| `GET /api/v2/openapi.json` | Public | The generated OpenAPI 3.1 document. |
| `POST /api/v2/oauth/tokens` | Browser session | A non-cached UI OAuth token. |
| `GET /api/v2/users/me` | Authenticated | Identity, roles, capabilities, external identifiers, and API session state. |
| `GET /api/v2/users/me/profile-image` | Authenticated | The current profile image as a non-cached PNG file. |

Do not add a resource spec for one of these routes. Use a concrete controller for a similar
non-CRUD operation.

## Add a resource

A resource has these parts:

1. A domain entity with JavaBean getters and setters.
2. An annotated API resource record.
3. A `ResourceOperations<T, ID>` class.
4. A domain manager and a DAO.

### 1. Define the public resource

The resource record is an allowlist. The framework does not instantiate this record. Each record
component maps to one entity property. The API does not show an entity property that is absent from
the record.

```java
package com.researchspace.widgets.model;

import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import java.util.List;

@ApiV2ResourceDefinition(name = "widgets", entity = Widget.class, id = "id")
public record ApiV2WidgetResource(
    @ApiV2ResourceField(
            description = "Stable widget identifier.",
            example = "42")
        Long id,
    @ApiV2ResourceField(
            requiredOnCreate = true,
            maxLength = 255,
            description = "Widget name.",
            example = "Centrifuge rotor")
        String name,
    @ApiV2ResourceField(description = "True when the widget is available.") boolean enabled) {

  public static final CollectionDescription<Widget> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2WidgetResource.class,
          List.of(),
          List.of(new Sort("name", true), new Sort("id", true)),
          AccessPolicy.authenticated());
}
```

The resource name is the URL segment. This example adds `/api/v2/widgets`. A resource name must be
unique. Do not change a resource name after clients use it.

Each default sort must meet these rules:

- It uses only declared sortable fields.
- It does not contain a field more than once.
- Its last field is the ID field.

The ID field makes the sort result stable.

### 2. Add the operations class and the resource spec

```java
package com.researchspace.widgets.api.v2;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.widgets.model.ApiV2WidgetResource;
import com.researchspace.widgets.model.Widget;
import com.researchspace.widgets.service.WidgetManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration(proxyBeanMethods = false)
public class WidgetResourceOperations implements ResourceOperations<Widget, Long> {

  private final WidgetManager manager;

  public WidgetResourceOperations(WidgetManager manager) {
    this.manager = manager;
  }

  @Bean
  ApiV2ResourceSpec<Widget, Long> widgetApiV2Resource() {
    return new ApiV2ResourceSpec<>(
        ApiV2WidgetResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.widget.create",
        "errors.api.v2.widget.update",
        Map.of(
            ResourceOperation.CREATE,
            List.of(
                ApiV2ErrorMapping.of(
                    WidgetNameConflictException.class,
                    HttpStatus.CONFLICT,
                    "errors.api.v2.widget.name.conflict",
                    "A widget with this name already exists."))));
  }

  @Override
  public ResourcePage<Widget> find(ResourceRequest request, User actor) {
    return manager.getWidgets(request);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return manager.countWidgets(request);
  }

  @Override
  public Optional<Widget> findById(Long id, User actor) {
    return manager.getWidget(id);
  }

  @Override
  public Widget create(ParsedDocument document, User actor) {
    return manager.createWidget(WidgetCreate.from(document), actor);
  }

  @Override
  public List<Widget> createMany(List<ParsedDocument> documents, User actor) {
    return manager.createResources(
        documents.stream().map(WidgetInput::from).map(WidgetInput::toWidget).toList(), actor);
  }

  @Override
  public Optional<Widget> update(Long id, ParsedDocument document, User actor) {
    return manager.updateWidget(id, WidgetPatch.from(document), actor);
  }

  @Override
  public List<Widget> updateMany(
      ResourceRequest request, ParsedDocument document, User actor) {
    return manager.updateWidgets(request, WidgetPatch.from(document), actor);
  }

  @Override
  public Optional<Widget> delete(Long id, User actor) {
    return manager.removeWidget(id, actor);
  }

  @Override
  public List<Widget> deleteMany(ResourceRequest request, User actor) {
    return manager.removeWidgets(request, actor);
  }
}
```

Spring finds each `ApiV2ResourceSpec` bean below `com.researchspace`. Do not add the resource to a
central registry. At startup, `ApiV2ResourceCatalog` validates all resource definitions. Startup
fails if a definition is invalid.

The ID parser converts one URL segment to the ID type. It must reject an invalid ID.

The create and update error keys are message keys. The parser uses these keys for invalid request
bodies. Put REST API v2 messages in the applicable `server.*.json` catalog below
`src/main/webapp/ui/src/modules/common/i18n/locales/`.

### 3. Keep domain work in the manager

The operations class converts API input to domain commands. The manager has these responsibilities:

- Domain authorization that the resource access policy cannot express.
- Transactions.
- Validation that needs two or more fields.
- Validation that needs stored data.
- Lifecycle actions and side effects.
- Limits and rules for bulk operations.

The DAO reads and writes the database. The DAO applies the typed `ResourceRequest`. The query
returns a `ResourcePage`. A controller or an operations class must not call a DAO.

Do not pass raw `where`, `sort`, or field-selection text to a manager. The common request parser
validates this text against the public resource definition.

### 4. Use the REST API v2 collection standard in application code

Use the REST API v2 collection types as the standard application vocabulary. This rule applies to
HTTP code, managers, scheduled jobs, event handlers, and other services.

A conventional mutable collection manager must implement `CollectionManager<T, ID>`. Extend
`AbstractCollectionManager<T, ID>` when its persistence rules fit the default implementation. Use
`ResourceRequest` for selection, `ResourcePage` for paged results, and `ParsedDocument` for a
validated update. This common contract removes resource-specific paging, filtering, and bulk CRUD
code.

Keep domain authorization, validation, transactions, audit events, and lifecycle effects in the
manager. Override the default manager hooks for these rules. Do not copy the standard collection
pipeline into a resource-specific service.

Every read operation receives the caller. Caller-independent collections can ignore it.
Collections that calculate caller-specific values must use the supplied user. They must not read a
browser session or an implicit security context.

Use `InMemoryCollectionQuery` for a bounded collection that does not use database queries. This
adapter applies the standard filter, sort, page, and count rules to a resource snapshot.

Use an escape hatch only when the standard operations cannot express a domain action. Examples
include a command that changes several aggregate types or an action that is not CRUD. Keep the
normal collection routes on the standard contract. Document the reason for each escape hatch near
its public interface.

## Reuse the operations layer

The generic REST controller uses an `ApiV2ResourceRegistration`. The registration performs API
access checks, parses JSON, resolves relationships, and renders the response. It then calls the
resource operations class.

Other Java code can inject the concrete operations class. Use this method when the caller already
has REST v2 types such as `ResourceRequest` or `ParsedDocument`.

```java
@Service
public final class WidgetReportService {

  private final WidgetResourceOperations widgetOperations;

  public WidgetReportService(WidgetResourceOperations widgetOperations) {
    this.widgetOperations = widgetOperations;
  }

  public ResourcePage<Widget> findWidgets(ResourceRequest request) {
    return widgetOperations.find(request);
  }
}
```

Inject the concrete class. Do not inject `ResourceOperations<?, ?>`. Many resource operations beans
implement that interface, so an interface injection is not unique.

Code can also inject its `ApiV2ResourceSpec` bean and call `operations()`. Use a qualifier when more
than one resource spec is in the injection context.

```java
private final ApiV2ResourceSpec<Widget, Long> widgetSpec;

public WidgetReportService(
    @Qualifier("widgetApiV2Resource") ApiV2ResourceSpec<Widget, Long> spec) {
  this.widgetSpec = spec;
}

public ResourcePage<Widget> findWidgets(ResourceRequest request) {
  return widgetSpec.operations().find(request);
}
```

Direct calls to `ResourceOperations` do not run the registration layer. A direct call does not do
these tasks:

- It does not authenticate the caller.
- It does not apply `AccessPolicy`.
- It does not apply field access rules.
- It does not parse or validate JSON.
- It does not resolve relationship references.
- It does not render sparse fields or expanded relationships.
- It does not check whether the HTTP operation is exposed.

For this reason, do not use a direct operations call to bypass the REST registration. The caller
must supply a validated `ResourceRequest`. For a write, the caller must supply a validated
`ParsedDocument` with resolved relationships. The caller must also perform the required access
checks.

Most services must call the concrete standard collection manager instead of the operations class.
The manager is the shared application boundary for collection commands. This rule keeps the same
transaction, authorization, validation, and audit behavior for REST, scheduled jobs, event
handlers, and other services.

Use the operations class in these cases:

- The caller works with REST v2 request types.
- The caller needs the same conversion from `ParsedDocument` to a domain command.
- The caller has already completed the REST v2 access and validation steps.

Use the standard collection manager in these cases:

- The caller already has an entity or another validated domain value.
- The caller performs a write outside the REST request pipeline.
- The caller needs domain authorization, transactions, lifecycle actions, or audit events.

Do not call `ApiV2ResourceRegistration` from a manager or a DAO. Registration is an HTTP boundary.
A call from a lower layer would reverse the dependency direction.

If many non-HTTP callers need the same document conversion, move that conversion to a small command
factory. Call the factory from the operations class and from the other callers. Keep the standard
collection types at the manager boundary. Do not move HTTP parsing or response rendering into a
manager.

## Standard routes

The default resource spec exposes these operations:

| Operation | Method and route | Operations method |
| --- | --- | --- |
| `LIST` | `GET /api/v2/widgets` | `find` |
| `COUNT` | `GET /api/v2/widgets/count` | `count` |
| `READ` | `GET /api/v2/widgets/{id}` | `findById` |
| `CREATE` | `POST /api/v2/widgets` | `create` |
| `BULK_CREATE` | `POST /api/v2/widgets/bulk` | `createMany` |
| `UPDATE` | `PATCH /api/v2/widgets/{id}` | `update` |
| `BULK_UPDATE` | `PATCH /api/v2/widgets?where=...` | `updateMany` |
| `DELETE` | `DELETE /api/v2/widgets/{id}` | `delete` |
| `BULK_DELETE` | `DELETE /api/v2/widgets?where=...` | `deleteMany` |

Use the full `ApiV2ResourceSpec` constructor to expose fewer operations:

```java
return new ApiV2ResourceSpec<>(
    ApiV2WidgetResource.DESCRIPTION,
    this,
    Long::valueOf,
    "errors.api.v2.widget.create",
    "errors.api.v2.widget.update",
    EnumSet.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
    Map.of());
```

Operation exposure and access are different controls:

- `exposedOperations` controls whether a route exists and appears in OpenAPI.
- `AccessPolicy` controls who can use an exposed route.

For a read-only HTTP contract, omit all write operations. Also use a read-only access policy.

### Default audit routes

Each registered resource also has these routes:

| Method and route | Result |
| --- | --- |
| `GET /api/v2/widgets/{id}/audit` | A page of audit events for one readable resource. |
| `GET /api/v2/widgets/{id}/audit/count` | The number of matching audit events. |

The caller must authenticate with an API key or an OAuth bearer token. The caller must also have
read access to the resource. The audit handler applies its normal actor visibility rules.

The list route accepts `page`, `limit`, `dateFrom`, `dateTo`, and repeated `actions` parameters. The
count route accepts the date and action filters. The server limits each search to 183 days. It uses
the last 183 days when the client does not supply dates.

The framework reads `@AuditTrailData` and the single `@AuditTrailIdentifier` method from the entity.
A resource without this metadata returns an empty audit result. Use a stable identifier that is
unique across audit domains. An entity in the `UNKNOWN` audit domain must include the resource name
in its audit identifier.

The response payload contains only audit properties that have matching readable REST v2 fields or
relationships. It does not contain the audit identifier or audit-only properties. This rule
prevents an audit response from bypassing field access.

## Resource configuration

### `@ApiV2ResourceDefinition`

| Option | Meaning |
| --- | --- |
| `name` | Stable plural resource name and URL segment. The name must be unique. |
| `entity` | Domain entity for the resource. The entity must be unique in the resource graph. |
| `id` | Resource ID field. The builder makes this field read-only. |
| `auditFields` | Adds available audit fields. The default is `true`. Use `false` only when the domain must not expose audit data. |

The annotated type must be a record. Each record component must have
`@ApiV2ResourceField`.

When `auditFields` is true, the framework looks for these entity properties:

| API field | Entity property, in search order |
| --- | --- |
| `createdAt` | `createdAt`, `creationDate` |
| `updatedAt` | `updatedAt`, `modificationDate` |
| `createdBy` | `createdBy` |
| `updatedBy` | `updatedBy`, `lastUpdatedBy`, `modifiedBy` |

Do not add discovered audit fields to the record or to the explicit relationships. The audit fields
are nullable and read-only. Clients can read, filter, and sort the date fields. Clients can use
normal relationship selectors for user fields, such as `createdBy.value`.

A creator or updater property of type `User` becomes a relationship to the `users` resource. An
old entity can return a username. In that case, the API keeps a scalar compatibility field. New
writable entities must store `User` relationships and must publish the normal RSpace audit events.
`BookingConfiguration` is an example.

The framework omits an audit field when the entity has no matching property. It does not publish a
field that clients cannot query.

### `@ApiV2ResourceField`

| Option | Default | Meaning |
| --- | --- | --- |
| `readAccess` | `INHERITED` | Controls field visibility. |
| `createAccess` | `INHERITED` | Controls create input. `NEVER` rejects the field. |
| `updateAccess` | `INHERITED` | Controls update input. `NEVER` rejects the field. |
| `requiredOnCreate` | `false` | Rejects a create body that omits the field. |
| `nullable` | `false` | Permits an explicit JSON `null`. Omission is different from `null`. |
| `filterable` | `true` | Permits the field in a `where` expression. |
| `sortable` | `true` | Permits the field in a `sort` expression. |
| `property` | Component name | Selects the entity JavaBean property. |
| `maxLength` | Unset | Sets the maximum input string length. |
| `title`, `description` | Empty | Adds OpenAPI text. |
| `example`, `additionalExamples` | Empty | Adds OpenAPI wire examples. |
| `format`, `pattern` | Empty | Adds OpenAPI string metadata. |
| `defaultValue` | Empty | Documents the wire default. |
| `minLength`, `minimum`, `maximum`, `enumValues` | Unset | Adds OpenAPI constraints. |
| `deprecated` | `false` | Marks the OpenAPI field as deprecated. |

The annotation builder supports these Java types:

| Java type | JSON/OpenAPI type |
| --- | --- |
| `String` | string |
| `long`, `Long` | integer, `int64` |
| `boolean`, `Boolean` | boolean |
| `java.util.Date` | string, `date-time` |

The entity property must have a getter. The framework makes the ID read-only. It also makes a
property read-only when that property has no setter. For other server-managed properties, set both
`createAccess = NEVER` and `updateAccess = NEVER`.

Set `filterable = false` and `sortable = false` for a computed property that is not persistent.
This setting prevents the query layer from sending the property to Hibernate.

Use a programmatic `CollectionDescription` for another scalar type. Also use it for a custom
writer or row-specific field access.

A programmatic writable field can call `defaultValue(value)` to supply a fixed value when a create
document omits that field. An explicit client value, including an allowed `null`, takes precedence.
The framework applies this default only on create and publishes it in the create schema. Dynamic
defaults are not supported yet; apply contextual values in the create operation.

Most annotation options only change OpenAPI. They do not add domain validation. The runtime options
in the table are exceptions. The manager must enforce all domain rules.

## Request and response payloads

### Create

Send a flat resource document. Do not use a JSON:API `data` envelope.

```http
POST /api/v2/widgets
Content-Type: application/json

{
  "name": "Centrifuge rotor",
  "enabled": true
}
```

Before it calls the operations class, the parser rejects these inputs:

- An unknown field.
- A read-only field.
- A missing required field.
- An invalid scalar value.
- A relationship with an invalid form.

### Bulk create

Put one or more create documents in `docs`.

```http
POST /api/v2/widgets/bulk
Content-Type: application/json

{
  "docs": [
    { "name": "Centrifuge rotor", "enabled": true },
    { "name": "Microscope stage", "enabled": false }
  ]
}
```

The request object must contain only a non-empty `docs` array. The endpoint validates and resolves
all documents before it calls the manager. The manager must process one batch in one transaction.
The result order must match the input order.

A validation error identifies the array position, such as `docs[1].name`. One validation error
prevents the creation of the full batch. A successful request returns status `201` and the standard
bulk result.

### Patch

An omitted field does not change. An explicit `null` is valid only for a nullable field or
relationship.

```http
PATCH /api/v2/widgets/42
Content-Type: application/json

{
  "enabled": false
}
```

Use `document.values().containsKey("field")` when a command must distinguish an omitted field from
an explicit `null`.

### List

```http
GET /api/v2/widgets?where=enabled==true&sort=name&limit=20&page=1
```

```json
{
  "docs": [
    {
      "id": 42,
      "name": "Centrifuge rotor",
      "enabled": true,
      "createdAt": "2026-08-01T10:15:30Z",
      "updatedAt": "2026-08-02T11:16:31Z",
      "createdBy": {"relationTo": "users", "value": 7},
      "updatedBy": {"relationTo": "users", "value": 12}
    }
  ],
  "totalDocs": 1,
  "limit": 20,
  "page": 1,
  "pagingCounter": 1,
  "totalPages": 1,
  "hasPrevPage": false,
  "hasNextPage": false,
  "prevPage": null,
  "nextPage": null
}
```

### Count and bulk results

A count response contains the number of matches:

```json
{
  "totalDocs": 12
}
```

A successful bulk operation returns the affected documents and an empty error list:

```json
{
  "docs": [{ "id": 42, "name": "Centrifuge rotor", "enabled": false }],
  "errors": []
}
```

A bulk update or delete request must contain a `where` filter. Put the batch limit and other
resource rules in the resource manager. Document these rules in the OpenAPI operation. Do not use
one limit for all resource types.

## Query configuration

The resource definition is also the query allowlist. Clients can use these parameters:

- `where` filters with RSQL.
- `sort` with public field names. Use `-` for descending order.
- `page` and `limit` for pagination.
- `fields[resource-name]` for an inclusive field list.
- `exclude[resource-name]` for an exclusive field list.
- `depth` for relationship expansion.

Examples:

```text
?where=enabled==true;name==*rotor*&sort=name,-createdAt
```

```text
?fields[widgets]=name,enabled
```

The response always contains the ID. A field access rule can hide a field. A client cannot use a
hidden field in `where` or `sort`.

## Access control

Access control has three layers:

1. `AccessPolicy` controls resource operations.
2. Field configuration controls field reads and writes.
3. The manager controls domain rules that need stored data or permission services.

The default policy requires authentication. Configure anonymous reads explicitly. REST API v2
accepts an `apiKey` header or an OAuth bearer token. REST API v2 usually ignores browser cookies.
A request with only a session cookie is usually anonymous.

`POST /api/v2/oauth/tokens` is the only exception. It uses an existing authenticated browser
session to create a UI OAuth token. It does not create a browser session. It ignores API keys and
bearer tokens. Permissive API CORS does not apply to this route.

The authentication interceptor always resolves supplied credentials and passes the caller, or an
anonymous caller, to an access function. `ApiV2EndpointSpec` supplies the coarse controller policy;
a handler with no contributed spec defaults to authenticated access. Public controllers contribute
`AccessFunction.anyone()`. The generic CRUD controller also contributes `anyone()` at this seam so
that its selected `ApiV2ResourceSpec` operation can make the final decision from the collection
policy. Invalid supplied credentials are rejected even when the endpoint is public.
The server uses the API key when both credential headers are present.

These policy fields are independent:

| Policy field | Operation |
| --- | --- |
| `readAccess` | List, count, read by ID, and relationship resolution. |
| `createAccess` | Single create and bulk create. |
| `updateAccess` | Single update and bulk update. |
| `deleteAccess` | Permanent single delete and bulk delete. |
| `softDeleteAccess` | A configured change to the deleted state. |

`softDeleteAccess` does not replace `deleteAccess`. A soft-delete operation must use
`AccessContext.Operation.SOFT_DELETE`. A hard-delete operation must use `DELETE`. Do not use the
same route for two behaviors that clients cannot distinguish.

An access function can return a row constraint. The framework combines the constraint with the
client filter. It does this for list, count, read by ID, and bulk operations. An unreadable row
returns 404.

Do not run authorization functions during OpenAPI generation. The `INHERITED` preset uses the
documentation from the matching collection operation. Every other access function must include
`AccessDocumentation`. The documentation must state its authentication rule and denial codes.

A custom `createAccess` function runs after structural parsing. It runs before relationship
resolution and persistence. For one create document, `context.requireInput()` returns the complete
immutable `ParsedDocument`. For bulk create, `context.inputs()` returns all documents in input
order.

```java
AccessFunction documentedCreateAccess =
    AccessFunction.documented(
        "A member can create a widget only in a workspace of that member.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED, AccessPolicy.FORBIDDEN),
        context -> {
          if (!context.isAuthenticated()) {
            return AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED);
          }
          Long workspaceId = (Long) context.requireInput().values().get("workspaceId");
          return membershipChecker.isMember(context.user(), workspaceId)
              ? AccessResult.allowed()
              : AccessResult.denied(AccessPolicy.FORBIDDEN);
        });
```

Create access cannot return a row constraint because a row does not exist. For bulk create, use
`inputs()`. Do not use `requireInput()`. A custom field function uses `Field.creatableBy(...)`. It
receives one document through `requireInput()`.

### Stateless requests

The servlet filter removes cookie headers before Shiro processes a REST API v2 request. The filter
also prevents servlet-session reads and writes. It preserves the existing session only for
`POST /api/v2/oauth/tokens`.

The authentication interceptor does not log in to Shiro. It does not log out of Shiro after the
request. Endpoint metadata selects browser-session authentication only for the token route. These
rules prevent a browser session from changing other API authorization.

### Rate limits

REST API v2 has separate throttle beans from REST API v1. Both versions use the same deployment
properties.

REST API v2 uses thread-safe Bucket4j buckets with greedy refill. Each caller bucket applies the
15-second, hourly, and daily limits atomically. A separate bucket applies the global limit and
minimum request interval.

The REST API v2 throttle uses these properties:

| Property | Purpose |
| --- | --- |
| `api.throttling.enabled` | Enables all request throttles. |
| `api.user.limit.15s` | Sets the limit for one caller during 15 seconds. |
| `api.user.limit.hour` | Sets the limit for one caller during one hour. |
| `api.user.limit.day` | Sets the limit for one caller during one day. |
| `api.global.limit.15s` | Sets the total limit during 15 seconds. |
| `api.global.minInterval` | Sets the minimum interval between all requests. |

When throttling is enabled, responses contain `X-Rate-Limit-*` usage headers. A rejected request
returns 429 and an API problem body.

Credential buckets use a SHA-256 fingerprint of the credential. Public and anonymous buckets use a
fingerprint of the client address. The server does not retain credentials or client addresses in
the bucket key.

The address resolver accepts `X-Forwarded-For`. Configure each trusted proxy to replace an
untrusted `X-Forwarded-For` header.

The caller bucket store keeps at most 10,000 keys. The separate global bucket supplies the hard
limit when caller buckets are removed.

### Cross-origin requests

Set `api.permissiveCors.enabled=true` to enable permissive cross-origin resource sharing (CORS).
REST API v2 then permits all origins.

An `OPTIONS` response permits `POST`, `PATCH`, `GET`, `OPTIONS`, and `DELETE`. It permits the
`apiKey`, `Authorization`, and `Content-Type` request headers.

### Deviations from REST API v1

These differences are intentional:

| Area | REST API v1 | REST API v2 |
| --- | --- | --- |
| Browser state | Authentication can use Shiro request state. | The stateless filter removes cookies and blocks servlet-session access. |
| Authentication lifecycle | The interceptor logs in to Shiro and logs out after the request. | The interceptor validates credentials without changing Shiro state. |
| External OAuth controls | External OAuth does not apply all API availability settings. | External OAuth applies deployment, user, and OAuth availability settings. |
| Disabled throttling | Responses contain fabricated rate-limit statistics. | Responses do not contain rate-limit statistics. |
| Throttle keys | The bucket store retains raw credentials and has no size limit. | The bucket store retains fingerprints and keeps at most 10,000 caller keys. |
| Concurrent requests | A caller can overspend one allowance through concurrent requests. | Bucket4j consumes every configured allowance atomically. |

## Relationships

Declare relationships separately from scalar record components. A relationship specifies these
items:

- Public name. Relationships currently expose one typed resource reference.
- Accepted target resource types.
- Entity reference reader.
- Accepted create and update forms.
- Required, nullable, read, and write rules.

This example is a polymorphic to-one value:

```json
{
  "enabled": true,
  "timezone": "Europe/Berlin",
  "target": {
    "relationTo": "instruments",
    "value": 123
  }
}
```

Before it calls the operations class, the relationship resolver verifies that the target exists.
It also verifies that the actor can read the target. The operations class receives a
`ResolvedResourceReference`. It does not receive an untrusted ID.

A target can support relationships without top-level CRUD routes. Register an
`ApiV2RelationshipTargetSpec<T, ID>` bean for this target. The lookup must return only entities that
the actor can read. A target-only resource adds schemas to OpenAPI but does not add paths.

At `depth=0`, `value` is the raw target ID. This reference object is also the update shape. If the
client supplies `globalId`, it must agree with `relationTo` and `value`. A relationship can also
accept a global-ID string as update shorthand.

At a greater depth, `value` is the expanded target document. The relationship envelope does not
change. The target registration applies its access and field rules. The API does not accept an
expanded response object as update input.

A response includes `globalId` when the target has a global-ID prefix. For example, an instrument
reference includes `"globalId": "IN123"`. The response keeps `globalId` when `value` is expanded. A
target without a global ID, such as a user, does not include this field.

## Errors and locale

REST API v2 returns errors as RFC 9457 problem details. The response content type is
`application/problem+json`.

```json
{
  "title": "The request contains an invalid value.",
  "status": 400,
  "code": "errors.api.v2.invalidRequest",
  "detail": "The request contains an invalid value.",
  "invalidParams": [
    {"name": "docs[1].name", "reason": "required"}
  ]
}
```

`title`, `status`, and `code` are always present. `detail` and `invalidParams` are present when the
error has that information.

The `code` value is stable. The server resolves `title` and `detail` from the message catalog.
Authentication errors also contain `WWW-Authenticate: Bearer`.

The same problem format applies when Spring rejects a request before it selects a handler. For
example, an unsupported method does not return an HTML error page.

The server uses the deployment locale. It does not select an unavailable locale from
`Accept-Language`.

Each REST API v2 response contains `Content-Language`. Each response also adds `Accept-Language` to
the `Vary` header.

## OpenAPI

The catalog publishes OpenAPI 3.1 at `/api/v2/openapi.json`. A routed resource spec adds its paths
and its schemas. A target-only spec adds schemas without paths.

The document service also merges Swagger annotations from concrete controllers. It validates the
complete document before it returns the document.

Production creates the document after a context refresh. Production caches the document privately
for one hour and supplies an `ETag`. A matching `If-None-Match` request returns 304.

Development creates and validates the document for each request. Development responses use
`Cache-Control: no-store`.

Use `OpenApiOperationDocumentation` for resource-specific descriptions and examples:

```java
Map.of(
    ResourceOperation.CREATE,
    OpenApiOperationDocumentation.builder()
        .description("Creates one widget in the selected workspace.")
        .requestExample(Map.of("name", "Centrifuge rotor", "enabled", true))
        .build())
```

Declare each non-standard error in the resource spec. An error mapping has these effects:

- It converts the mapped domain exception to the specified HTTP response for that operation.
- It resolves the response detail from the stable message key.
- It adds the error response to OpenAPI.

When exception classes overlap, the most specific matching class applies. An unmapped exception
continues through the normal controller-advice chain. Startup fails if a mapping refers to an
operation that the resource does not expose.

For a concrete controller, use the standard Swagger annotations. These include `@Operation`,
`@Parameter`, `@RequestBody`, `@ApiResponse`, and `@Schema`.

## Test the resource

Add focused tests for these areas:

- Resource metadata, fields, sort, access, schema, and relationships.
- Conversion from API documents to manager commands.
- Manager authorization, validation, transactions, and bulk rules.
- DAO filters, access constraints, sort, pagination, and counts.
- Spring catalog registration.
- HTTP payloads, responses, status codes, fields, filters, and writes.
- OpenAPI paths, schemas, examples, access data, and errors.

See these examples:

- `ApiV2MaintenanceResourceTest`
- `MaintenanceResourceOperations`
- `BookingConfigurationResourceOperationsTest`
- `ApiV2BookingConfigurationResourceTest`
- `ApiV2ResourceConfigTest`
- `ApiV2OpenApiGeneratorTest`

Run focused unit tests:

```bash
mvn test -Dtest=MyResourceTest,MyResourceOperationsTest -Dfast=true
```

A controller integration test extends `MVCTestBase` and has an `*IT.java` name. A DAO test extends
`SpringTransactionalTest`. A service that uses the configured name-based transaction advice must
have a bean name that ends in `Manager`. A service with another name must declare its transaction
boundary explicitly.

## Checklist

- [ ] The resource name and public fields are stable.
- [ ] The ID is read-only and is the last default sort field.
- [ ] Resource and field access fail closed.
- [ ] The exposed operations match the HTTP contract.
- [ ] The operations class calls a manager and does not call a DAO.
- [ ] The manager controls domain authorization, validation, transactions, and bulk rules.
- [ ] All user-facing errors are in message catalogs.
- [ ] Each public concrete controller contributes an `ApiV2EndpointSpec`.
- [ ] Each relationship verifies target read access.
- [ ] Each relationship target is registered.
- [ ] OpenAPI shows examples and resource-specific errors.
- [ ] Focused resource, operations, manager, DAO, controller, and OpenAPI tests pass.
