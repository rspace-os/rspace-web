import React, { useContext, useState } from "react";
import { observer } from "mobx-react-lite";
import Badge from "@mui/material/Badge";
import ListItemText from "@mui/material/ListItemText";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import useStores from "../../../stores/use-stores";
import NavigateContext from "../../../stores/contexts/Navigate";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import IconButton from "@mui/material/IconButton";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import { withStyles } from "Styles";
import NameDialog from "../../../Inventory/Search/components/NameDialog";
import { type SavedItem } from "../../../Inventory/Search/components/SearchParameterControls";
import BasketModel from "../../../stores/models/Basket";
import CustomTooltip from "../../../components/CustomTooltip";
import Alert from "@mui/material/Alert";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()((theme) => ({
  itemWrapper: {
    minWidth: "300px",
    paddingRight: "120px",
  },
  alert: {
    width: "300px",
  },
  sideSpaced: {
    margin: theme.spacing(0, 1),
  },
}));

export type ItemType = "searches" | "baskets";

export type SavedListArgs<T extends SavedItem> = {
  anchorEl: HTMLElement | null;
  itemType: ItemType;
  items: Array<T>;
  onSelect: (item: T | null) => void;
  isDisabled?: (item: T) => boolean;
};

const DeleteSavedItemIcon = withStyles<
  React.ComponentProps<typeof DeleteIcon>,
  { root: string }
>((theme) => ({
  root: {
    color: theme.palette.warningRed,
  },
}))(DeleteIcon);

const EditSavedItemIcon = withStyles<
  React.ComponentProps<typeof EditIcon>,
  { root: string }
>((theme) => ({
  root: {
    color: theme.palette.primary.main,
  },
}))(EditIcon);

const Action = withStyles<
  React.ComponentProps<typeof ListItemSecondaryAction>,
  { root: string }
>((theme) => ({
  root: {
    "& .MuiIconButton-root": {
      backgroundColor: "white",
      padding: theme.spacing(0.5),
      marginLeft: theme.spacing(1),
      marginRight: "-4px !important",
    },
    "&:hover .MuiIconButton-root": {
      backgroundColor: "rgba(0, 0, 0, 0.04)",
    },
  },
}))(ListItemSecondaryAction);

const helpText: Record<ItemType, string> = {
  baskets: `There are no Baskets yet. To create one: select some results and then 'Add to Basket'.`,
  searches: `There are no Saved Searches yet.`,
};

function SavedList<T extends SavedItem>({
  anchorEl,
  itemType,
  items,
  onSelect,
  isDisabled,
}: SavedListArgs<T>): React.ReactNode {
  const { classes } = useStyles();
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
      if (searchStore.search.fetcher.parentGlobalId === item.globalId)
        leaveDeletedBasketListing();
      if (searchStore.savedBaskets.length === 0) onSelect(null);
    } else {
      searchStore.deleteSearch(item);
    }
  };

  return (
    <>
      <StyledMenu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => onSelect(null)}
      >
        {items.length > 0 ? (
          items.map((item, i) => (
            <StyledMenuItem
              key={i}
              className={classes.itemWrapper}
              onClick={() => onSelect(item)}
              disabled={Boolean(isDisabled?.(item))}
            >
              <ListItemText primary={item.name} />
              <Action>
                {itemType === "baskets" && (
                  <Badge
                    className={classes.sideSpaced}
                    badgeContent={(item as BasketModel).itemCount || "0"}
                    color="primary"
                  />
                )}
                <CustomTooltip title="Edit name">
                  <IconButton
                    aria-label="edit saved item"
                    onClick={(e) => {
                      e.stopPropagation();
                      setIndex(i);
                      setName(item.name);
                      setOpen(true);
                    }}
                  >
                    <EditSavedItemIcon />
                  </IconButton>
                </CustomTooltip>
                <CustomTooltip
                  title={`Delete ${
                    itemType === "searches" ? "Saved Search" : "Basket"
                  }`}
                >
                  <IconButton
                    edge="end"
                    aria-label="delete saved item"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDelete(item);
                    }}
                  >
                    <DeleteSavedItemIcon />
                  </IconButton>
                </CustomTooltip>
              </Action>
            </StyledMenuItem>
          ))
        ) : (
          <Alert
            className={classes.alert}
            severity="info"
            onClose={() => onSelect(null)}
          >
            {helpText[itemType]}
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
