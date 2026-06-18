import React, { type ReactNode } from "react";
import { observer } from "mobx-react-lite";
import FieldModel, { type FieldModelAttrs } from "../../../stores/models/FieldModel";
import useStores from "../../../stores/use-stores";
import Grid from "@mui/material/Grid";
import { makeStyles } from "tss-react/mui";
import NoValue from "../../../components/NoValue";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import CustomField from "../../Template/Fields/CustomField";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Button from "@mui/material/Button";
import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";

const useStyles = makeStyles()((theme) => ({
  textSpacer: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(1),
  },
}));

const EMPTY_FIELD: FieldModelAttrs = {
  name: "",
  type: "text",
  editing: true,
  initial: true,
  selectedOptions: [],
  columnIndex: null,
  attachment: null,
  mandatory: false,
};

type CustomFieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

function CustomFields({ onErrorStateChange }: CustomFieldsArgs): ReactNode {
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");
  const { classes } = useStyles();

  const TemplateFields = observer(({ editable }: { editable: boolean }) => {
    const removeCustomField =
      (field: FieldModel) =>
      (_b = false) => {
        activeResult.removeCustomField(
          field.id,
          activeResult.fields.indexOf(field)
        );
      };

    return (
      <Grid container direction="column" spacing={2}>
        {ArrayUtils.filterClass(FieldModel, activeResult.fields).map(
          (field: FieldModel, i: number) => (
            <CustomField
              field={field}
              i={i}
              key={i}
              editable={editable}
              onErrorStateChange={(value) =>
                onErrorStateChange(field.globalId ?? "NEW", value)
              }
              onRemove={(b) => removeCustomField(field)(b)}
              forceColumnLayout={!uiStore.isLarge}
              onMove={(index) => activeResult.moveField(field, index)}
            />
          )
        )}
      </Grid>
    );
  });

  const fields = activeResult.fields;
  const editable = activeResult.isFieldEditable("fields");
  return (
    <>
      {fields.length > 0 ? (
        <TemplateFields editable={editable} />
      ) : (
        <div className={classes.textSpacer}>
          <NoValue label="No custom fields" />
        </div>
      )}
      {editable && (
        <FormControl inline>
          <CustomTooltip title="Add field">
            <Button
              color="primary"
              startIcon={<AddOutlinedIcon />}
              variant="outlined"
              onClick={() => activeResult.addField(EMPTY_FIELD)}
            >
              Add new field
            </Button>
          </CustomTooltip>
        </FormControl>
      )}
    </>
  );
}

export default observer(CustomFields);
