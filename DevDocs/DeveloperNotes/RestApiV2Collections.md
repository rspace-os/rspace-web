# Add a REST API v2 collection

REST API v2 resource definitions specify the public fields, access rules, relationships, and default
sort. A resource operations class connects the definition to a domain manager. An
`ApiV2ResourceSpec` bean registers its HTTP operations.

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

Implement `ResourceOperations<Widget, Long>` with `find`, `count`, and `findById`, delegating to the
domain manager. Register the adapter as a Spring bean below `com.researchspace`:

```java
@Bean
ApiV2ResourceSpec<Widget, Long> widgetApiV2Resource() {
  return new ApiV2ResourceSpec<>(
      ApiV2WidgetResource.DESCRIPTION,
      this,
      Long::valueOf,
      "errors.api.v2.widget.create",
      "errors.api.v2.widget.update",
      EnumSet.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
      Map.of());
}
```

Pair this read-only registration with `AccessPolicy.readOnly(...)` in the description. The ID parser
converts one URL segment to the ID type and must reject invalid IDs. At startup,
`ApiV2ResourceCatalog` discovers resource-spec beans and rejects invalid definitions; no central
registry entry is needed. Add operation-specific error mappings when an operation needs them.

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

## Add a read-only collection

Use `AccessPolicy.readOnly(...)` for a collection that clients only read, such as a relationship
target that a picker searches. The policy rejects mutations before dispatch, so the operations
class implements only `find`, `count`, and `findById`. Limit
`exposedOperations` to `LIST`, `COUNT`, and `READ` to match the routes the collection serves.

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

## Standard routes

| Operation | Method and route | Operations method |
| --- | --- | --- |
| `LIST` | `GET /api/v2/widgets` | `find` |
| `COUNT` | `GET /api/v2/widgets/count` | `count` |
| `READ` | `GET /api/v2/widgets/{id}` | `findById` |
| Runtime fields | `GET /api/v2/widgets/fields/{namespace}` | Registered runtime provider |

Limit `exposedOperations` to the operations the collection implements. An unexposed operation
returns `405` with an `Allow` header before the access policy runs. Exposure and access are
independent controls. Mutation routes are not exposed by the read controller.

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

Defaults and published examples pass declared-child validation, including scalar child
constraints. The declared child list controls the public object shape. Scalar to-many fields and generic
array row editors remain deferred; existing relationship multiplicity and arbitrary historical audit
JSON are unchanged.

`CollectionFieldTypes.object()` remains available for read-only opaque objects. Writable non-array
structured values can still use `structured(...)`; ARRAY values always use declared children.

A writable field can default to a fixed value when omitted on create: use the annotation's
`defaultValue`, or call `defaultValue(value)` on a programmatic field. An explicit client value,
including an allowed `null`, takes precedence. The framework applies it only on create and
records it as create-schema metadata. The default must pass the field's own rules, and a required
field cannot have one. Dynamic defaults are not supported yet. Apply contextual values in the create
operation.

### Field constraints

The framework enforces `maxLength`, `minLength`, `pattern`, `minimum`, `maximum` and `enumValues`
when a client writes a value. Violations return `INVALID_VALUE`. Schema metadata distinguishes body constraints from query constraints:

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

The read-only `containers` shape exposes `id`, `globalId`, `name`, `containerType`
and `deleted`. These fields are safe when the caller has only contents-based
visibility. Global IDs use `IC` for containers and `BE` for workbenches and cannot
be queried. Supply `ContainerReadAccess` to `ApiV2ContainerResource.description`
when registering the resource. Its static description denies reads until a
deployment supplies the policy.

`CollectionQueryExecutor.count` uses the unpaginated count query without loading an entity.
For scalar readers that do not need totals, `projectedSlice` returns a `ResourceSlice`.
It requests one extra row at the original page offset with counting disabled, trims that
row, and reports `hasNext`. Offsets beyond Blaze's integer limit return an empty slice
without querying. `page` and `projectedPage` retain their counted-page behavior.

The instrument default sort uses `(DTYPE, deleted, name, id)`. A full name key is
needed to preserve sort order. On the seeded 100k-row benchmark with the PI access
rule, MariaDB `ANALYZE FORMAT=JSON` for 21 rows examined 35 index entries and stopped
without a filesort, taking 0.22 ms. Without the index it scanned 100,000 rows and
sorted 90,002 active rows, taking 92.40 ms. These single warm-query measurements
justify the access path, rather than a latency guarantee. The temporary benchmark
index was removed after measurement.

## Request and response payloads

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

Collection lists omit `totalDocs` and `totalPages` unless `totals=true` is supplied.
Use `hasNextPage` and `nextPage` to traverse results. Without totals, `prevPage` is
available on every page after page 1, including pages past the end. Instruments
fetch a scalar window plus one row to determine whether another page exists;
resources without a scalar slice retain their counted query internally.
