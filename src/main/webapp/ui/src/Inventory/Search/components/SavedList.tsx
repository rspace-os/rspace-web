import DeleteIcon from "@mui/icons-material/Delete";
import EditIcon from "@mui/icons-material/Edit";
import Alert from "@mui/material/Alert";
import Badge from "@mui/material/Badge";
import IconButton, { iconButtonClasses } from "@mui/material/IconButton";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../components/CustomTooltip";
import StyledMenu from "../../../components/StyledMenu";
import NameDialog from "../../../Inventory/Search/components/NameDialog";
import type { SavedItem } from "../../../Inventory/Search/components/SearchParameterControls";
import NavigateContext from "../../../stores/contexts/Navigate";
import BasketModel from "../../../stores/models/Basket";
import useStores from "../../../stores/use-stores";

export type ItemType = "searches" | "baskets";

export type SavedListArgs<T extends SavedItem> = {
  anchorEl: HTMLElement | null;
  itemType: ItemType;
  items: Array<T>;
  onSelect: (item: T | null) => void;
  isDisabled?: (item: T) => boolean;
};

function SavedList<T extends SavedItem>({
  anchorEl,
  itemType,
  items,
  onSelect,
  isDisabled,
}: SavedListArgs<T>): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore, peopleStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [index, setIndex] = useState(0);

  const leaveDeletedBasketListing = () => {
    if (peopleStore.currentUser) {
      const params = searchStore.search.fetcher.generateNewQuery({
        parentGlobalId: `BE${peopleStore.currentUser.workbenchId}`,
      });
      navigate(`/inventory/search?${params.toString()}`);
    }
  };

  const handleDelete = async (item: T) => {
    if (item instanceof BasketModel) {
      await searchStore.deleteBasket(item.id);
      // if we are on the deleted basket listing, navigate to bench
      if (searchStore.search.fetcher.parentGlobalId === item.globalId) leaveDeletedBasketListing();
      if (searchStore.savedBaskets.length === 0) onSelect(null);
    } else {
      searchStore.deleteSearch(item);
    }
  };

  return (
    <>
      <StyledMenu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => onSelect(null)}>
        {items.length > 0 ? (
          items.map((item, i) => (
            <MenuItem
              key={i}
              sx={{ minWidth: "300px", pr: "120px" }}
              onClick={() => onSelect(item)}
              disabled={Boolean(isDisabled?.(item))}
            >
              <ListItemText primary={item.name} />
              <ListItemSecondaryAction
                sx={(theme) => ({
                  [`& .${iconButtonClasses.root}`]: {
                    backgroundColor: "white",
                    padding: theme.spacing(0.5),
                    marginLeft: theme.spacing(1),
                    marginRight: "-4px !important",
                  },
                  [`&:hover .${iconButtonClasses.root}`]: {
                    backgroundColor: "rgba(0, 0, 0, 0.04)",
                  },
                })}
              >
                {itemType === "baskets" && (
                  <Badge sx={{ mx: 1 }} badgeContent={(item as BasketModel).itemCount || "0"} color="primary" />
                )}
                <CustomTooltip title={t("search.savedList.editName")}>
                  <IconButton
                    aria-label={t("search.savedList.editAria")}
                    onClick={(e) => {
                      e.stopPropagation();
                      setIndex(i);
                      setName(item.name);
                      setOpen(true);
                    }}
                  >
                    <EditIcon sx={{ color: "primary.main" }} />
                  </IconButton>
                </CustomTooltip>
                <CustomTooltip
                  title={
                    itemType === "searches"
                      ? t("search.savedList.deleteSavedSearch")
                      : t("search.savedList.deleteBasket")
                  }
                >
                  <IconButton
                    edge="end"
                    aria-label={t("search.savedList.deleteAria")}
                    onClick={(e) => {
                      e.stopPropagation();
                      void handleDelete(item);
                    }}
                  >
                    <DeleteIcon sx={{ color: "warningRed" }} />
                  </IconButton>
                </CustomTooltip>
              </ListItemSecondaryAction>
            </MenuItem>
          ))
        ) : (
          <Alert sx={{ width: "300px" }} severity="info" onClose={() => onSelect(null)}>
            {itemType === "searches" ? t("search.savedList.noSavedSearches") : t("search.savedList.noBaskets")}
          </Alert>
        )}
      </StyledMenu>
      <NameDialog
        open={open}
        setOpen={setOpen}
        name={name}
        setName={setName}
        index={index}
        existingNames={
          itemType === "searches"
            ? searchStore.savedSearches.map((s) => s.name)
            : searchStore.savedBaskets.map((s) => s.name)
        }
        onChange={() => {
          if (itemType === "searches") {
            searchStore.saveSearch(name, index);
          } else {
            void searchStore.savedBaskets[index].updateDetails({ name });
          }
        }}
      />
    </>
  );
}

export default observer(SavedList);
