# RSpace API changelog notes

Source notes for the public [API changelog](https://researchspace.helpdocs.io/article/dj4l39d70d-api-changelog).
Copy each release's section there when the release ships. The OpenAPI descriptions
(`src/main/webapp/resources/rspace_api_specs_*.yaml` for the ELN API and
`rspace_api_inventory_specs_*.yaml` for the Inventory API) are the reference for each property.

## Unreleased

### Display rules (conditional field display)

Fields of ELN Forms and of Inventory sample and instrument templates can now carry a display
rule: show this field only when a radio or choice field of the same form or template has (or
lacks) certain options. See [ConditionalFieldDisplay.md](ConditionalFieldDisplay.md) for the
behaviour in the product.

#### New properties

| API | Objects | New properties |
|---|---|---|
| ELN `/api/v1/forms` | form fields, read and written (`FormField`, `FormFieldPost`) | `fieldKey`, `displayRule` |
| ELN `/api/v1/documents` | document fields (`Field`), read only | `fieldKey`, `displayRule` |
| Inventory `/api/inventory/v1/sampleTemplates`, `/instrumentTemplates` | template fields, read and written | `fieldKey`, `displayRule` |
| Inventory `/api/inventory/v1/samples`, `/instruments` | record fields (`InventoryEntityField`), read only | `fieldKey`, `displayRule` |

- `fieldKey` is a lower-case UUID that identifies a field within its form or template. It is
  copied unchanged into ELN form versions and form copies, into duplicated Inventory templates,
  and into the samples and instruments made from a template. Rules name their source field by
  this key, never by id.
- `displayRule` is `null` when the field is always shown, or an object:

  ```json
  { "sourceFieldKey": "6f1c2e0a-9b7d-4c1e-8f3a-2d5b6c7e8f90", "operator": "IS_ANY_OF", "values": ["Yes"] }
  ```

  `operator` is `IS_ANY_OF`, `IS_NONE_OF`, `HAS_VALUE` or `HAS_NO_VALUE`. `values` is required
  and non-empty for the first two, and must be empty or omitted for the last two. A rule with
  any other property is rejected with 400.
- The API does not compute visibility. Every field and value is returned whether or not the field
  is currently shown. A client can evaluate visibility from the keys, rules and values:
  - a field whose source field is hidden is hidden too, whatever the operator;
  - a field whose source no longer exists is shown.
- An ELN document field reports the key and rule of the form version the document was created
  from, so a document read on its own can be evaluated even after a newer form version is
  published.
- Inventory sample and instrument fields created before this release have `fieldKey: null` and
  no rule until the record is updated to the latest template version.

#### Writing rules

- **Keys.** `fieldKey` may be sent only for a new field. It must be a lower-case UUID unused by
  any field of the form or template, including deleted fields and the other fields created in
  the same request. When omitted, the server generates one. A saved field's key cannot change:
  on PUT, omit it or send the current value.
- **PUT semantics.** Omitting `displayRule` on a saved field keeps its current rule. Sending
  `"displayRule": null` removes it.
- **Whole-form validation.** The rules of the whole form or template are validated together,
  after every change in the request has been applied. One request can therefore add a source
  field and a rule that uses it, or delete a source field and clear the rules that read it. The
  whole request is rejected, and nothing changes, if any rule:
  - names a source that does not exist, is the field itself, or is not a radio or choice field;
  - names an option that the source does not offer;
  - is part of a loop of fields that depend on each other.
- **Mandatory fields.** A mandatory field is only required while its rule shows it. This applies
  to Inventory sample and instrument create and update, CSV import validation and the operations
  wizard, and to the ELN Sign button calculation. Values for hidden fields are accepted and
  stored.

#### Other changes

- **Stricter radio and choice options (ELN forms API).** On POST and PUT `/api/v1/forms`, radio and
  choice options that the request adds or changes must not be blank and must not contain `&` or
  `=`. Options already stored are not re-checked unless the request changes them.
- **Failed form version update (ELN forms API).** If a PUT `/api/v1/forms/{id}` saves the fields
  but the draft cannot then be made the form's new version, the response is now `422` and the
  form, its draft and the edit lock are left as they were.
- **ELN forms API fixes.** PUT now renames number fields (the new name was dropped before),
  accepts a time field without `defaultValue`, maps a published form's field ids to its draft
  by the stored link rather than by position, and rejects unknown or repeated field ids. Forms
  no longer list deleted fields.
- **Instrument Landing page.** On POST `/api/inventory/v1/instruments`, a blank Landing page
  field is accepted even when the template marks it mandatory, as the UI already did. On update,
  blanking a shown, mandatory Landing page is still rejected.
- **PIDINST import.** POST `/api/inventory/v1/instruments/importPidinst` now fills radio and
  choice fields with the template's default selection (they were left blank before), and does
  not require mandatory fields that the template's defaults hide.
- **Inventory `selectedOptions`.** A `null` element in a radio or choice field's
  `selectedOptions` is now a field validation error rather than a server error.
- **Booking filters.** The `/api/v2` booking filters are unchanged. A filter on a template field
  value still matches records whose matching field is currently hidden, because hiding never
  removes the value.
