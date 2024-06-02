//@flow

import FieldModel from "../../../stores/models/FieldModel";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
} from "react";
import Typography from "@mui/material/Typography";
import { match } from "../../../util/Util";

type NameFieldArgs = {|
  field: FieldModel,
  editing: boolean,
  onErrorStateChange: (boolean) => void,
  id: string,
|};

function NameField({
  field,
  editing,
  onErrorStateChange,
  id,
}: NameFieldArgs): Node {
  const [duplicateFieldError, setDuplicateFieldError] = useState(false);

  useEffect(() => {
    setDuplicateFieldError(
      field.owner.fieldNamesInUse.filter((n) => n === field.name).length > 1
    );
  }, [field.owner.fieldNamesInUse]);

  const emptyName = field.name === "";
  const tooLongName = field.name.length > 50;

  const helperText = match<void, string>([
    [() => emptyName, "Name cannot be empty"],
    [
      () => duplicateFieldError,
      "You either already have a field with that name or that name is not permitted",
    ],
    [() => tooLongName, "Name cannot be longer than 50 characters"],
    [() => true, ""],
  ])();

  return editing ? (
    <InputWrapper
      label="Name"
      error={emptyName || tooLongName || duplicateFieldError}
      helperText={helperText}
      maxLength={50}
      value={field.name}
    >
      <TextField
        variant="standard"
        value={field.name}
        name="name"
        disabled={!editing}
        onChange={({ target: { value } }) => {
          field.setAttributesDirty({ name: value });
          onErrorStateChange(value === "" || value.length > 50);
        }}
        id={id}
      />
    </InputWrapper>
  ) : (
    <Typography
      variant="h6"
      component="h6"
      style={{ overflowWrap: "anywhere" }}
      id={id}
    >
      {field.name}
    </Typography>
  );
}

export default (observer(NameField): ComponentType<NameFieldArgs>);
