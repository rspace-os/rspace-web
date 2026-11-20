# RSpace API changelog notes

Source notes for the public [API changelog](https://researchspace.helpdocs.io/article/dj4l39d70d-api-changelog).
Copy each release's section there when the release ships. The OpenAPI descriptions
(`src/main/webapp/resources/rspace_api_specs_*.yaml` for the ELN API and
`rspace_api_inventory_specs_*.yaml` for the Inventory API) are the reference for each property.

## Unreleased

### Display rules (conditional field display)

Fields of ELN Forms and of Inventory sample and instrument templates can now carry a display
rule: show this field only when another field of the same form or template, its source, has (or
lacks) a certain answer. Radio, choice, number and one-line text fields can be sources (ELN
String fields only when they are not password fields; Inventory string fields). See
[ConditionalFieldDisplay.md](ConditionalFieldDisplay.md) for the behaviour in the product.

#### New properties

| API | Objects | New properties |
|---|---|---|
| ELN `/api/v1/forms` | form fields, read and written (`FormField`, `FormFieldPost`) | `fieldKey`, `displayRule` |
| ELN `/api/v1/documents` | document fields (`Field`), read only | `fieldKey`, `displayRule` |
| Inventory `/api/inventory/v1/sampleTemplates`, `/instrumentTemplates` | template fields, read and written | `fieldKey`, `displayRule` |
| Inventory `/api/inventory/v1/samples`, `/instruments` | record fields (`InventoryEntityField`), read only | `fieldKey`, `displayRule` |

- `fieldKey` is a lower-case UUID that identifies a field within its form or template. RSpace
  generates UUID version 7 keys, but accepts and returns keys of any version. It is copied
  unchanged into ELN form versions and form copies, into duplicated Inventory templates, and
  into the samples and instruments made from a template. Rules name their source field by this
  key, never by id.
- `displayRule` is `null` when the field is always shown, or an object:

  ```json
  { "sourceFieldKey": "6f1c2e0a-9b7d-4c1e-8f3a-2d5b6c7e8f90", "operator": "IS_ANY_OF", "values": ["Yes"] }
  ```

  The operators that a rule can use depend on its source:

  | Source | Operators |
  |---|---|
  | Radio, choice | `IS_ANY_OF`, `IS_NONE_OF`, `HAS_VALUE`, `HAS_NO_VALUE` |
  | One-line text (ELN String, not password; Inventory string) | `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `HAS_VALUE`, `HAS_NO_VALUE` |
  | Number | `EQUALS`, `NOT_EQUALS`, `LESS_THAN`, `AT_MOST`, `GREATER_THAN`, `AT_LEAST`, `HAS_VALUE`, `HAS_NO_VALUE` |

  `values` is required and non-empty for `IS_ANY_OF` and `IS_NONE_OF`. For `HAS_VALUE` and
  `HAS_NO_VALUE`, omit `values` or send `[]`; the server also accepts `null`. Every other
  operator compares with exactly one value. A malformed rule is rejected with 400: one with any
  other property, an unknown operator, values that are not strings, or values that do not suit
  `IS_ANY_OF`, `IS_NONE_OF`, `HAS_VALUE` or `HAS_NO_VALUE` (such as empty `values` for
  `IS_ANY_OF`, or any values for `HAS_VALUE`). A known operator that does not suit the source
  field is rejected with 422 (see [Writing rules](#writing-rules)).
- **Compatibility:** the `operator` enum has new members (`EQUALS`, `NOT_EQUALS`, `CONTAINS`,
  `LESS_THAN`, `AT_MOST`, `GREATER_THAN`, `AT_LEAST`). Clients that read rules into a strict
  enum of operators must accept them.
- For option operators, `values` hold the raw option text. A comma that is part of an option is written as a comma,
  not as the `&#44;` escape used in ELN choice field content.
- The two APIs write an empty `values` differently. The Inventory API omits it; the ELN forms
  and documents API always returns `"values": []`. Treat a missing `values` as an empty list.
- **Text and number comparisons.** A comparison value is stored as sent, not trimmed. It must
  not be blank after trimming. Against a number source it must be a number as written, with no
  surrounding spaces: ASCII digits with an optional sign, decimal point and exponent, such as
  `5`, `-2.5`, `+5`, `.5`, `5.`, `05` or `1e3`. It may be at most 1,000 characters, with at
  most nine written exponent digits. For a nonzero value, the exponent in normalised scientific
  notation must be between -2000 and 2000 inclusive, so `9e2000` is accepted and `1e2001` is
  not; zero is exempt. Commas, `0x`, `Infinity` and `NaN` are not numbers. When evaluating:
  - Field values and rule values are trimmed of leading and trailing white space (the
    characters JavaScript's `String.prototype.trim()` removes). A value that is empty after
    trimming is no value.
  - Text is compared after Unicode NFC normalisation and lowercasing. This is not case folding:
    `STRASSE` does not equal `straße`. After trimming, NFC normalisation and lowercasing,
    Greek final sigma (ς) is treated as sigma (σ). The server uses ICU4J 79.1 (Unicode 18).
  - Numbers are compared as exact decimals: `1.0` equals `1`, and `-0` equals `0`.
  - When the source has no value, or a number source holds a value that is not a number, every
    comparison is false except `NOT_EQUALS`, which is true.
  - A stored rule that cannot apply (an operator that does not suit its source, the wrong
    number of values, a blank value, or a number comparison whose value is not a number) is
    ignored, so the field is shown, as for a missing source.
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

- **Keys.** `fieldKey` may be sent only for a new field. It must be a lower-case UUID, of any
  version, unused by any field of the form or template, including deleted fields and the other
  fields created in the same request. When omitted, the server generates one, a UUID version 7.
  A saved field's key cannot change: on PUT, omit it or send the current value.
- **PUT semantics.** Omitting `displayRule` on a saved field keeps its current rule. Sending
  `"displayRule": null` removes it.
- **Whole-form validation.** The rules of the whole form or template are validated together,
  after every change in the request has been applied. One request can therefore add a source
  field and a rule that uses it, or delete a source field and clear the rules that read it. The
  whole request is rejected, and nothing changes, if any rule:
  - names a source that does not exist (`sourceMissing`) or is the field itself (`sourceSelf`);
  - names a source that cannot be read, because it is not a radio, choice, number or one-line
    text field, or is an ELN String field marked as a password field (`sourceType`);
  - uses an operator that does not suit its source (`operatorNotForSource`);
  - for a text or number comparison, does not have exactly one value (`valueCount`), has a value
    that is blank after trimming (`valueBlank`), or, against a number source, has a value that
    is not a number (`valueNotANumber`);
  - names an option that the source does not offer (`valueUnknown`);
  - is part of a loop of fields that depend on each other (`cycle`).

  The API returns 422 with a validation message, in the server's language, naming the field;
  the identifiers above are the message keys under `validation.fieldDisplayRule.`, not values
  returned in the response. The Inventory API reports the first problem it finds. Making an ELN String field that a rule reads into a password field is
  therefore rejected with `sourceType`.
- **Size limits.** A rule may list at most 1,000 values, each at most once, and the rule as
  stored may be at most 32,768 characters. A request that breaks a limit is rejected with 422,
  naming the field, and nothing changes. The messages are
  `validation.fieldDisplayRule.valueTooMany`, `validation.fieldDisplayRule.ruleTooLong` and
  `validation.fieldDisplayRule.valueDuplicate`. This applies to the ELN forms API and to the
  Inventory sample and instrument template APIs.
- **Mandatory fields.** A mandatory field is only required while its rule shows it. This applies
  to Inventory sample and instrument create and update, CSV import validation and the operations
  wizard, and to the ELN Sign button calculation. Values for hidden fields are accepted and
  stored.

#### Other changes

- **Choice field content is checked (ELN documents API).** On POST and PUT `/api/v1/documents`,
  choice field `content` lists the selected options separated by commas, with a comma that is
  part of an option written as `&#44;`.
  - Content that names a value that is not an option of the field is rejected with 422
    (`validation.fieldData.choiceNotAnOption`). The message lists at most 10 such values, then
    how many more there are.
  - Content with an empty value, such as `","`, `"Yes,"` or `"Yes,,No"`, is rejected with 422
    (`validation.fieldData.choiceEmptyValue`). Only wholly empty content clears the selection.
  - On PUT, content equal to what a GET returns for the field is accepted without these checks,
    so a document that already holds values that are not options can still be updated by
    sending back the fields it was read with.
  - **Compatibility:** values are not trimmed, so `"Yes, No"`, with a space after the comma, is
    now rejected because ` No` is not an option. Send `"Yes,No"`.
- **Form PUT response (ELN forms API).** PUT `/api/v1/forms/{id}` now returns the form's fields,
  as POST does. Before, a PUT that replaced every field returned `"fields": []`.
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
- **Blank over blank is no change (Inventory).** On an Inventory update, such as a sample or
  instrument PUT, a non-option field sent with blank `content` when its stored content is also
  blank is treated as unchanged and is not written. Before, an
  instrument with a blank mandatory Landing page could not be updated, because the client sends
  `""` for it and that was checked as a new, empty value. Clearing a filled mandatory field is
  still rejected.
- **Instrument Landing page.** On POST `/api/inventory/v1/instruments`, a blank Landing page
  field is accepted even when the template marks it mandatory, as the UI already did. On update,
  blanking a shown, mandatory Landing page is still rejected.
- **PIDINST import.** POST `/api/inventory/v1/instruments/importPidinst` now fills radio and
  choice fields with the template's default selection (they were left blank before), and does
  not require mandatory fields that the template's defaults hide.
- **Inventory `selectedOptions`.** A `null` element in a radio or choice field's
  `selectedOptions` is now a field validation error rather than a server error.
