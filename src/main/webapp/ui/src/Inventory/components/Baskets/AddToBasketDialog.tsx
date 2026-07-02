import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import { type GlobalId, getSavedGlobalId } from "../../../stores/definitions/BaseRecord";
import type { Basket } from "../../../stores/definitions/Basket";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { NEW_BASKET } from "../../../stores/models/Basket";
import useStores from "../../../stores/use-stores";
import ContextDialog from "../ContextMenu/ContextDialog";

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
  const { t } = useTranslation(["inventory", "common"]);

  const [targetBaskets, setTargetBaskets] = useState<Array<Basket>>([NEW_BASKET]);
  const [targetBasket, setTargetBasket] = useState<Basket>(NEW_BASKET);
  const [newBasketName, setNewBasketName] = useState<string>("");
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    void searchStore.getBaskets().then(() => {
      setTargetBaskets([NEW_BASKET, ...searchStore.savedBaskets]);
    });
  }, []);

  const noDuplicates = (): boolean => !searchStore.savedBaskets.map((b) => b.name).includes(newBasketName);
  const validLength = (): boolean => newBasketName.length <= 32;
  const validNewName = (): boolean => noDuplicates() && validLength();

  useEffect(() => {
    setError(!validNewName());
  }, [newBasketName]);

  const itemIds: Array<GlobalId> = selectedResults.map((r) => getSavedGlobalId(r));
  const selectedCount = selectedResults.length;
  const itemString = t("baskets.addDialog.item", { count: selectedCount });

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
    <ContextDialog open={openAddToBasketDialog} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t("baskets.addDialog.title", { count: selectedCount })}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <FormControl component="fieldset" fullWidth sx={{ mt: 1 }}>
            <InputLabel id={basketSelectorLabel}>{t("baskets.addDialog.chooseBasket")}</InputLabel>
            <Select
              labelId={basketSelectorLabel}
              value={`${targetBasket.id ?? undefined}`}
              onChange={(event: SelectChangeEvent<string>) => {
                const selectedBasket = targetBaskets.find((b) => `${b.id}` === event.target.value);
                if (selectedBasket) {
                  setTargetBasket(selectedBasket);
                }
              }}
              label={t("baskets.addDialog.chooseBasket")}
              size="small"
            >
              {targetBaskets.map((basket) => (
                <MenuItem key={basket.id} value={`${basket.id}`}>
                  {basket.name}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {!targetBasket.id && (
            <FormControl component="fieldset" fullWidth>
              <TextField
                size="small"
                label={t("baskets.addDialog.customName")}
                fullWidth
                disabled={Boolean(targetBasket.id)}
                error={error}
                // for a11y
                id="basketNameField"
                value={newBasketName}
                placeholder={t("baskets.addDialog.customNamePlaceholder")}
                helperText={
                  error && !noDuplicates()
                    ? t("baskets.addDialog.duplicateName")
                    : error && !validLength()
                      ? t("baskets.addDialog.nameTooLong")
                      : t("baskets.addDialog.customNameHelper")
                }
                onChange={({ target }) => setNewBasketName(target.value)}
                variant="standard"
                slotProps={{
                  inputLabel: {
                    shrink: true,
                  },
                }}
              />
            </FormControl>
          )}
          <Alert severity="info">{t("baskets.addDialog.locationUnchanged", { itemString })}</Alert>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={false}>
          {t("common:actions.cancel")}
        </Button>
        <SubmitSpinner
          onClick={onSubmitHandler}
          disabled={error}
          loading={targetBasket.loading}
          label={t("baskets.addDialog.addButton")}
        />
      </DialogActions>
    </ContextDialog>
  );
}

export default observer(AddToBasketDialog);
