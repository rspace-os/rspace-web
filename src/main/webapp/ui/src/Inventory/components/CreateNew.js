// @flow
import AddIcon from "@mui/icons-material/Add";
import React, { type Node, useContext } from "react";
import useStores from "../../stores/use-stores";
import NavigateContext from "../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../util/error";
import { styled } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import Divider from "@mui/material/Divider";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";

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

type CreateNewArgs = {|
  onClick: () => void,
|};

export default function CreateNew({ onClick }: CreateNewArgs): Node {
  const { searchStore, trackingStore, uiStore, importStore } = useStores();
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
      onClick();
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

  const handleImport = async (
    recordType: "SAMPLES" | "CONTAINERS" | "SUBSAMPLES"
  ) => {
    if (await uiStore.confirmDiscardAnyChanges()) {
      await importStore.initializeNewImport(recordType);
      navigate(`/inventory/import?recordType=${recordType}`);
      onClick();
      setAnchorEl(null);
    }
  };

  const controls = React.useId();
  return (
    <Box sx={{ p: 1.5, pt: 0 }}>
      <Button
        variant="outlined"
        fullWidth
        aria-controls={controls}
        aria-haspopup="true"
        onClick={(event) => setAnchorEl(event.currentTarget)}
        startIcon={<AddIcon />}
      >
        Create
      </Button>
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
        <Divider textAlign="left" aria-label="CSV import">
          CSV Import
        </Divider>
        <NewMenuItem
          title="Samples"
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
          subheader="Import samples with a similar structure."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("SAMPLES");
          }}
        />
        <NewMenuItem
          title="Subsamples"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "subsample",
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
          subheader="Import subsamples for each sample."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("SUBSAMPLES");
          }}
        />
        <NewMenuItem
          title="Containers"
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
          subheader="Import containers and their contents."
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("CONTAINERS");
          }}
        />
      </StyledMenu>
    </Box>
  );
}
