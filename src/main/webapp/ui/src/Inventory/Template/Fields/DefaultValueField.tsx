import React from "react";
import { observer } from "mobx-react-lite";
import FieldModel from "../../../stores/models/FieldModel";
import { hasOptions } from "../../../stores/models/FieldTypes";
import { match } from "../../../util/Util";
import * as ArrayUtils from "../../../util/ArrayUtils";
import CustomField from "../../components/Inputs/CustomField";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import Button from "@mui/material/Button";
import AddIcon from "@mui/icons-material/Add";
import { makeStyles } from "tss-react/mui";
import { truncateIsoTimestamp } from "../../../stores/definitions/Units";
import { type Option } from "../../../stores/definitions/Field";
import { type GalleryFile } from "../../../eln/gallery/useGalleryListing";

const useStyles = makeStyles()((theme) => ({
  buttonWrapper: {
    display: "inline",
    marginTop: theme.spacing(1),
  },
  bottomSpaced: {
    marginBottom: theme.spacing(4),
  },
}));

const AdditionalOptionButton = ({
  field,
}: {
  field: FieldModel;
}): React.ReactNode => {
  const { classes } = useStyles();
  return (
    <div className={classes.buttonWrapper}>
      <Button
        color="primary"
        variant="outlined"
        startIcon={<AddIcon />}
        onClick={() => {
          field.setAttributesDirty({
            options: [
              ...field.options,
              {
                value: "",
                label: "",
                editing: true,
              },
            ],
          });
        }}
      >
        Add Value
      </Button>
    </div>
  );
};

type DefaultValueFieldArgs = {
  field: FieldModel;
  editing: boolean;
};

function DefaultValueField({
  field,
  editing,
}: DefaultValueFieldArgs): React.ReactNode {
  const _hasOptions = hasOptions(field.fieldType);
  const isAttachment = field.type === "attachment";

  /*
   * It is quite expensive to re-render TinyMCE, resulting in a slightly
   * flicker every time, so we need to minimise the number of times we render
   * it. Now, for fields that already exist on the server, the id acts as a
   * perfectly fine key but for new fields they have no unique identifier.
   * So for the purposes here, we just generate a string that is very unlikely
   * to be the same for any two new fields and will only change when this
   * component is re-mounted and thus the TinyMCE of the rich text field will
   * have to be re-rendered anyway.
   */
  const key = React.useMemo(() => field.id ?? crypto.randomUUID(), [field.id]);

  const errorState = match<void, string | null>([
    [() => !_hasOptions, null],
    [() => field.options.length === 0, "One or more values are required."],
    [() => !field.optionsAreUnique, "All values must be unique."],
    [() => true, null],
  ])();

  /*
   * Have to use a variable here to make the suppression work. No idea why; may
   * well be a bug in Flow.
   */
  const fieldType = (field.type.charAt(0).toUpperCase() +
    field.type.slice(1)) as
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
    [
      () => field.type === "radio",
      () => ArrayUtils.head(field.selectedOptions ?? []).orElse(null),
    ],
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
    error: Boolean(errorState),
    helperText: errorState ?? "",
  };

  // Common onChange handler
  const handleChange = ({
    target: { value },
  }: {
    target: { value: unknown };
  }) => {
    field.setAttributesDirty(
      match<void, object>([
        [
          () => field.type === "radio",
          { selectedOptions: value ? [value] : [] },
        ],
        [() => field.type === "choice", { selectedOptions: value }],
        [
          () => field.type === "date",
          {
            content: value
              ? truncateIsoTimestamp(String(value), "date").orElse(
                  "NaN-NaN-NaN"
                )
              : null,
          },
        ],
        [() => true, { content: value }],
      ])()
    );
  };

  // Add properties based on field type
  props.onChange = handleChange;
  props.name = field.name;
  props.noValueLabel = "None";

  // Add options for choice/radio fields
  if (field.type === "radio" || field.type === "choice") {
    props.options = field.options ?? [];
    props.allowOptionDeletion = true;
    props.hideWhenDisabled = false;
    props.onOptionChange = (index: number, changedOption: Option) => {
      field.setAttributesDirty({
        options: ArrayUtils.splice(field.options, index, 1, changedOption),
      });
    };
    props.onOptionRemove = (index: number) => {
      field.setAttributesDirty({
        options: ArrayUtils.splice(field.options, index, 1),
      });
    };
  }

  // Add radio-specific props
  if (field.type === "radio") {
    props.allowRadioUnselection = true;
  }

  // Add attachment-specific props
  if (field.type === "attachment") {
    props.attachment = field.attachment;
    props.onAttachmentChange = (file: File | GalleryFile) =>
      field.setAttachment(file);
    props.disableFileUpload = true;
  }

  const custom = React.createElement(
    CustomField,
    props as React.ComponentProps<typeof CustomField>
  );

  return (
    <InputWrapper
      label={
        _hasOptions
          ? "Values"
          : isAttachment
          ? "Default Description"
          : "Default Value"
      }
      explanation={
        _hasOptions
          ? "The set of available options. Any selected values will be the default when creating samples."
          : null
      }
    >
      <>
        {custom}
        {hasOptions(field.fieldType) && editing && (
          <AdditionalOptionButton field={field} />
        )}
      </>
    </InputWrapper>
  );
}

export default observer(DefaultValueField);
