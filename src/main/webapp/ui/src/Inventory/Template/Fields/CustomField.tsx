import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Switch from "@mui/material/Switch";
import Typography from "@mui/material/Typography";
import type { TFunction } from "i18next";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import RemoveButton from "../../../components/RemoveButton";
import type FieldModel from "../../../stores/models/FieldModel";
import { FIELD_LABEL, type FieldType, FieldTypes, fieldTypeToApiString } from "../../../stores/models/FieldTypes";
import { match } from "../../../util/Util";
import RemoveMenu, { type DeleteOption } from "../../components/Fields/RemoveMenu";
import NameField from "./CustomFieldNameField";
import DefaultValueField from "./DefaultValueField";
import FieldTypeMenu from "./FieldTypeMenu";
import MoveButtons from "./MoveButtons";

function makeDeleteOptions(t: TFunction<"inventory">, recordType: string): Array<DeleteOption> {
  return [
    { value: false, label: t("fields.templateFields.customField.deleteOptions.keep", { recordType }) },
    { value: true, label: t("fields.templateFields.customField.deleteOptions.remove", { recordType }) },
  ];
}

const FieldTypeSelector = observer(({ field }: { field: FieldModel }) => {
  const { t } = useTranslation("inventory");
  return (
    <InputWrapper label={t("fields.templateFields.customField.type")}>
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

const Mandatory = observer(({ field, editing }: { field: FieldModel; editing: boolean }) => {
  const { t } = useTranslation(["inventory", "common"]);
  const mandatoryLabel = field.mandatory ? t("common:actions.yes") : t("common:actions.no");
  return (
    <InputWrapper label={t("fields.templateFields.customField.mandatory")}>
      {editing ? (
        <Switch
          checked={field.mandatory}
          onChange={({ target: { checked } }) => {
            field.setAttributesDirty({
              mandatory: checked,
            });
          }}
          edge="start"
        />
      ) : (
        mandatoryLabel
      )}
    </InputWrapper>
  );
});

type CustomFieldArgs = {
  field: FieldModel;
  i: number;
  editable: boolean;
  onErrorStateChange: (value: boolean) => void;
  onRemove: (b?: boolean) => void;
  forceColumnLayout: boolean;
  onMove: (index: number) => void;
  recordTypeName?: "sample" | "instrument";
};

function CustomField({
  field,
  i,
  editable,
  onErrorStateChange,
  onRemove,
  forceColumnLayout,
  onMove,
  recordTypeName = "sample",
}: CustomFieldArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const nameFieldId = useId();
  const recordType = t(`recordTypes.${recordTypeName}.lowerPlural`);

  return (
    <Grid role="region" aria-labelledby={nameFieldId}>
      <div data-testid="TemplateField">
        <Paper variant="outlined">
          {field.deleteFieldRequest ? (
            <Box sx={{ p: 2 }}>
              <Typography variant="subtitle1">
                <strong>{field.name}</strong>{" "}
                {t("template.fields.customField.deleteField", { fieldType: FIELD_LABEL[field.fieldType] })}
              </Typography>
              <p>
                {t("template.fields.customField.newSamplesExclusion", { recordType })}{" "}
                {field.deleteFieldOnSampleUpdate
                  ? t("template.fields.customField.deleteFieldOnUpdate", { recordType })
                  : t("template.fields.customField.deleteFieldOnUpdateNot", { recordType })}
              </p>
            </Box>
          ) : (
            <Box sx={{ position: "relative", p: 2 }}>
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
                          title={t("fields.templateFields.customField.deleteNewField")}
                        />
                      ) : (
                        <RemoveMenu
                          deleteOptions={makeDeleteOptions(t, recordType)}
                          onClick={(b) => onRemove(b)}
                          tooltipTitle={t("fields.templateFields.customField.deleteField", { fieldName: field.name })}
                        />
                      )}
                    </Box>
                  )}
                  {!field.initial && (
                    <Typography variant="subtitle1" sx={{ whiteSpace: "nowrap" }}>
                      {FIELD_LABEL[field.fieldType]}
                    </Typography>
                  )}
                </Box>
              </Box>
              <Box sx={{ mt: 4 }}>
                <Grid container sx={{ flexDirection: forceColumnLayout ? "column" : "row" }}>
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
              {editable && <MoveButtons index={i} onClick={(newIndex) => onMove(newIndex)} />}
            </Box>
          )}
        </Paper>
      </div>
    </Grid>
  );
}

export default observer(CustomField);
