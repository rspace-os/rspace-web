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

  <dt>*/NewRecordForm.js</dt>
  <dd>
    There are forms for creating new samples, containers, and templates.
  </dd>

  <dt>*/Form.js</dt>
  <dd>
    There are forms for viewing and editing samples, subsamples, containers,
    and templates. When viewing all of the fields are simply disabled.
  </dd>

  <dt>*/BatchForm.js</dt>
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
a property needs to be added to the model class -- [Result],
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
in [Sample's Quantity.test.js], anything in [Organization/\_\_tests\_\_], or
[LocationsImageField.test.js].

### Downsides
In addition to the quick tricky testing, this technique tightly couples the
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
example, here is the type of the props for [Tags].

```
type TagsArgs = {|
  fieldOwner: HasEditableFields<{ tags: string, ... }>,
|};
```

Tags does not care if there are any other fields, hence the `...`, it just
cares that whatever instance of the class it is given, that that class has a
field called tags of type string.

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
completes the interface. For examples, look at [Name.test.js],
[Description.test.js], or [Tags.test.js].

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




[CanStore]: /src/main/webapp/ui/src/Inventory/Container/Fields/CanStore.js
[ContainerModel]: /src/main/webapp/ui/src/stores/models/ContainerModel.js
[Description.test.js]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Description.test.js
[Editable]: /src/main/webapp/ui/src/stores/definitions/Editable.js
[Expiry]: /src/main/webapp/ui/src/Inventory/Sample/Fields/Expiry.js
[ExtraFields]: /src/main/webapp/ui/src/Inventory/components/Fields/ExtraFields/ExtraFields.js
[InventoryRecord]: /src/main/webapp/ui/src/stores/definitions/InventoryRecord.js
[LocationsImageField.test.js]: /src/main/webapp/ui/src/Inventory/Container/Fields/__tests__/LocationsImageField.test.js
[Name.test.js]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Name.test.js
[Organization/\_\_tests\_\_]: /src/main/webapp/ui/src/Inventory/Container/Fields/Organization/__tests__
[Result]: /src/main/webapp/ui/src/stores/models/Result.js
[Sample's Quantity.test.js]: /src/main/webapp/ui/src/Inventory/Sample/Fields/__tests__/Quantity.test.js
[Sample's Quantity]: /src/main/webapp/ui/src/Inventory/Sample/Fields/Quantity.js
[SearchStore]: /src/main/webapp/ui/src/stores/stores/SearchStore.js
[Subsample's Quantity]: /src/main/webapp/ui/src/Inventory/Subsample/Fields/Quantity.js
[Tags.test.js]: /src/main/webapp/ui/src/Inventory/components/Fields/__tests__/Tags.test.js
[Tags]: /src/main/webapp/ui/src/Inventory/components/Fields/Tags.js
[interface segregation principle]: https://en.wikipedia.org/wiki/Interface_segregation_principle
