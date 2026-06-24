import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "@/components/ValidatingSubmitButton";
import Result from "../../util/result";

export type AddReagentDialogArgs = {
  open: boolean;
  onClose: () => void;
  onAddReagent: (smilesString: string, name: string) => void;
};

const StoichiometryAddReagentDialog = ({ open, onClose, onAddReagent }: AddReagentDialogArgs) => {
  const { t } = useTranslation("common");
  const [smilesString, setSmilesString] = useState<string>("");
  const [name, setName] = useState<string>("");

  const validate = () => {
    return Result.all(
      smilesString.length === 0 ? IsInvalid(t("stoichiometry.addReagent.validation.smilesRequired")) : IsValid(),
      name.length === 0 ? IsInvalid(t("stoichiometry.addReagent.validation.nameRequired")) : IsValid(),
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
        <DialogTitle>{t("stoichiometry.addReagent.title")}</DialogTitle>
        <DialogContent>
          <FormControl component="fieldset" sx={{ width: "100%", mt: 1 }}>
            <Stack spacing={2}>
              <TextField
                name="Name"
                label={t("stoichiometry.addReagent.name")}
                value={name}
                onChange={(e) => setName(e.target.value)}
                variant="outlined"
                size="small"
                fullWidth
                sx={{ mb: 1 }}
              />
              <TextField
                name="SMILES String"
                label={t("stoichiometry.addReagent.smilesString")}
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
          <Button onClick={handleClose}>{t("actions.cancel")}</Button>
          <ValidatingSubmitButton onClick={onSubmitHandler} validationResult={validate()} loading={false}>
            {t("stoichiometry.addReagent.addChemical")}
          </ValidatingSubmitButton>
        </DialogActions>
      </form>
    </Dialog>
  );
};

export default StoichiometryAddReagentDialog;
