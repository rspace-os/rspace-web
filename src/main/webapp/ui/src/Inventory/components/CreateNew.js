// @flow
import AddIcon from "@mui/icons-material/Add";
import React, { type Node, useContext } from "react";
import useStores from "../../stores/use-stores";
import NavigateContext from "../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../util/error";
import { styled } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import { makeStyles } from "tss-react/mui";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import RecordTypeIcon from "../../components/RecordTypeIcon";

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(-4px, 4px) !important",
          boxShadow: "none",
          border: `2px solid hsl(198deg, 37%, 80%)`,
        }
      : {}),
  },
}));

const useStyles = makeStyles()((theme) => ({
  button: {
    paddingLeft: theme.spacing(3),
    borderTopRightRadius: theme.spacing(3),
    borderBottomRightRadius: theme.spacing(3),
    color: theme.palette.primary.main,
  },
  buttonIcon: {
    color: theme.palette.primary.main,
  },
}));

type CreateNewArgs = {|
  onCreate: () => void,
|};

export default function CreateNew({ onCreate }: CreateNewArgs): Node {
  const { searchStore, trackingStore } = useStores();
  const { classes } = useStyles();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = React.useState(null);

  const handleCreate = async (
    recordType: "sample" | "container" | "template"
  ) => {
    trackingStore.trackEvent("CreateInventoryRecordClicked", {
      type: recordType,
    });
    try {
      const newRecord = await searchStore.createNew(recordType);
      onCreate();
      const params = searchStore.fetcher.generateNewQuery(
        newRecord.showNewlyCreatedRecordSearchParams
      );
      navigate(`/inventory/search?${params.toString()}`, {
        modifyVisiblePanel: false,
      });
      setAnchorEl(null);
    } catch (e) {
      if (e instanceof UserCancelledAction) return;
      throw e;
    }
  };

  const controls = React.useId();
  return (
    <>
      <ListItem
        button
        className={classes.button}
        aria-controls={controls}
        aria-haspopup="true"
        variant="contained"
        color="primary"
        onClick={(event) => setAnchorEl(event.currentTarget)}
      >
        <ListItemIcon className={classes.buttonIcon}>
          <AddIcon />
        </ListItemIcon>
        <ListItemText primary="Create" />
      </ListItem>
      <StyledMenu
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        id={controls}
        keepMounted
        onClose={() => {
          setAnchorEl(null);
        }}
        MenuListProps={{
          disablePadding: true,
        }}
      >
        <NewMenuItem
          title="New Sample"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "sample",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          subheader="For recording experimental materials."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("sample");
          }}
        />
        <NewMenuItem
          title="New Container"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "container",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          subheader="For organising samples."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("container");
          }}
        />
        <NewMenuItem
          title="New Template"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "template",
              }}
              color=""
              style={{
                width: "32px",
                height: "32px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "2px",
                paddingTop: "5px",
                paddingLeft: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          subheader="For easily creating new samples."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("template");
          }}
        />
      </StyledMenu>
    </>
  );
}
