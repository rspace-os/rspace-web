# Add a REST API v2 collection

REST API v2 resource definitions specify the public fields, access rules, relationships, and default
sort. A resource operations class connects the definition to a domain manager. An
`ApiV2ResourceSpec` bean registers its HTTP operations and schemas in OpenAPI.

Use a concrete controller for an operation outside collection CRUD, such as `/api/v2/users/me`.
For query compilation, authentication and request limits, see
[REST API v2 queries and access](RestApiV2QueriesAndAccess.md).

## Add a field type

Collection field implementations live in `model.collection.types`. Each implements
`CollectionFieldType` and owns its literal parser, JSON decoder, validation,
serialization, schema and supported operators. `CollectionFieldTypes` keeps the
convenience factories used by resource declarations.

For an annotated Java type, copy the registrations from
`CollectionFieldTypeRegistry.builtIns().registrations()`, add an exact-class
`Registration`, and construct a new immutable registry. Pass it to the
registry-taking `CollectionDescription.fromApiV2Resource` overload. Primitive
classes and boxed classes share a registration. Enum declarations use the enum
factory unless an exact registration overrides it. Programmatic `Field`
declarations can use a type instance directly, including types configured with
per-field limits.

`parse` validates filter literals. `parseDocument` decodes a JSON body value and
receives the write operation. The document parser checks the accepted JSON kind
before decoding and runs field validation afterwards. Keep rules that apply only
to writes in `validate` and `writeConstraints`; do not publish those rules in
filter schemas. Declared object and array types retain child validation before
assembly. A constraint decorator forwards the underlying decoder and comparison
strategy.

Runtime fields use `RuntimeFieldType` implementations in
`model.collection.types.runtime`. These decode persisted strings and parse filter
arguments, which can have different shapes: a choice filter takes one option,
while a stored choice serializes to an array. Add the stable identifier to
`RuntimeFieldValueType`, register its implementation in `RuntimeFieldTypeRegistry`,
and map the source domain's field kind in its runtime manager. The enum delegates
to the registered implementation. Share value helpers with predefined types only
when their rules match. Runtime numeric syntax and precision rules also govern
the DAO's numeric-text SQL conversion.

Each type declares a `FieldComparisonStrategy`. Reuse an existing strategy when
the storage semantics match. For a new strategy, implement
`RsqlFieldPredicateCompiler` in the DAO layer and add its `Registration` to a new
`RsqlFieldPredicateCompilerRegistry`, retaining the built-in registrations. Pass
that registry to `RsqlCollectionQuery` or `CollectionQueryExecutor`; nested
relationship queries use the same registry. Compiler contexts bind values and
count LIKE predicates through the operation's shared state. Model types never
contain SQL or import DAO classes. Runtime correlation, access checks and presence
semantics remain outside the value compiler.

Registry construction rejects duplicate mappings. Query construction validates
known field mappings, and compilation checks dynamically resolved fields. A
non-queryable type uses `FieldComparisonStrategy.NONE` and no filter operators.
Add tests for literal and document parsing, invalid values, schema output,
serialization and compiled parameter bindings when introducing a type. Storage
conversions also need a database comparison test.

## Current concrete routes

These routes use concrete controllers:

| Route | Access | Result |
| --- | --- | --- |
| `GET /api/v2/openapi.json` | Public | The generated OpenAPI 3.1 document. |
| `GET /api/v2/config` | Public | Application version, branding, help links, description, and support email. |
| `POST /api/v2/oauth/tokens` | Browser session | A non-cached, session-bound UI OAuth token that retains actor and subject during run-as. |
| `GET /api/v2/users/me` | Authenticated | Identity, roles, capabilities, external identifiers, and API session state. |
| `GET /api/v2/users/me/profile-image` | Authenticated | The current profile image as a non-cached PNG file. |

Use concrete controllers for these and similar non-CRUD routes. Do not add resource specs for them.

### Existing resources

The `users` collection exposes `LIST`, `COUNT`, and `READ`. A system administrator can read all
users; another authenticated user can read only their own row. An unexposed write returns `405`
with an `Allow` header before its access policy runs. Its operations class has no persistent writes.

`/api/v2/users/me/profile-image` returns a PNG only after the user has uploaded an image. Otherwise
it returns `404`. Fetch the image and supply a client placeholder on `404`; pointing an image element
directly at the route gives a new account a broken image.

