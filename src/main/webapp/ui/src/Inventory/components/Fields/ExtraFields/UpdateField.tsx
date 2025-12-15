import React, { useState, useEffect } from "react";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import TextField from "@mui/material/TextField";
import Grid from "@mui/material/Grid";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Button from "@mui/material/Button";
import { match } from "../../../../util/Util";
import { type ExtraField } from "../../../../stores/definitions/ExtraField";
import { pick } from "../../../../util/unsafeUtils";
import FormField from "../../../../components/Inputs/FormField";

type UpdateFieldArgs = {
  extraField: ExtraField;
  index: number;
  record: InventoryRecord;
};

export default function UpdateField({
  extraField,
  index,
  record,
}: UpdateFieldArgs): React.ReactNode {
  const [fieldState, setFieldState] = useState({ name: "", type: "" });
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    if (extraField) {
      setFieldState({
        name: extraField.name,
        type: extraField.type,
      });
    }
  }, [extraField]);

  const canSubmit =
    !errorMessage &&
    fieldState.name !== "" &&
    (fieldState.name !== extraField.name ||
      fieldState.type !== extraField.type);

  const handleNameChange = ({
    target: { value },
  }: {
    target: { value: string };
  }) => {
    setFieldState({
      ...fieldState,
      name: value,
    });
    setErrorMessage(
      match<void, string | null>([
        [() => value === "", "Name should not be empty."],
        [
          () => value.length > 255,
          "Name must be no longer than 255 characters.",
        ],
        [
          () => {
            const unchangedName = value === extraField.name;
            return (
              extraField.owner.fieldNamesInUse.filter((n) => n === value)
                .length > Number(unchangedName)
            );
          },
          "You either already have a field with that name or that name is not permitted.",
        ],
        [() => true, null],
      ])()
    );
  };

  const handleTypeChange = ({
    target: { value },
  }: {
    target: { value: string };
  }) => {
    setFieldState({
      ...fieldState,
      type: value,
    });
  };

  const update = () => {
    record.updateExtraField(extraField.name, fieldState);
  };

  const discardChanges = () => {
    record.updateExtraField(
      extraField.name,
      pick("name", "type")(extraField) as {
        name: typeof extraField.name;
        type: typeof extraField.type;
      }
    );
  };

  return (
    <>
      {extraField && (
        <Grid
          container
          spacing={1}
          role="group"
          aria-label={
            extraField.id === null
              ? "New extra field"
              : `Editing extra field with name ${extraField.name}`
          }
        >
          <Grid item md={7} xs={12}>
            <FormField
              value={fieldState.name}
              label="Field name"
              renderInput={(props) => (
                <TextField
                  {...props}
                  variant="standard"
                  onChange={handleNameChange}
                />
              )}
              maxLength={255}
              error={Boolean(errorMessage)}
              helperText={errorMessage}
            />
          </Grid>
          <Grid item md={5} xs={12}>
            <FormField
              value={fieldState.type}
              label="Field type"
              disabled={Boolean(extraField.id)}
              renderInput={(props) => (
                <Select
                  {...props}
                  sx={{ mt: 3 }}
                  variant="standard"
                  onChange={handleTypeChange}
                >
                  <MenuItem value="Text">Text</MenuItem>
                  <MenuItem value="Number">Number</MenuItem>
                </Select>
              )}
            />
          </Grid>
          <Grid item md={12} xs={12}>
            <Button
              color="callToAction"
              disableElevation
              variant="contained"
              aria-label="Update field"
              onClick={update}
              disabled={!canSubmit}
              data-test-id={"ApplyOrUpdateButton"}
            >
              {extraField.initial ? "Apply" : "Update"}
            </Button>
            <Button
              style={{ marginLeft: "10px" }}
              disableElevation
              variant="text"
              aria-label="Cancel update"
              onClick={() =>
                extraField.initial
                  ? record.removeExtraField(null, index)
                  : discardChanges()
              }
              data-test-id={"DiscardOrCancelButton"}
            >
              {extraField.initial ? "Discard" : "Cancel"}
            </Button>
          </Grid>
        </Grid>
      )}
    </>
  );
}
