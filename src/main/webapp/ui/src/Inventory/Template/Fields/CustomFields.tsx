import React, { type ReactNode } from "react";
import { observer } from "mobx-react-lite";
import FieldModel from "../../../stores/models/FieldModel";
import useStores from "../../../stores/use-stores";
import NewField from "./NewField";
import Grid from "@mui/material/Grid";
import { makeStyles } from "tss-react/mui";
import NoValue from "../../../components/NoValue";
import TemplateModel from "../../../stores/models/TemplateModel";
import CustomField from "./CustomField";
import * as ArrayUtils from "../../../util/ArrayUtils";

const useStyles = makeStyles()((theme) => ({
  textSpacer: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(1),
  },
}));

type FieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

function Fields({ onErrorStateChange }: FieldsArgs): ReactNode {
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel))
    throw new Error("ActiveResult must be a Template");
  const { classes } = useStyles();

  const TemplateFields = observer(({ editable }: { editable: boolean }) => {
    const removeCustomField =
      (field: FieldModel) =>
      (deleteFromSamples = false) => {
        activeResult.removeCustomField(
          field.id,
          activeResult.fields.indexOf(field),
          deleteFromSamples
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
          <NoValue label="No more fields" />
        </div>
      )}
      {editable && <NewField record={activeResult} />}
    </>
  );
}

export default observer(Fields);
