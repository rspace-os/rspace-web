# Add a REST API v2 collection

REST API v2 resource definitions specify the public fields, access rules, relationships, and default
sort. A `CollectionDescription` can be built programmatically or from an annotated record.

For predicate compilation and signed relationship IDs, see
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
