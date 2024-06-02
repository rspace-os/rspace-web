//@flow

import React, { type Node, type ComponentType, useId } from "react";
import { observer } from "mobx-react-lite";
import FieldModel from "../../../stores/models/FieldModel";
import {
  FIELD_LABEL,
  fieldTypeToApiString,
  FieldTypes,
  type FieldType,
} from "../../../stores/models/FieldTypes";
import { match, toYesNo } from "../../../util/Util";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import { makeStyles } from "tss-react/mui";
import FieldTypeMenu from "./FieldTypeMenu";
import DefaultValueField from "./DefaultValueField";
import NameField from "./CustomFieldNameField";
import Typography from "@mui/material/Typography";
import RelativeBox from "../../../components/RelativeBox";
import RemoveButton from "../../../components/RemoveButton";
import RemoveMenu, {
  type DeleteOption,
} from "../../components/Fields/RemoveMenu";
import clsx from "clsx";
import Switch from "@mui/material/Switch";
import MoveButtons from "./MoveButtons";

const useStyles = makeStyles()((theme) => ({
  removeWrapper: {
    marginLeft: theme.spacing(0.75),
    marginBottom: theme.spacing(0.75),
  },
  row: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  column: {
    display: "flex",
    flexDirection: "column",
    alignItems: "flex-end",
  },
  noWrap: {
    whiteSpace: "nowrap",
  },
  fullWidth: {
    width: "100%",
  },
  leftSpaced: { marginLeft: theme.spacing(1.5) },
}));

const deleteOptions: Array<DeleteOption> = [
  { value: false, label: "Keep field in existing samples" },
  {
    value: true,
    label: "Remove field from existing samples",
  },
];

const FieldTypeLabel = ({ field }: { field: FieldModel }) => {
  const { classes } = useStyles();
  return (
    <Typography variant="subtitle1" className={classes.noWrap}>
      {FIELD_LABEL[field.fieldType]}
    </Typography>
  );
};

const FieldTypeSelector = observer(function FieldTypeSelector({
  field,
}: {
  field: FieldModel,
}) {
  return (
    <InputWrapper label="Type">
      <FieldTypeMenu
        fieldType={field.fieldType}
        onChange={(fieldType) => {
          field.setAttributesDirty({
            type: fieldTypeToApiString(fieldType),
            content: match<FieldType, string | number | Array<mixed>>([
              [(f) => f === FieldTypes.radio, []],
              [(f) => f === FieldTypes.choice, []],
              [(f) => f === FieldTypes.number, 0],
              [() => true, ""],
            ])(fieldType),
          });
        }}
      />
    </InputWrapper>
  );
});

const Mandatory = observer(function Mandatory({
  field,
  editing,
}: {
  field: FieldModel,
  editing: boolean,
}) {
  return (
    <InputWrapper label="Mandatory">
      {editing ? (
        <>
          <Switch
            checked={field.mandatory}
            onChange={({ target: { checked } }) => {
              field.setAttributesDirty({
                mandatory: checked,
              });
            }}
            edge="start"
          />
        </>
      ) : (
        toYesNo(field.mandatory)
      )}
    </InputWrapper>
  );
});

const DeletionRecap = ({ field }: { field: FieldModel }) => {
  return (
    <Box p={2}>
      <Typography variant="subtitle1">
        <strong>{field.name}</strong> {FIELD_LABEL[field.fieldType]} field will
        be deleted from this template.
      </Typography>
      <p>
        New samples will not include this field.{" "}
        {field.deleteFieldOnSampleUpdate
          ? "The field will also be deleted from existing samples made with this template after the samples are updated to the latest template version."
          : "The field will not be deleted from existing samples even if the samples are updated to the latest template version."}
      </p>
    </Box>
  );
};

type CustomFieldArgs = {|
  field: FieldModel,
  i: number,
  editable: boolean,
  onErrorStateChange: (boolean) => void,
  onRemove: (b?: boolean) => void,
  forceColumnLayout: boolean,
  onMove: (number) => void,
|};

function CustomField({
  field,
  i,
  editable,
  onErrorStateChange,
  onRemove,
  forceColumnLayout,
  onMove,
}: CustomFieldArgs): Node {
  const nameFieldId = useId();
  const { classes } = useStyles();

  return (
    <Grid item role="region" aria-labelledby={nameFieldId}>
      <div data-testid="TemplateField">
        <Paper variant="outlined">
          {field.deleteFieldRequest ? (
            <DeletionRecap field={field} />
          ) : (
            <RelativeBox p={2}>
              <div className={clsx(classes.fullWidth, classes.row)}>
                <NameField
                  field={field}
                  editing={editable}
                  onErrorStateChange={(value) => {
                    onErrorStateChange(value);
                  }}
                  id={nameFieldId}
                />
                <Box className={clsx(classes.leftSpaced, classes.column)}>
                  {editable && (
                    <div className={classes.removeWrapper}>
                      {field.initial ? (
                        <RemoveButton
                          onClick={() => onRemove()}
                          title="Delete new field"
                        />
                      ) : (
                        <RemoveMenu
                          deleteOptions={deleteOptions}
                          onClick={(b) => onRemove(b)}
                          tooltipTitle="Delete field"
                        />
                      )}
                    </div>
                  )}
                  {!field.initial && <FieldTypeLabel field={field} />}
                </Box>
              </div>
              <Box mt={4}>
                <Grid
                  container
                  direction={forceColumnLayout ? "column" : "row"}
                >
                  <Grid item lg={3}>
                    <Mandatory field={field} editing={editable} />
                  </Grid>
                  <Grid item lg={9}>
                    {field.initial && (
                      <Box mt={forceColumnLayout ? 3 : 0}>
                        <FieldTypeSelector field={field} />
                      </Box>
                    )}
                  </Grid>
                </Grid>
              </Box>
              <DefaultValueField field={field} editing={editable} />
              {editable && (
                <MoveButtons
                  index={i}
                  onClick={(newIndex) => onMove(newIndex)}
                />
              )}
            </RelativeBox>
          )}
        </Paper>
      </div>
    </Grid>
  );
}

export default (observer(CustomField): ComponentType<CustomFieldArgs>);
