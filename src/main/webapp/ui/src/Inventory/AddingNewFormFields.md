# Adding New Form Fields

This document provides some background on forms in Inventory and direction on
how to add new fields to these forms. It doesn't cover the full complexities of
how fields work when batch editing but mainly focuses on regular creating and
editing forms.


## Background

A core part of the Inventory UI are the forms associated with each record type
that on desktop are shown on the right side of the UI and on mobile are shown
fullscreen. These forms come in various different forms.

<dl>

  <dt>*/NewRecordForm.tsx</dt>
  <dd>
    There are forms for creating new samples, containers, and templates.
  </dd>

  <dt>*/Form.tsx</dt>
  <dd>
    There are forms for viewing and editing samples, subsamples, containers,
    and templates. When viewing all of the fields are simply disabled.
  </dd>

  <dt>*/BatchForm.tsx</dt>
  <dd>
    There are forms for batch editing samples, subsamples, and containers, as
    well as a form for editing a mixture of two or more of any of those three
    record types.
  </dd>

</dl>

These forms are formed of fields, grouped into collapsible sections. These
fields themselves can be categorised as follows:

<dl>

  <dt>Fixed and common</dt>
  <dd>
    All records have fields like name and description and they are always
    shown. As such, they are usually supported by batch edit regardless of
    selection.
  </dd>

  <dt>Fixed, but type specific</dt>
  <dd>
    Every record of a given type has these fields and so include fields like a
    samples's expiry date and a container's restrictions on what kind of
    records can be stored within. These are only supported for batch editing
    when all of the records are of the same type.
  </dd>

  <dt>Varying</dt>
  <dd>
    These fields vary based on the specific record and are created by the user.
    These include the custom fields of samples and templates and the extra
    fields that all records have.
  </dd>

</dl>

All this is to say that there is a fair bit of complexity in how different
forms and their fields are implemented. Much of this is driven by how the
product has evolved. As such, there are two ways in which form fields are
implemented. Originally, all form fields were implemented using the SearchStore
technique but as the complexity of the product has grown and the need for
robust testing has become increasingly important, a second approach based on
an interface has increasingly been utilised.


## Changes common to both approaches

Regardless of which of the two techniques described below is used, there are a
couple of common steps the need to be taken. The first and most obvious is that
a property needs to be added to the model class -- [InventoryBaseRecord],
[ContainerModel], etc -- to store the value of the field. This should probably
align with what the API responds with to keep things simple.

Next, different fields are visible and editable at different times. Some fields
are only visible when creating a new record, others are only editable when
editing an existing record, etc. This logic is controlled by the
`currentlyVisibleFields` and `currentlyEditableFields` sets which contain the
names of all of the fields that are visible and editable at the current point
in time, respectively. Each model class defines a set of these strings,
typically called `FIELDS`. When a new field is added to this set, the method
`updateFieldState` should be checked to ensure that the field is set to be
visible and editable at the right times.


## SearchStore based approach

[SearchStore] manages all of the state concerning the main search
functionality of `/inventory`. Of the many things that it does, it exports a
reference to the [InventoryRecord] that is currently the active record to be
displayed in the right panel. To get this reference inside of a react component
simply use the following statement.

```
const { searchStore: { activeResult } } = useStores();
```

### Checking if the field is visible and editable
To check if a field is visible inside of a react component call
`activeResult.isFieldVisible` with the name of the field. To check if it is
editable call `activeResult.isFieldEditable`.

### Reading the current value
To get the current value simply access the activeResult's property. If the
field is only available on records of a particular type, then use an
`instanceof` check to cast `activeResult`, throwing an error if the field is
accidentally rendered for the wrong record type.

```
if (!activeResult || !(activeResult instanceof ContainerModel))
  throw "ActiveResult must be a Container";
```

### Setting a new value
To set a value of the field, use the `setAttributesDirty` method of Result,
which takes an object of properties and new values. It also sets the dirty flag
which enables the save button and ensures that the user is warned if they
navigate away without saving.

```
activeResult.setAttributesDirty({
  canStoreContainers: true,
  canStoreSamples: false,
});
```

