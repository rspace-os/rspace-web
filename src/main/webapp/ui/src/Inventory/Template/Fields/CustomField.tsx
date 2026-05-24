import React, { useId } from "react";
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
import FieldTypeMenu from "./FieldTypeMenu";
import DefaultValueField from "./DefaultValueField";
import NameField from "./CustomFieldNameField";
import Typography from "@mui/material/Typography";
import RelativeBox from "../../../components/RelativeBox";
import RemoveButton from "../../../components/RemoveButton";
import RemoveMenu, {
  type DeleteOption,
} from "../../components/Fields/RemoveMenu";
import Switch from "@mui/material/Switch";
import MoveButtons from "./MoveButtons";

const deleteOptions: Array<DeleteOption> = [
  { value: false, label: "Keep field in existing samples" },
  {
    value: true,
    label: "Remove field from existing samples",
  },
];

const FieldTypeLabel = ({ field }: { field: FieldModel }) => {
  return (
    <Typography variant="subtitle1" sx={{ whiteSpace: "nowrap" }}>
      {FIELD_LABEL[field.fieldType]}
    </Typography>
  );
};

const FieldTypeSelector = observer(({ field }: { field: FieldModel }) => {
  return (
    <InputWrapper label="Type">
      <FieldTypeMenu
        fieldType={field.fieldType}
        onChange={(fieldType) => {
          field.setAttributesDirty({
            type: fieldTypeToApiString(fieldType),
            content: match<FieldType, string | number | Array<unknown>>([
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

const Mandatory = observer(
  ({ field, editing }: { field: FieldModel; editing: boolean }) => {
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
  },
);

const DeletionRecap = ({ field }: { field: FieldModel }) => {
  return (
    <Box sx={{ p: 2 }}>
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

type CustomFieldArgs = {
  field: FieldModel;
  i: number;
  editable: boolean;
  onErrorStateChange: (value: boolean) => void;
  onRemove: (b?: boolean) => void;
  forceColumnLayout: boolean;
  onMove: (index: number) => void;
};

function CustomField({
  field,
  i,
  editable,
  onErrorStateChange,
  onRemove,
  forceColumnLayout,
  onMove,
}: CustomFieldArgs): React.ReactNode {
  const nameFieldId = useId();

  return (
    <Grid role="region" aria-labelledby={nameFieldId}>
      <div data-testid="TemplateField">
        <Paper variant="outlined">
          {field.deleteFieldRequest ? (
            <DeletionRecap field={field} />
          ) : (
            <RelativeBox sx={{ p: 2 }}>
              <Box
                sx={{
                  width: "100%",
                  display: "flex",
                  flexDirection: "row",
                  justifyContent: "space-between",
                }}
              >
                <NameField
                  field={field}
                  editing={editable}
                  onErrorStateChange={(value) => {
                    onErrorStateChange(value);
                  }}
                  id={nameFieldId}
                />
                <Box
                  sx={{
                    ml: 1.5,
                    display: "flex",
                    flexDirection: "column",
                    alignItems: "flex-end",
                  }}
                >
                  {editable && (
                    <Box sx={{ ml: 0.75, mb: 0.75 }}>
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
                    </Box>
                  )}
                  {!field.initial && <FieldTypeLabel field={field} />}
                </Box>
              </Box>
              <Box sx={{ mt: 4 }}>
                <Grid
                  container
                  sx={{ flexDirection: forceColumnLayout ? "column" : "row" }}
                >
                  <Grid
                    size={{
                      lg: 3,
                    }}
                  >
                    <Mandatory field={field} editing={editable} />
                  </Grid>
                  <Grid
                    size={{
                      lg: 9,
                    }}
                  >
                    {field.initial && (
                      <Box sx={{ mt: forceColumnLayout ? 3 : 0 }}>
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

export default observer(CustomField);
