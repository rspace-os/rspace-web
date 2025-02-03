//@flow

import React, { type Node, type ComponentType } from "react";
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
import { truncateIsoTimestamp } from "../../../util/conversions";

const useStyles = makeStyles()((theme) => ({
  buttonWrapper: {
    display: "inline",
    marginTop: theme.spacing(1),
  },
  bottomSpaced: {
    marginBottom: theme.spacing(4),
  },
}));

const AdditionalOptionButton = ({ field }: { field: FieldModel }): Node => {
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

type DefaultValueFieldArgs = {|
  field: FieldModel,
  editing: boolean,
|};

function DefaultValueField({ field, editing }: DefaultValueFieldArgs): Node {
  const _hasOptions = hasOptions(field.fieldType);
  const isAttachment = field.type === "Attachment";

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
  const key = React.useMemo(() => field.id ?? crypto.randomUUID(), []);

  const errorState = match<void, ?string>([
    [() => !_hasOptions, null],
    [() => field.options.length === 0, "One or more values are required."],
    [() => !field.optionsAreUnique, "All values must be unique."],
    [() => true, null],
  ])();

  /*
   * Have to use a variable here to make the suppression work. No idea why; may
   * well be a bug in Flow.
   */
  const custom = (
    // $FlowExpectedError[incompatible-type]
    <CustomField
      key={key}
      name={field.name}
      value={match<void, () => mixed>([
        [
          () => field.type === "radio",
          () => ArrayUtils.head(field.selectedOptions ?? []).orElse(null),
        ],
        [() => field.type === "choice", () => field.selectedOptions ?? []],
        [() => true, () => field.content],
      ])()()}
      type={field.type.replace(/(^.)/, (m) => m.toUpperCase())}
      disabled={!editing}
      options={field.options ?? []}
      attachment={field.attachment}
      onAttachmentChange={(file) => field.setAttachment(file)}
      allowOptionDeletion
      allowRadioUnselection
      // $FlowExpectedError[prop-missing] File field is not used, so target will always be there
      onChange={({ target: { value } }) => {
        field.setAttributesDirty(
          // $FlowExpectedError[incompatible-call]
          match<void, mixed>([
            [
              () => field.type === "radio",
              { selectedOptions: value ? [value] : [] },
            ],
            [() => field.type === "choice", { selectedOptions: value }],
            [
              () => field.type === "date",
              {
                content: value
                  ? // $FlowExpectedError[incompatible-call]
                    truncateIsoTimestamp(value, "date").orElse("NaN-NaN-NaN")
                  : null,
              },
            ],
            [() => true, { content: value }],
          ])()
        );
      }}
      onOptionChange={(index, changedOption) => {
        field.setAttributesDirty({
          options: ArrayUtils.splice(field.options, index, 1, changedOption),
        });
      }}
      onOptionRemove={(index) => {
        field.setAttributesDirty({
          options: ArrayUtils.splice(field.options, index, 1),
        });
      }}
      hideWhenDisabled={false}
      error={Boolean(errorState)}
      helperText={errorState ?? ""}
      disableFileUpload
      noValueLabel="None"
    />
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

export default (observer(
  DefaultValueField
): ComponentType<DefaultValueFieldArgs>);