### Testing
This is where this technique falls down: it is rather annoying to test as the
whole SearchStore must be mocked. Some examples of how to do this can be seen
in [Sample's Quantity.test.tsx], anything in [Organization/\_\_tests\_\_], or
[LocationsImageField.test.tsx].

### Downsides
In addition to the quite tricky testing, this technique tightly couples the
field to the model class. What I mean by that is that the react component that
implements the field depends on the precise way that the class which implements
the record type is implemented. The properties and models that the model class
exports are used directly and the way in which the component checks that it
even has the right type is based on an `instanceof` check. This violates the
[interface segregation principle] because the component depends on much more
than it strictly needs to.

To be a bit more concrete, an example of how this causes issues, is that
**fields implemented using this search store technique cannot be used for batch
editing** because different model classes are used to represent a collection of
records. Both the record model classes and the batch edit collection classes
implement a standard interface, as discussed below, but they are different
classes and so cannot both be used with `instanceof` checks.

### Examples
Examples of this approach include [ExtraFields], [CanStore], and
[Sample's Quantity].


## The HasEditableFields approach

An alternative approach is to use [HasEditableFields][Editable] which defines an
interface that both the model classes and the react components can depend on,
without having to depend on each other. The HasEditableFields interface is
parameterised by a type, in much the same way that an Array or Set is a
parameterised by a type. HasEditableFields parameterised type is an object that
defines the names of fields and their types (e.g. name and description are
strings), so that the field's react component can specify that it has a
dependency on any class which has a field with that name and type. It could be
a model class like [ContainerModel] or it could be a collection class for batch
editing like [ContainerCollection][ContainerModel]. In either case, the field
can be implemented to work the same.

Under this approach, the react components take as a prop an argument typically
called `fieldOwner` of type `HasEditableField<T>`, for some type `T`. For
example, here is the type of [Tags].

```
function Tags<
  Fields extends {
    tags: Array<Tag>;
  },
  FieldOwner extends HasEditableFields<Fields>
>({ fieldOwner }: { fieldOwner: FieldOwner }): React.ReactNode {
```

### Checking if the field is visible and editable
There is no way to check if a field is visible using this approach -- it is
assumed that all fields are visible here. If a field should be visible then
don't include it in the form, or pass in a boolean flag as an additional prop.

To check if a field is editable call `fieldOwner.isFieldEditable` with the
name of the field.

### Reading the current value

Now, to get access to the current value of the field, all the Tags component
has to do is
```
fieldOwner.fieldValues.tags
```
No `instanceof` check or casting required.

### Setting a new value
To set a new value, say once the user has typed one in in the case of Tags, the
field component need simply call `fieldOwner.setFieldsDirty`, which just like
setAttributesDirty takes an object of fields and their new values and sets the
dirty flag.
```
fieldOwner.setFieldsDirty({ tags: event.target.value });
```

### Testing
Testing such a react component is much easier to do because unlike the
SearchStore based approach that requires mocking the entire SearchStore, with
these tests the react component just needs to be passed on object that
completes the interface. For examples, look at [Name.test.tsx],
[Description.test.tsx], or [Tags.test.tsx].

### Downsides
The biggest downside to this approach is that there are additional changes that
need to be made to the model classes when adding a new field. It is not a
simple case of just adding a new property and calling it done as is the case
with the SearchStore approach.

First, each model class will define a type of the form
`${ClassName}EditableFields`, where `${ClassName}` is Result, ContainerModel,
ContainerCollection, etc. These are the fields that the model class exposes and
so it will then go on to
`implements HasEditableFields<${ClassName}EditableFields>`. Any new field
should be added to this object type.

Furthermore, the `fieldValues` property of the model class should be extended
to return the value in the property of the model class and `noValueLabel`
should be implemented to return a suitable value (typically null for simple
fields).

### Examples
Examples of this approach include [Tags], [Expiry], and [Subsample's Quantity].


## Template fields: field keys and display rules

The "varying" fields above include the custom fields that a sample or instrument
template defines and that every sample or instrument made from it copies. These
fields can carry a display rule (RPD-339): show this field only when a radio or
choice field of the same template has, or lacks, certain options. A hidden field
keeps its value and is skipped by mandatory checks. The user-facing behaviour is
described in [ConditionalFieldDisplay.md]. Extra fields have no template
definition and cannot have rules.

### Data model

Each template field ([FieldModel]) has two properties for this:

<dl>

  <dt>`fieldKey`</dt>
  <dd>
    A lower-case UUID that identifies the field within its template. RSpace
    generates version 7 but accepts any version, so check keys only as
    lower-case UUIDs. Rules name their source field by key, never by id, because
    ids change whenever a field is copied. The key is copied unchanged when a
    template is duplicated, when a record is created from a template, and when a
    record is updated to the latest template version. A key is immutable once
    saved. Record fields created before the feature have a `null` key until they
    are updated.
  </dd>

  <dt>`displayRule`</dt>
  <dd>
    The parsed rule, or `null` when the field is always shown. Build rules only
    with `createDisplayRule` or `parseDisplayRule` from [displayRule.ts], never as
    object literals: the type is branded so that a rule always comes from the
    schema.
  </dd>

</dl>

### Adding a template field

- Generate a key when the author adds the field, with `newFieldKey()` from
  `modules/common/field-rules/newFieldKey.ts` (a UUID version 7 that, unlike
  `crypto.randomUUID()`, also works on plain HTTP), in `TemplateModel.addField`
  and `InstrumentTemplateModel.addField` only. Do not generate one in the
  `FieldModel` constructor: it also builds legacy keyless record fields, which
  must stay keyless. A field with a key can be used in a rule straight away,
  before the template is first saved.
- Keep `fieldKey` and `displayRule` in `FieldModel.paramsForBackend`. Send
  `displayRule: null` explicitly when a saved field's rule is removed: on a
  template PUT an absent `displayRule` means "unchanged" and `null` means
  "remove".
- When copying fields between models (for example
  `InstrumentModel.overrideFields`), copy the key and the rule.
- A new field type can be a rule's source only if the shared rule core treats it
  as one (radio and choice today, `canBeSource` in [fieldVisibility.ts]). Any
  type can be a rule's target.

### Showing and hiding fields

- Visibility is computed by the shared evaluator in
  [modules/common/field-rules][field-rules], which has the same semantics as the
  Java evaluator on the server (parity vectors in
  `src/test/resources/fieldrules/display-rule-vectors.json`).
- [SampleModel] and [InstrumentModel] expose a computed `hiddenFields` set and
  `isFieldHidden(field)`, built with `hiddenFieldsOf` in [fieldVisibility.ts].
  [TemplateModel] overrides `hiddenFields` to show every field, because the
  template editor always shows all fields.
- The record forms render template fields through [useShownTemplateFields], which
  filters out hidden fields and returns an `announcer`, a polite live region
  ([RevealedFieldsAnnouncer]) that names the fields an answer shows or hides. Render
  it next to the fields. Do not move focus when fields appear or disappear.
- `FieldModel.validate()` returns valid for a hidden field, so a hidden mandatory
  field never blocks Save. The server makes the same decision for create,
  update, CSV import and the operations wizard.
- The operations wizard ignores mandatory fields hidden by the template's
  defaults ([templateResolution.ts]).

### Editing rules in the template editor

The template editor is built on shared, plain-props components in
[modules/common/field-editor][field-editor], which the ELN form field editor
also uses: `FieldCard`, `FieldTypeSelect`, `OptionsEditor`, `MoveButtons`,
`FieldDisplayCondition`, `DisplayConditionsNote` and `DisplayRuleSummary`.
[CustomField] adapts a MobX `FieldModel` onto `FieldCard` and passes each field's
`FieldDisplayCondition` in its `displayCondition` slot. [TemplateFieldList]
renders `DisplayConditionsNote` once above the fields while editing.

- `FieldDisplayCondition` is the "Display this field when…" section of one
  field. In edit mode it offers "Always" or a condition on another radio or
  choice field; read-only, it shows the "Shown when…" summary. It also lists
  the fields that depend on this one. The "Only when…" radio is always
  enabled: with no field the condition could read, the Field list shows a
  disabled "No other fields" item and the same text as its helper.
- It reports a rule through `onChange` only once the condition is complete and
  the shared validator accepts it. An unfinished or rejected condition stays a
  draft inside the component, with inline errors on the missing part, and the
  field keeps its stored rule meanwhile. Give the component a new React key to
  start a new edit session.
- While a draft exists, it calls `onIncompleteChange(true)`. [CustomField]
  stores that with `FieldModel.setDisplayConditionIncomplete`, and
  `FieldModel.validateDisplayCondition()` then fails with
  `template.displayRules.conditionIncomplete`, so the template's `validate()`
  refuses Save, and `assertDisplayConditionsComplete` stops the template being
  sent. Any new host of the component must refuse to save in the same way.
- [templateRuleFields] gives every condition of a template the same
  `RuleEditorField` list, a MobX computed that does not read names, so
  `ruleGraphFor` works out candidate sources once per change rather than once
  per field, and renaming a field re-renders only the cards that show its name.
- Pure helpers for candidate sources, drafts and pruning rules live in
  `ruleEditing.ts`.

Any new action that can break a rule must confirm first, naming the dependent
fields, and then prune the dependent rules in the same model change. Today
these are: deleting a field that rules read (`FieldModel.dependentFields`),
removing an option that a rule uses, and changing an unsaved source field to a
type that cannot be a source. An option that a rule uses cannot be renamed: the
`OptionsEditor` row is locked with the `template.displayRules.optionInUse`
reason, which only new options show, because saved options cannot be renamed
anyway. The server validates every rule of the template
together after a save, so a save that leaves a dangling rule is rejected.





[CanStore]: /src/main/webapp/ui/src/Inventory/Container/Fields/CanStore.tsx
[ContainerModel]: /src/main/webapp/ui/src/stores/models/ContainerModel.tsx
[Description.test.tsx]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Description.test.tsx
[Editable]: /src/main/webapp/ui/src/stores/definitions/Editable.ts
[Expiry]: /src/main/webapp/ui/src/Inventory/Sample/Fields/Expiry.tsx
[ExtraFields]: /src/main/webapp/ui/src/Inventory/components/Fields/ExtraFields/ExtraFields.tsx
[InventoryRecord]: /src/main/webapp/ui/src/stores/definitions/InventoryRecord.ts
[LocationsImageField.test.tsx]: /src/main/webapp/ui/src/Inventory/Container/Fields/__tests__/LocationsImageField.test.tsx
[Name.test.tsx]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Name.test.tsx
[Organization/\_\_tests\_\_]: /src/main/webapp/ui/src/Inventory/Container/Fields/Organization/__tests__
[InventoryBaseRecord]: /src/main/webapp/ui/src/stores/models/InventoryBaseRecord.tsx
[Sample's Quantity.test.tsx]: /src/main/webapp/ui/src/Inventory/Sample/Fields/__tests__/Quantity.test.tsx
[Sample's Quantity]: /src/main/webapp/ui/src/Inventory/Sample/Fields/Quantity.tsx
[SearchStore]: /src/main/webapp/ui/src/stores/stores/SearchStore.tsx
[Subsample's Quantity]: /src/main/webapp/ui/src/Inventory/Subsample/Fields/Quantity.tsx
[Tags.test.tsx]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Tags.test.tsx
[Tags]: /src/main/webapp/ui/src/Inventory/components/Fields/Tags.tsx
[interface segregation principle]: https://en.wikipedia.org/wiki/Interface_segregation_principle
[ConditionalFieldDisplay.md]: /DevDocs/public/ConditionalFieldDisplay.md
[CustomField]: /src/main/webapp/ui/src/Inventory/Template/Fields/CustomField.tsx
[displayRule.ts]: /src/main/webapp/ui/src/modules/common/field-rules/displayRule.ts
[field-editor]: /src/main/webapp/ui/src/modules/common/field-editor
[field-rules]: /src/main/webapp/ui/src/modules/common/field-rules
[FieldModel]: /src/main/webapp/ui/src/stores/models/FieldModel.ts
[fieldVisibility.ts]: /src/main/webapp/ui/src/stores/models/fieldVisibility.ts
[InstrumentModel]: /src/main/webapp/ui/src/stores/models/InstrumentModel.tsx
[RevealedFieldsAnnouncer]: /src/main/webapp/ui/src/modules/common/field-display/RevealedFieldsAnnouncer.tsx
[SampleModel]: /src/main/webapp/ui/src/stores/models/SampleModel.tsx
[TemplateFieldList]: /src/main/webapp/ui/src/Inventory/Template/Fields/TemplateFieldList.tsx
[TemplateModel]: /src/main/webapp/ui/src/stores/models/TemplateModel.tsx
[templateRuleFields]: /src/main/webapp/ui/src/Inventory/Template/Fields/templateRuleFields.ts
[templateResolution.ts]: /src/main/webapp/ui/src/Inventory/components/Operations/templateResolution.ts
[useShownTemplateFields]: /src/main/webapp/ui/src/Inventory/components/Fields/useShownTemplateFields.tsx