`/api/v2/config` returns a public allowlist of deployment properties, never a reflection of all
properties, credentials, or secret paths. It is a concrete route, not a registered collection.

### Maintenance collection

The `maintenances` collection exposes stored `ScheduledMaintenance` fields and standard read and
mutation operations. An anonymous caller can read only a non-deleted row whose end is in the future.
System administrators can read all rows and run writes. `MaintenanceManager` owns domain validation
and authorization. Clients format the stored ISO-8601 dates themselves.

Maintenance creation accepts `startDate`, `endDate`, optional `stopUserLoginDate`, and `message`.
The manager validates the complete resulting window after a patch, including unchanged fields.

The computed `canUserLoginNow` field is neither filterable nor sortable. It is evaluated at request
time and must not be reused from a cached maintenance document.

## Add a resource

A resource has these parts:

| # | Requirement |
| --- | --- |
| 1. | A domain entity with JavaBean getters and setters. |
| 2. | An annotated API resource record. |
| 3. | A `ResourceOperations<T, ID>` class. |
| 4. | A domain manager and a DAO. |

> **Read only the ID through a lazy relationship.** The read transaction commits before the
> renderer reads the relationship. The renderer takes a lazy to-one target's ID from its Hibernate
> proxy without initializing it, but any other accessor on that proxy, such as a polymorphic kind
> accessor, initializes it and the request returns 500. Tests can miss this failure because the test
> context keeps the session open. Return the kind from an embedded value, or fetch the association
> eagerly.

### 1. Define the public resource

The resource record allowlists entity properties. Each component maps to one property; absent
properties are not exposed. The framework never instantiates the record.

```java
package com.researchspace.widgets.model;

import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.Sort;
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
          Widget.class,
          List.of(new Sort("name", true), new Sort("id", true)),
          AccessPolicy.authenticated());
}
```

The unique resource name is the URL segment. This example adds `/api/v2/widgets`. Do not change
the name after clients use it.

Each default sort must meet these rules:

| # | Requirement |
| --- | --- |
| 1. | It uses only declared sortable fields. |
| 2. | It does not contain a field more than once. |
| 3. | Its last field is the ID field. |

The ID field makes the sort result stable.

### 2. Add the operations class and the resource spec

```java
package com.researchspace.widgets.api.v2;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ApiV2ErrorMapping;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.auth.ApiV2Caller;
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
  public ResourcePage<Widget> find(ResourceRequest request, User subject) {
    return manager.getWidgets(request, subject);
  }

  @Override
  public long count(ResourceRequest request, User subject) {
    return manager.countWidgets(request, subject);
  }

  @Override
  public Optional<Widget> findById(Long id, User subject) {
    return manager.getWidget(id, subject);
  }

  @Override
  public Widget create(ParsedDocument document, ApiV2Caller caller) {
    return manager.createWidget(WidgetCreate.from(document), caller.subject(), caller.actor());
  }

  @Override
  public List<Widget> createMany(List<ParsedDocument> documents, ApiV2Caller caller) {
    return manager.createResources(
        documents.stream().map(WidgetInput::from).map(WidgetInput::toWidget).toList(),
        caller.subject(),
        caller.actor());
  }

  @Override
  public Optional<Widget> update(Long id, ParsedDocument document, ApiV2Caller caller) {
    return manager.updateWidget(
        id, WidgetPatch.from(document), caller.subject(), caller.actor());
  }

  @Override
  public List<Widget> updateMany(
      ResourceRequest request, ParsedDocument document, ApiV2Caller caller) {
    return manager.updateWidgets(
        request, WidgetPatch.from(document), caller.subject(), caller.actor());
  }

  @Override
  public Optional<Widget> delete(Long id, ApiV2Caller caller) {
    return manager.removeWidget(id, caller.subject(), caller.actor());
  }

  @Override
  public List<Widget> deleteMany(ResourceRequest request, ApiV2Caller caller) {
    return manager.removeWidgets(request, caller.subject(), caller.actor());
  }
}
```

Spring finds each `ApiV2ResourceSpec` bean below `com.researchspace`. Do not add a central registry
entry. At startup, `ApiV2ResourceCatalog` validates all definitions and fails if any is invalid.

The ID parser converts one URL segment to the ID type and must reject invalid IDs.

The parser uses the create and update message keys for invalid request bodies. Put REST API v2
messages in the applicable `server.*.json` catalog below
`src/main/webapp/ui/src/modules/common/i18n/locales/`.

