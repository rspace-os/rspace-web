import React, { useState } from "react";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import TextField from "@mui/material/TextField";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "@/components/ValidatingSubmitButton";
import Result from "../../util/result";
import Stack from "@mui/material/Stack";

export type AddReagentDialogArgs = {
  open: boolean;
  onClose: () => void;
  onAddReagent: (smilesString: string, name: string) => void;
};

const StoichiometryAddReagentDialog = ({
  open,
  onClose,
  onAddReagent,
}: AddReagentDialogArgs): React.ReactNode => {
  const [smilesString, setSmilesString] = useState<string>("");
  const [name, setName] = useState<string>("");

  const validate = () => {
    return Result.all(
      smilesString.length === 0
        ? IsInvalid("SMILES string is required")
        : IsValid(),
      name.length === 0 ? IsInvalid("Name is required") : IsValid(),
    ).map(() => null);
  };

  const handleClose = () => {
    setSmilesString("");
    setName("");
    onClose();
  };

  const onSubmitHandler = (e: React.FormEvent) => {
    e.preventDefault();
    onAddReagent(smilesString, name.trim());
    handleClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <form onSubmit={onSubmitHandler}>
        <DialogTitle>Add New Reagent</DialogTitle>
        <DialogContent>
          <FormControl component="fieldset" sx={{ width: "100%", mt: 1 }}>
            <Stack spacing={2}>
              <TextField
                name="Name"
                label="Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                variant="outlined"
                size="small"
                fullWidth
                sx={{ mb: 1 }}
              />
              <TextField
                name="SMILES String"
                label="SMILES String"
                value={smilesString}
                onChange={(e) => setSmilesString(e.target.value)}
                variant="outlined"
                size="small"
                fullWidth
                onFocus={(e) => e.target.select()}
                sx={{ mb: 1 }}
              />
            </Stack>
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

export default StoichiometryAddReagentDialog;
