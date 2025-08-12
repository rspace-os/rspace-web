import React, { useState, useEffect } from "react";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormHelperText from "@mui/material/FormHelperText";
import TextField from "@mui/material/TextField";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "@/components/ValidatingSubmitButton";

export type AddReagentDialogArgs = {
  open: boolean;
  onClose: () => void;
  onAddReagent: (smilesString: string, name: string | null) => void;
};

const AddReagentDialog = ({
  open,
  onClose,
  onAddReagent,
}: AddReagentDialogArgs): React.ReactNode => {
  const [smilesString, setSmilesString] = useState<string>("");
  const [name, setName] = useState<string>("");

  const validate = () => {
    if (smilesString.length === 0) return IsInvalid("Invalid SMILES");
    return IsValid();
  };

  const handleClose = () => {
    setSmilesString("");
    setName("");
    onClose();
  };

  const onSubmitHandler = (e: React.FormEvent) => {
    e.preventDefault();
    onAddReagent(smilesString, name.length === 0 ? null : name);
    handleClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={onSubmitHandler}>
        <DialogTitle>Add New Reagent</DialogTitle>
        <DialogContent>
          <FormControl component="fieldset" sx={{ width: "100%", mt: 1 }}>
            <TextField
              name="SMILES String"
              label="SMILES String"
              autoFocus
              value={smilesString}
              onChange={(e) => setSmilesString(e.target.value)}
              variant="outlined"
              size="small"
              fullWidth
              onFocus={(e) => e.target.select()}
              sx={{ mb: 1 }}
            />
            <FormHelperText sx={{ mb: 2 }}>
              Enter the SMILES string for the chemical compound
            </FormHelperText>

            <TextField
              name="Name (Optional)"
              label="Name (Optional)"
              value={name}
              onChange={(e) => setName(e.target.value)}
              variant="outlined"
              size="small"
              fullWidth
              sx={{ mb: 1 }}
            />
            <FormHelperText>
              Optional: Provide a custom name for the reagent (will be
              auto-generated if left empty)
            </FormHelperText>
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleClose}>Cancel</Button>
          <ValidatingSubmitButton
            onClick={onSubmitHandler}
            validationResult={validate()}
            loading={false}
          >
            Add Reagent
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default AddReagentDialog;