### 3. Keep domain work in the manager

The operations class converts API input to domain commands. The manager handles:

| # | Requirement |
| --- | --- |
| 1. | Domain authorization that the resource access policy cannot express. |
| 2. | Transactions. |
| 3. | Validation that needs two or more fields. |
| 4. | Validation that needs stored data. |
| 5. | Lifecycle actions and side effects. |
| 6. | Limits and rules for bulk operations. |

The DAO reads and writes the database, applying the typed `ResourceRequest` and returning a
`ResourcePage`. Follow [Creating transactions](Transactions.md#creating-transactions) for the
controller, manager and DAO boundary.

Do not pass raw `where`, `sort`, or field-selection text to a manager. The common request parser
validates this text against the public resource definition.

### 4. Use the REST API v2 collection standard in application code

Use REST API v2 collection types throughout HTTP code, managers, scheduled jobs, event handlers,
and other services.

A conventional mutable collection manager must implement `CollectionManager<T, ID>`. Extend
`AbstractCollectionManager<T, ID>` when its persistence rules fit the default implementation. Use
`ResourceRequest` for selection, `ResourcePage` for paged results, and `ParsedDocument` for a
validated update. This common contract removes resource-specific paging, filtering, and bulk CRUD
code.

Keep domain authorization, validation, transactions, audit events, and lifecycle effects in the
manager. Override the default manager hooks for these rules. Do not copy the standard collection
pipeline into a resource-specific service.

Every read receives the caller. Caller-independent collections can ignore it; caller-specific
calculations must use the supplied user, never a browser session or implicit security context.
Managers must apply the resource read policy and row constraint before returning data, including
outside the HTTP registration pipeline.

For a REST API v2 request, the user supplied to a read is the effective subject. A write receives
an `ApiV2Caller`, which contains the effective subject and the originating actor. The manager must
authorize the subject. An audit event must use the actor and retain the subject when they differ.
Record the subject alongside the actor when they differ.

Use `InMemoryCollectionQuery` for a bounded collection that does not use database queries. This
adapter applies the standard filter, sort, page, and count rules to a resource snapshot.

Use an escape hatch only for domain actions the standard operations cannot express, such as a
command changing several aggregate types or a non-CRUD action. Keep normal collection routes on
the standard contract. Document each escape hatch's reason near its public interface.

## Add a read-only collection

Use `AccessPolicy.readOnly(...)` for a collection that clients only read, such as a relationship
target that a picker searches. The policy rejects mutations before dispatch, so the operations
class implements only `find`, `count`, and `findById`. Limit
`exposedOperations` to `LIST`, `COUNT`, and `READ` so OpenAPI documents only the routes the collection serves.

Each resource name has one catalog entry. Registration also makes it a relationship target, so
adding an `ApiV2RelationshipTargetSpec` for an existing resource-spec name causes a duplicate-target
startup error.

Describe a collection programmatically when a public field maps to a nested property. The annotated
record derives the query property from a bean property name, so it cannot describe a value such as
`editInfo.name`. `ApiV2InstrumentResource` is the example: it also declares `globalId` with
`withQueryCapabilities(false, false)`, because a derived value has no column to filter or sort.

Declare fields and relationships in one `List<? extends ResourceMember<T>>` passed to
`CollectionDescription`. Member names must be unique across both kinds. Each kind retains its
relative declaration order when exposed through `fields()` and `relationships()`.

Express a read rule as a row constraint when the rule fits the described fields. A read access
function returns `AccessResult.allowedWhere(...)`, and the registration folds that constraint into
the list, the count, the single read, and relationship resolution. `ApiV2InstrumentResource` hides
soft-deleted rows this way, so its operations class does not repeat the rule on each route.

Use an `InternalFilter` when a server access rule needs a property that clients must not query.
The query compiler can resolve this selector, but request parsing does not expose it.

Inventory sharing uses internal selectors for the owner, sharing mode, and sharing ACL.
`InventoryReadFilters.ALL` defines these selectors. Each applicable inventory description must
include that list.

`InstrumentReadAccess` returns one `AccessResult.allowedWhere(...)` constraint that uses those
selectors. The manager evaluates the policy. `InventoryDaoHibernate` compiles the trusted
constraint and combines it with the client filter. The database then applies access, filtering,
sorting, pagination, and totals together.

Do not filter rows again in the operations class. Java text rules differ from database collation,
so matched rows can disappear from the page and total.

Follow [Creating transactions](Transactions.md#creating-transactions): `InstrumentResourceOperations`
delegates to the transaction-advised `InstrumentEntityApiManager`.

## Reuse the operations layer

The generic REST controller uses an `ApiV2ResourceRegistration`. The registration performs API
access checks, parses JSON, resolves relationships, and renders the response. It then calls the
resource operations class.

Other Java code can inject the concrete operations class when it already has REST API v2 types
such as `ResourceRequest` or `ParsedDocument`.

```java
@Service
public final class WidgetReportService {

  private final WidgetResourceOperations widgetOperations;

  public WidgetReportService(WidgetResourceOperations widgetOperations) {
    this.widgetOperations = widgetOperations;
  }

  public ResourcePage<Widget> findWidgets(ResourceRequest request, User subject) {
    return widgetOperations.find(request, subject);
  }
}
```

Inject the concrete class, since many beans implement `ResourceOperations<?, ?>` and interface
injection is ambiguous.

Code can also inject its `ApiV2ResourceSpec` bean and call `operations()`. Use a qualifier when more
than one resource spec is in the injection context.

```java
private final ApiV2ResourceSpec<Widget, Long> widgetSpec;

public WidgetReportService(
    @Qualifier("widgetApiV2Resource") ApiV2ResourceSpec<Widget, Long> spec) {
  this.widgetSpec = spec;
}

public ResourcePage<Widget> findWidgets(ResourceRequest request, User subject) {
  return widgetSpec.operations().find(request, subject);
}
```

Direct calls to `ResourceOperations` skip the registration layer and these tasks:

| # | Requirement |
| --- | --- |
| 1. | Caller authentication. |
| 2. | `AccessPolicy` checks. |
| 3. | Field access checks. |
| 4. | JSON parsing and validation. |
| 5. | Relationship reference resolution. |
| 6. | Sparse field and expanded relationship rendering. |
| 7. | HTTP operation exposure checks. |

Do not use direct operations calls to bypass REST registration. The caller
must supply a validated `ResourceRequest`. For a write, the caller must supply a validated
`ParsedDocument` with resolved relationships and an `ApiV2Caller`. Construct
`ApiV2Caller.direct(user)` only when there is no delegated identity. The caller must also perform
the required access checks.

Most services must call the concrete standard collection manager instead of the operations class. This shared boundary for
collection commands keeps transaction, authorization, validation, and audit behavior consistent
across REST, scheduled jobs, event handlers, and other services.

Use the operations class in these cases:

| # | Requirement |
| --- | --- |
| 1. | The caller works with REST API v2 request types. |
| 2. | The caller needs the same conversion from `ParsedDocument` to a domain command. |
| 3. | The caller has already completed the REST API v2 access and validation steps. |

Use the standard collection manager in these cases:

| # | Requirement |
| --- | --- |
| 1. | The caller already has an entity or another validated domain value. |
| 2. | The caller performs a write outside the REST request pipeline. |
| 3. | The caller needs domain authorization, transactions, lifecycle actions, or audit events. |

Do not call `ApiV2ResourceRegistration` from a manager or DAO: calling this HTTP boundary from a
lower layer reverses the dependency direction.

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

| # | Requirement |
| --- | --- |
| 1. | `exposedOperations` controls whether a route exists and appears in OpenAPI. |
| 2. | `AccessPolicy` controls who can use an exposed route. |

An unexposed operation returns `405` with an `Allow` header listing supported methods. This check
precedes the access policy, so an unexposed write returns `405` even for anonymous callers.

For a read-only HTTP contract, omit all write operations. Also use a read-only access policy.

## Resource configuration

### `@ApiV2ResourceDefinition`

| Option | Meaning |
| --- | --- |
| `name` | Stable plural resource name and URL segment. The name must be unique. |
| `entity` | Domain entity for the resource. The entity must be unique in the resource graph. |
| `id` | Resource ID field. The builder makes this field read-only. |
| `auditFields` | Adds available audit fields. The default is `true`. Use `false` only when the domain must not expose audit data. |

The annotated type must be a record. The framework never instantiates it. Each record component
declares exactly one of these annotations:

| # | Requirement |
| --- | --- |
| 1. | `@ApiV2ResourceField` maps a scalar field to an entity bean property. |
| 2. | `@ApiV2ResourceRelationship` maps a `ResourceReference` component to a related resource. |
| 3. | `@ApiV2ResourceMember(factory = "methodName")` builds a custom field or relationship in code. |

Fields and relationships are declared the same way, as record components. The framework preserves
component order within the field and relationship lists and appends the automatic audit members to
each list. Rendered documents emit fields followed by relationships.

When `auditFields` is true, the framework looks for these entity properties:

| API field | Entity property, in search order |
| --- | --- |
| `createdAt` | `createdAt`, `creationDate` |
| `updatedAt` | `updatedAt`, `modificationDate` |
| `createdBy` | `createdBy` |
| `updatedBy` | `updatedBy`, `lastUpdatedBy`, `modifiedBy` |

Do not add discovered audit fields to the record. The audit fields are nullable and read-only.
Clients can read, filter, and sort the date fields. Clients can use normal relationship selectors for user fields, such as `createdBy.value`.

A creator or updater property becomes a relationship to the `users` resource, so it must return
`User`. A property that returns anything else, such as a legacy username string, fails at startup;
map it as a `User` association or set `auditFields = false`. Writable entities must also publish
the normal RSpace audit events. 

The framework omits an audit field when the entity has no matching property. It does not publish a
field that clients cannot query.

### `@ApiV2ResourceRelationship`

A relationship component has the type `ResourceReference<K, ID>`. `relationTo` lists one or more
`@ApiV2RelationshipTarget` entries, each naming a target `resource` and its `entity`.

```java
@ApiV2ResourceDefinition(name = "projects", entity = Project.class, id = "id")
public record ApiV2ProjectResource(
    @ApiV2ResourceField Long id,
    @ApiV2ResourceField String name,
    @ApiV2ResourceRelationship(
        relationTo =
            @ApiV2RelationshipTarget(resource = "users", entity = User.class))
        ResourceReference<String, Long> owner) {

  public static final CollectionDescription<Project> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2ProjectResource.class,
          Project.class,
          List.of(new Sort("id", true)));
}
```

The relationship's entity property defaults to the component name; `property` overrides it. The
default ID path is `<property>.id`, and the default kind path is `<property>.kind`. Use `idProperty`
and `kindProperty` to override them. A path starting with `<property>.` names the accessor on the
related value. For other paths, the runtime reader uses `id` or `kind`. For example, `idProperty =
"targetId"` still reads the related value's `id()` or `getId()`. An explicit `kindProperty` on a
monomorphic relationship fails at startup.

The relationship property and the related value's accessors must be readable at startup. JavaBean
getters and public no-argument methods work on records and ordinary classes. Associated entity IDs
use the Hibernate proxy identifier without initializing the proxy, including for automatic audit
relationships. The related value's ID accessor return type determines the collection ID type.

A single target without `kind` is monomorphic. Polymorphic targets all declare `kind`, which must be
a constant of the kind accessor's enum type or a String. Mixed targets and unknown enum constants
fail at startup. For example, an entity's `getTarget()` can return a record with `kind()` and `id()`
accessors. A final embeddable class with `type()` and `id()` also works when declared with
`kindProperty = "target.type"` and the default ID path `target.id`.

```java
@ApiV2ResourceRelationship(
    relationTo = {
      @ApiV2RelationshipTarget(
          resource = "documents",
          entity = Document.class,
          kind = "DOCUMENT",
          globalIdPrefix = "SD"),
      @ApiV2RelationshipTarget(
          resource = "folders",
          entity = Folder.class,
          kind = "FOLDER",
          globalIdPrefix = "FL")
    },
    writeOn = {WriteOperation.CREATE, WriteOperation.UPDATE},
    nullable = false,
    acceptGlobalIdOn = WriteOperation.CREATE)
ResourceReference<TargetKind, Long> target
```

Relationships default to read-only and nullable. Declare writable operations with `writeOn`; an
empty array means read-only. `requiredOnCreate` requires a create-writable relationship and implies
non-null even when `nullable` retains its default value. `acceptGlobalIdOn` enables global-ID input
for selected writable operations and requires a prefix on every target. `allowSelfReference`
defaults to false. `readAccess` and `writeAccess` use `ApiV2ResourceField.AccessPreset` and default
to `INHERITED`. `title`, `description`, `example`, and `deprecated` publish OpenAPI documentation.

### `@ApiV2ResourceMember`

`@ApiV2ResourceMember(factory = "methodName")` calls a static no-argument method on the record
returning a custom `Field` or `Relationship`. The member name must match the component, and its
entity type must accept the resource entity. The component type is unrestricted. Use it for a field
or relationship that the annotations cannot describe, such as a declared array or a relationship
with a custom reader.

### `@ApiV2ResourceField`

See [`ApiV2ResourceField`](../../src/main/java/com/researchspace/model/collection/ApiV2ResourceField.java)
for options and defaults, and
[`CollectionFieldTypeRegistry`](../../src/main/java/com/researchspace/model/collection/CollectionFieldTypeRegistry.java)
for built-in Java types. `property` defaults to the component name; omission differs from explicit
null. `createAccess`/`updateAccess = NEVER` rejects the field on that write.

The entity property must have a getter. The framework makes IDs and properties without setters
read-only. For other server-managed properties, set both
`createAccess = NEVER` and `updateAccess = NEVER`.

Set `filterable = false` and `sortable = false` for a computed property that is not persistent.
This setting prevents the query layer from sending the property to Hibernate.

For additional scalar types, see [Add a field type](#add-a-field-type). Use programmatic
descriptions for custom writers or row-specific access.

Every ARRAY field declares a non-empty list of uniquely named child fields directly. Use
`CollectionFieldTypes.array(Row.class, fields, adapter)` and `Field.embedded(name, type, reader)`.
The adapter constructs a row only after child parsing and validation. An overload accepts row and
whole-array validators plus inclusive minimum/maximum row counts. Use
`CollectionFieldTypes.object(Row.class, fields, adapter)` for nested groups and another declared
array for nested rows. Neither embedded values nor their children are filterable or sortable. Relationship children and cyclic declarations are
unsupported. The no-argument array factory and opaque structured ARRAY decoders are removed.
Array fields cannot be inferred from `@ApiV2ResourceField`; declare them programmatically with their
children. Annotation inference fails with that guidance rather than creating an opaque placeholder.

For example, this declares an array of labels with one required text child:

```java
record Label(String text) {}

var labels = CollectionFieldTypes.array(
    Label.class,
    List.of(Field.embedded("text", CollectionFieldTypes.text(80), Label::text).required()),
    values -> new Label((String) values.get("text")));
```

Each supplied array replaces the entire value. Unknown and non-writable child keys, null/scalar
rows, and direct array rows fail with indexed paths such as `labels[2].text`. Required
children apply on CREATE and UPDATE. Child defaults fill omitted keys only, without inventing rows.
Child write access runs after the complete parent input enters the caller context.
Required embedded children must support both write operations. Combining `required()` with
`writeOnlyOn(CREATE)` fails when the field is declared because no complete replacement UPDATE could
satisfy that child. Optional create-only children and read-only parent arrays remain supported.

Only declared readable properties are serialized, including DTO rows, caller-specific overrides,
and pre-rendered included documents. The parent gates the whole value and child restrictions narrow
that access. Entity values, overrides, defaults, examples, and fresh custom serializer results must
contain required children before access filtering. A custom serializer returns the complete declared
value; the shared field renderer applies child read access afterward.

Pre-rendered included documents have already passed validation and caller projection. They may omit
unreadable required children. Reprojection validates every supplied child and every required readable
child, plus row-count bounds. Row and whole-array rules run when all values needed to reconstruct the
rows remain available; those rules cannot be rerun after projection removes their input. Producers
must validate complete values before creating such an included document. This exception does not
apply to readOverrides or fresh serializer output.

Defaults and published examples pass declared-child validation, including scalar child constraints.
OpenAPI publishes object and array examples as JSON values, including additional examples.
STRING examples retain their raw text, even when that text looks like JSON.
Recursive `fields` metadata and OpenAPI object `items` come from those definitions. The declared
child list controls the public object shape; child constraints, documentation, nullability, and
operation-specific access shape recursive `properties` and `items`, with `additionalProperties: false`
on each object. The array factory's inclusive row bounds publish as `minItems` and `maxItems`. Request
schemas describe a supplied array as a complete replacement: required children and child defaults
apply to every supplied row on CREATE and UPDATE, while the outer field remains optional on update.
These schemas do not enable HTTP write routes for read-only collections. Read properties may be
omitted by child access, so read item schemas do not require those keys. Each declared property also
publishes `x-rspace-fields` for custom frontend renderers. Scalar to-many fields and generic array row
editors remain deferred; existing relationship multiplicity and arbitrary historical audit JSON are
unchanged.

`CollectionFieldTypes.object()` remains available for read-only opaque objects. Writable non-array
structured values can still use `structured(...)`; ARRAY values always use declared children.

A writable field can default to a fixed value when omitted on create: use the annotation's
`defaultValue`, or call `defaultValue(value)` on a programmatic field. An explicit client value,
including an allowed `null`, takes precedence. The framework applies it only on create and
publishes it in the create schema. The default must pass the field's own rules, and a required
field cannot have one. Dynamic defaults are not supported yet. Apply contextual values in the create
operation.

### Field constraints

The framework enforces `maxLength`, `minLength`, `pattern`, `minimum`, `maximum` and `enumValues`
when a client writes a value. Violations return `INVALID_VALUE`. OpenAPI publishes each rule only where the server checks it:

| Rule | Checked on writes | Checked in `where` | Published on |
| --- | --- | --- | --- |
| Java enum constants | Yes | Yes | Every schema |
| `maxLength` | Yes | Yes | Every schema |
| `minLength`, `pattern`, `minimum`, `maximum`, `enumValues` | Yes | No | Request and response bodies |

A nullable enum lists `null` among its values. A `pattern` is unanchored, like a JSON Schema
`pattern`: the value must contain a match. Write `^...$` to match the whole value. Use only syntax
that JavaScript and Java read the same way: no lookbehind, possessive quantifiers, inline flag groups
such as `(?i)`, or `\p{...}` classes.

Startup fails in these cases:

| # | Requirement |
| --- | --- |
| 1. | `minimum` or `maximum` is set on a field that is not a number. |
| 2. | `minLength`, `pattern` or `enumValues` is set on a field that is not a `String`. A Java enum field lists its constants automatically. |
| 3. | A `pattern` does not compile. |
| 4. | An example or a default breaks the field's own rules. |

A programmatic field uses `CollectionFieldTypes.constrained(type, constraints)`. One decorator holds
all of a scalar field's rules, so it cannot wrap another constrained type, a structured type or a
read-only opaque type. Declare constraints on embedded children using the same scalar types. A
constraint `Pattern` must have no flags, because the published pattern cannot carry them.

These per-field wire rules do not replace manager enforcement of domain rules.

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

Before calling the operations class, the parser rejects:

| # | Requirement |
| --- | --- |
| 1. | An unknown field. |
| 2. | A read-only field. |
| 3. | A missing required field. |
| 4. | An invalid scalar value, or an invalid declared object or array child. |
| 5. | A relationship with an invalid form. |

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

A validation error identifies the array position, such as `docs[1].name`, and prevents creation
of the entire batch. A successful request returns status `201` and the standard
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

A successful bulk operation returns the affected documents:

```json
{
  "docs": [{ "id": 42, "name": "Centrifuge rotor", "enabled": false }]
}
```

Bulk operations are atomic. One invalid document fails the whole batch with a problem body naming
the array position, such as `docs[1].name`. There is no per-document error list.

A bulk update or delete request must contain a `where` filter. Declare create and update/delete
batch limits in the resource spec's `CollectionMutationLimits`, and enforce the same limits in the
resource manager. Document these rules in the OpenAPI operation. Do not use one limit for all
resource types.

## JSON request binding

Stack-owned API v2 JSON bodies use mapper copies derived from the MVC converter.
`ApiV2JsonBody` preserves the collection parser's type rules and decimal-node
handling while limiting documents to 10 MiB, strings to 2 MiB, token counts to
100,000 and nesting depth to 100. The shared MVC mapper and other endpoints'
`JsonNode` bindings keep their existing configuration.

Register each fixed-shape request record in the single `ApiV2JsonMappers.register`
call in `WebConfig.extendMessageConverters` at the layer that introduces it.
The error advice reads that converter registration to identify strict bodies.

Fixed-shape request records use strict binding: unknown fields and scalar
coercions fail, including floating-point tokens such as `15.0` for integral
fields. Collection field codecs retain their existing numeric rules. Required
and nullable fields on HTTP records remain governed by their validation
annotations, so partial settings updates and validation messages are preserved.

Access replacements validate the registered resource and `If-Match` before
binding assignments to `ReplaceResourceAccessRequest`. Malformed fields return
`errors.api.v2.invalidRequest` with an additive `invalidParams` list using dotted
field names and array indices, such as `assignments[0].role`. All rows are
validated before the named-assignment limit; `audience:all-users` does not count
toward that limit.
