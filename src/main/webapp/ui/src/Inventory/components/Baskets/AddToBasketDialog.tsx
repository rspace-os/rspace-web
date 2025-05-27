import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextDialog from "../ContextMenu/ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import Alert from "@mui/material/Alert";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select, { SelectChangeEvent } from "@mui/material/Select";
import Grid from "@mui/material/Grid";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import useStores from "../../../stores/use-stores";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { NEW_BASKET } from "../../../stores/models/Basket";
import { type Basket } from "../../../stores/definitions/Basket";
import TextField from "@mui/material/TextField";
import {
  type GlobalId,
  getSavedGlobalId,
} from "../../../stores/definitions/BaseRecord";

type AddToBasketDialogArgs = {
  openAddToBasketDialog: boolean;
  setOpenAddToBasketDialog: (newOpen: boolean) => void;
  /* n/a for non context menus cases */
  closeMenu?: () => void;
  selectedResults: Array<InventoryRecord>;
};

function AddToBasketDialog({
  openAddToBasketDialog,
  setOpenAddToBasketDialog,
  selectedResults,
  closeMenu,
}: AddToBasketDialogArgs): React.ReactNode {
  const { searchStore } = useStores();

  const [targetBaskets, setTargetBaskets] = useState<Array<Basket>>([
    NEW_BASKET,
  ]);
  const [targetBasket, setTargetBasket] = useState<Basket>(NEW_BASKET);
  const [newBasketName, setNewBasketName] = useState<string>("");
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    void searchStore.getBaskets().then(() => {
      setTargetBaskets([NEW_BASKET, ...searchStore.savedBaskets]);
    });
  }, []);

  const noDuplicates = (): boolean =>
    !searchStore.savedBaskets.map((b) => b.name).includes(newBasketName);
  const validLength = (): boolean => newBasketName.length <= 32;
  const validNewName = (): boolean => noDuplicates() && validLength();

  useEffect(() => {
    setError(!validNewName());
  }, [newBasketName]);

  const itemIds: Array<GlobalId> = selectedResults.map((r) =>
    getSavedGlobalId(r)
  );
  const selectedCount = selectedResults.length;
  const itemString = selectedCount > 1 ? "Items" : "Item";

  const onAdd = () =>
    targetBasket.id
      ? targetBasket.addItems(itemIds)
      : // basket creation includes items addition
        searchStore.createBasket(newBasketName, itemIds);

  const handleClose = () => {
    setOpenAddToBasketDialog(false);
    if (typeof closeMenu === "function") closeMenu();
  };

  const onSubmitHandler = () => {
    void onAdd();
    handleClose();
  };

  const basketSelectorLabel = React.useId();

  return (
    <ContextDialog
      open={openAddToBasketDialog}
      onClose={handleClose}
      maxWidth="xs"
      fullWidth
    >
      <DialogTitle>{`Adding ${itemString} to Basket`}</DialogTitle>
      <DialogContent>
        <Grid container direction="column" spacing={2}>
          <Grid item>
            <FormControl component="fieldset" fullWidth sx={{ mt: 1 }}>
              <InputLabel id={basketSelectorLabel}>Choose a Basket</InputLabel>
              <Select
                labelId={basketSelectorLabel}
                value={`${targetBasket.id ?? undefined}`}
                onChange={(event: SelectChangeEvent<string>) => {
                  const selectedBasket = targetBaskets.find(
                    (b) => `${b.id}` === event.target.value
                  );
                  if (selectedBasket) {
                    setTargetBasket(selectedBasket);
                  }
                }}
                label="Choose a Basket"
                size="small"
              >
                {targetBaskets.map((basket) => (
                  <MenuItem key={basket.id} value={`${basket.id}`}>
                    {basket.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          {!targetBasket.id && (
            <Grid item>
              <FormControl component="fieldset" fullWidth>
                <TextField
                  InputLabelProps={{
                    shrink: true,
                  }}
                  size="small"
                  label="Custom Name (optional)"
                  fullWidth
                  disabled={Boolean(targetBasket.id)}
                  error={error}
                  id="basketNameField" // for a11y
                  value={newBasketName}
                  placeholder="Enter custom name for new Basket"
                  helperText={
                    error && !noDuplicates()
                      ? "This name is already used for another Basket."
                      : error && !validLength()
                      ? "The name should be no longer than 32 characters."
                      : "You can assign a unique name to the new Basket."
                  }
                  onChange={({ target }) => setNewBasketName(target.value)}
                  variant="standard"
                />
              </FormControl>
            </Grid>
          )}
          <Grid item>
            <Alert severity="info">
              {`This action will not change the location of the ${itemString}.`}
            </Alert>
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          Cancel
        </Button>
        <SubmitSpinner
          onClick={onSubmitHandler}
          disabled={error}
          loading={targetBasket.loading}
          label="Add to Basket"
        />
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(AddToBasketDialog);
