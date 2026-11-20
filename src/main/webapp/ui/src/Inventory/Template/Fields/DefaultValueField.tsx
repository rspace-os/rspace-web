import Autocomplete from "@mui/material/Autocomplete";
import Paper from "@mui/material/Paper";
import MuiTextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { usableOptions } from "@/modules/common/field-editor/ruleEditing";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import type { GalleryFile } from "../../../eln/gallery/useGalleryListing";
import type { Option } from "../../../stores/definitions/Field";
import { truncateIsoTimestamp } from "../../../stores/definitions/Units";
import type FieldModel from "../../../stores/models/FieldModel";
import { hasOptions } from "../../../stores/models/FieldTypes";
import { match } from "../../../util/Util";
import { DATACITE_RELATION_TYPES } from "../../components/Fields/Link/dataciteRelationTypes";
import CustomField from "../../components/Inputs/CustomField";
import LinkFieldValue from "../../Sample/Fields/TemplateFields/LinkFieldValue";

type DefaultValueFieldArgs = {
  field: FieldModel;
  editing: boolean;
  recordTypeName?: "sample" | "instrument";
};

function DefaultValueField({ field, editing, recordTypeName = "sample" }: DefaultValueFieldArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const _hasOptions = hasOptions(field.fieldType);
  const isAttachment = field.type === "attachment";
  const recordType = t(`recordTypes.${recordTypeName}.lowerPlural`);

  /*
   * It is quite expensive to re-render TinyMCE, resulting in a slightly
   * flicker every time, so we need to minimise the number of times we render
   * it. Now, for fields that already exist on the server, the id acts as a
   * perfectly fine key but for new fields they have no unique identifier.
   * So for those, this component's own id serves, which only changes when it
   * is re-mounted and thus the TinyMCE of the rich text field will have to be
   * re-rendered anyway.
   */
  const instanceId = React.useId();
  const key = field.id ?? instanceId;

  /*
   * A Link template field's "default value" is two things: the whitelist of relationship types
   * items may use (empty means all), and an optional default link stamped onto those items
   * (RSDEV-1246). The default reuses the item's own LinkFieldValue editor.
   */
  if (field.type === "link") {
    // siblings, not nested: InputWrapper renders a labelled fieldset, so nesting would make the
    // default-link controls announce as part of the "Allowed relationship types" group
    return (
      <>
        <InputWrapper
          label={t("fields.templateFields.defaultValue.allowedRelationshipTypes")}
          explanation={t(
            recordTypeName === "instrument"
              ? "fields.templateFields.defaultValue.allowedRelationshipTypesExplanationInstrument"
              : "fields.templateFields.defaultValue.allowedRelationshipTypesExplanation",
          )}
        >
          <Autocomplete
            multiple
            disabled={!editing}
            options={[...DATACITE_RELATION_TYPES]}
            value={field.allowedRelationTypes}
            // already-chosen types are greyed out rather than toggled off; they
            // are removed via their chip's delete icon instead
            getOptionDisabled={(option) => field.allowedRelationTypes.includes(option)}
            onChange={(_event, value) => field.setAttributesDirty({ allowedRelationTypes: value })}
            renderInput={(params) => {
              const { slotProps, ...textFieldProps } = params;
              return (
                <MuiTextField
                  {...textFieldProps}
                  variant="standard"
                  placeholder={
                    field.allowedRelationTypes.length === 0
                      ? t("fields.templateFields.defaultValue.allRelationshipTypes")
                      : ""
                  }
                  slotProps={{
                    ...slotProps,
                    htmlInput: {
                      ...slotProps.htmlInput,
                      "aria-label": t("fields.templateFields.defaultValue.allowedRelationshipTypes"),
                    },
                  }}
                />
              );
            }}
          />
        </InputWrapper>
        {/* a whole sub-form, so without a boundary it reads as a continuation of the
          allowed-types control above it */}
        <Paper variant="outlined" data-test-id="DefaultLinkSection" sx={{ mt: 2, p: 2 }}>
          <InputWrapper
            label={t("fields.templateFields.defaultValue.defaultLink")}
            explanation={t("fields.templateFields.defaultValue.defaultLinkExplanation")}
          >
            <LinkFieldValue
              field={field}
              // "" on an unsaved template, leaving the client-side self-link check inert; harmless,
              // since a template with no Global ID has nothing to self-link to yet
              sourceGlobalId={field.owner.globalId ?? ""}
              disabled={!editing}
              // the name is already entered in the Name field above
              showFieldName={false}
              // inside this section "Target" alone would be ambiguous against the item's own link
              targetHeading={t("fields.templateFields.defaultValue.defaultLinkTarget")}
              // InputWrapper renders the heading above, so these groups sit one level below
              nestHeadings
            />
          </InputWrapper>
        </Paper>
      </>
    );
  }

  /*
   * Have to use a variable here to make the suppression work. No idea why; may
   * well be a bug in Flow.
   */
  const fieldType = (field.type.charAt(0).toUpperCase() + field.type.slice(1)) as
    | "Attachment"
    | "Choice"
    | "Date"
    | "File"
    | "Number"
    | "Radio"
    | "Reference"
    | "String"
    | "Text"
    | "Time"
    | "Uri";

  const fieldValue = match<void, () => unknown>([
    [() => field.type === "radio", () => field.selectedOptions?.at(0) ?? null],
    [() => field.type === "choice", () => field.selectedOptions ?? []],
    [() => true, () => field.content],
  ])()();

  // Create props object based on field type
  const props: {
    key: string | number;
    value: unknown;
    type:
      | "Attachment"
      | "Choice"
      | "Date"
      | "File"
      | "Number"
      | "Radio"
      | "Reference"
      | "String"
      | "Text"
      | "Time"
      | "Uri";
    disabled: boolean;
    error: boolean;
    helperText: string;
    onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
    name?: string;
    noValueLabel?: string;
    options?: Array<Option>;
    allowOptionDeletion?: boolean;
    hideWhenDisabled?: boolean;
    onOptionChange?: (index: number, changedOption: Option) => void;
    onOptionRemove?: (index: number) => void;
    allowRadioUnselection?: boolean;
    attachment?: unknown;
    onAttachmentChange?: (file: File | GalleryFile) => void;
    disableFileUpload?: boolean;
  } = {
    key,
    value: fieldValue,
    type: fieldType,
    disabled: !editing,
    error: false,
    helperText: "",
  };

  // Common onChange handler
  const handleChange = ({ target: { value } }: { target: { value: unknown } }) => {
    field.setAttributesDirty(
      match<void, object>([
        [() => field.type === "radio", { selectedOptions: value ? [value] : [] }],
        [() => field.type === "choice", { selectedOptions: value }],
        [
          () => field.type === "date",
          {
            content:
              typeof value === "string" && value ? truncateIsoTimestamp(value, "date").orElse("NaN-NaN-NaN") : null,
          },
        ],
        [() => true, { content: value }],
      ])(),
    );
  };

  // Add properties based on field type
  props.onChange = handleChange;
  props.name = field.name;
  props.noValueLabel = t("fields.templateFields.defaultValue.none");

  // choice/radio options are edited in the field's options editor; here the author only picks defaults
  if (field.type === "radio" || field.type === "choice") {
    props.options = usableOptions(field.options.map((option) => option.value)).map((value) => ({
      value,
      label: value,
      editing: false,
    }));
    props.hideWhenDisabled = false;
  }

  // Add radio-specific props
  if (field.type === "radio") {
    props.allowRadioUnselection = true;
  }

  // Add attachment-specific props
  if (field.type === "attachment") {
    props.attachment = field.attachment;
    props.onAttachmentChange = (file: File | GalleryFile) => field.setAttachment(file);
    props.disableFileUpload = true;
  }

  // the wrapper's legend does not label a single input, so name the input itself
  const inputLabel = {
    slotProps: { htmlInput: { "aria-label": t("fields.templateFields.customField.defaultValue") } },
  };
  const custom = React.createElement(CustomField, {
    ...props,
    ...(fieldType === "String" || fieldType === "Number" ? inputLabel : {}),
  } as React.ComponentProps<typeof CustomField>);

  return (
    <InputWrapper
      label={
        isAttachment
          ? t("fields.templateFields.customField.defaultDescription")
          : t("fields.templateFields.customField.defaultValue")
      }
      explanation={
        _hasOptions ? t("fields.templateFields.customField.defaultOptionsExplanation", { recordType }) : null
      }
    >
      {custom}
    </InputWrapper>
  );
}

export default observer(DefaultValueField);
