import React, { useState, useEffect } from "react";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import InputAdornment from "@mui/material/InputAdornment";
import StringField from "../../../components/Inputs/StringField";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";

/*
 * A generic dialog for renaming various records in the Inventory interface.
 */

export type NameDialogArgs = {
  open: boolean;
  setOpen: (newOpen: boolean) => void;

  /*
   * This dialog does not maintain its own state. The textfield within derives
   * its current state from this `name` prop and when edits are made the new
   * value will be passed to the `setName` prop. Parent components should
   * maintain this state, almost certainly through the use of a `useState`
   * variable, and pass the relevant values. It is not clear why this component
   * does not maintain its own state and instead violates the principle of
   * information hiding.
   */
  name: string;
  setName: (newName: string) => void;

  /*
   * If naming a new record then `index` should be undefined. If renaming an
   * existing record then `index` should be the index of the existing record
   * into the `existingNames` array so that the existing name can be removed
   * and wont trigger an error.
   */
  index?: number;
  existingNames: Array<string>;

  /*
   * When the user closes the dialog by submitting the change, this event
   * handler is called. It does not pass the new name, as the parent component
   * already has it as `setName` will have been called with the submitted value
   * when the last edit was made.
   */
  onChange: () => void;
};

const NameDialog = ({
  open,
  setOpen,
  name,
  setName,
  index,
  existingNames,
  onChange,
}: NameDialogArgs): React.ReactNode => {
  const [error, setError] = useState<boolean>(false);

  const noDuplicates = (): boolean => {
    const names = new Set(existingNames);

    // if renaming, then remove the current name as a no-op is allowed
    if (typeof index === "number") {
      names.delete(existingNames[index]);
    }

    return !names.has(name);
  };

  const validLength = (): boolean => name.length > 0 && name.length <= 32;
  const validName = (): boolean => noDuplicates() && validLength();

  useEffect(() => {
    setError(!validName());
  }, [name]);

  const errorMessage: string = !validLength()
    ? "Please enter minimum 1 and maximum 32 characters."
    : !noDuplicates()
    ? "This name is already taken. Please modify it."
    : "Please enter a unique name, no longer than 32 characters.";

  const onSubmitHandler = () => {
    onChange();
    setOpen(false);
  };

  return (
    <Dialog open={open} onClose={() => setOpen(false)}>
      <DialogContent>
        <FormControl component="fieldset">
          <StringField
            name="item name"
            autoFocus
            value={name}
            onChange={({ target }) => setName(target.value)}
            error={error}
            variant="outlined"
            size="small"
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">Name</InputAdornment>
              ),
            }}
            onFocus={({ target }) => target.select()}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !error) {
                onSubmitHandler();
                e.preventDefault();
              }
            }}
          />
          <FormHelperText error={error}>{errorMessage}</FormHelperText>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => setOpen(false)}>Cancel</Button>
        <SubmitSpinner
          onClick={onSubmitHandler}
          disabled={error}
          loading={false}
          label="Save"
        />
      </DialogActions>
    </Dialog>
  );
};

export default NameDialog;
