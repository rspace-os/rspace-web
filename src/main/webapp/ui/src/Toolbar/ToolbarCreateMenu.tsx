/* global configurePermittedActions */

import { faEvernote } from "@fortawesome/free-brands-svg-icons/faEvernote";
import { faFileAlt } from "@fortawesome/free-solid-svg-icons/faFileAlt";
import { faFileWord } from "@fortawesome/free-solid-svg-icons/faFileWord";
import { faFolderOpen } from "@fortawesome/free-solid-svg-icons/faFolderOpen";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

import NewFolder from "./Workspace/Misc/NewFolder";
import NewNotebook from "./Workspace/Misc/NewNotebook";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const configurePermittedActions: any;

/**
 * Primary create menu shown in the workspace toolbar.
 */
type DynamicMenuItem = {
  id: string | number;
  name: string;
  iconURL: string;
};

type CreateMenuProps = {
  asposeEnabled?: boolean;
  evernoteEnabled?: boolean;
  pioEnabled?: boolean;
};

export default function CreateMenu(props: CreateMenuProps) {
  const { t } = useTranslation("common");
  const [open, setOpen] = React.useState(false);
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  const [dynamicItems, setDynamicItems] = React.useState<Array<DynamicMenuItem>>([]);
  const [gotDynamicItems, setGotDynamicItems] = React.useState(false);

  function getDynamicItems() {
    if (gotDynamicItems) return;

    axios.get("/workspace/ajax/createMenuEntries").then((response) => {
      setGotDynamicItems(true);
      setDynamicItems(response.data.data as Array<DynamicMenuItem>);
    });
  }

  function openMenu(event: React.MouseEvent<HTMLButtonElement>) {
    getDynamicItems();
    setOpen(true);
    setAnchorEl(event.currentTarget);
    setTimeout(() => {
      configurePermittedActions();
    }, 200);
  }

  return (
    <>
      <Button
        id="create"
        data-test-id="create-btn"
        onClick={(e) => openMenu(e)}
        variant="outlined"
        sx={{
          color: "white",
          fontSize: "clamp(1.2rem, 1cqi, 1.5rem)",
          fontWeight: "normal",
          borderColor: "white",
        }}
        aria-label={t("toolbar.createRecord")}
      >
        {t("actions.create")}
      </Button>
      <Menu
        anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
        anchorEl={anchorEl}
        keepMounted
        open={open}
        onClick={() => setOpen(false)}
        onClose={() => setOpen(false)}
      >
        <MenuItem id="createEntry" data-test-id="create-btn-new-entry">
          <FontAwesomeIcon icon={faFileAlt} style={{ paddingRight: "10px" }} aria-hidden="true" />
          {t("toolbar.newEntry")}
        </MenuItem>
        <MenuItem id="createFolder" data-test-id="create-btn-folder">
          <Box
            component="img"
            src="/images/icons/folder.png"
            alt={t("toolbar.folderIcon")}
            sx={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
          />
          {t("toolbar.folder")}
        </MenuItem>
        <MenuItem data-test-id="create-btn-notebook" id="createNotebook">
          <Box
            component="img"
            src="/images/icons/notebook.png"
            alt={t("toolbar.notebookIcon")}
            sx={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
          />
          {t("toolbar.notebook")}
        </MenuItem>
        <Divider className="createMenuItemDivider" />
        {dynamicItems.map((entry) => (
          <MenuItem
            key={entry.id}
            className="directList"
            data-test-id={`create-btn-${entry.name.toLowerCase().replace(" ", "-")}`}
          >
            <Box
              component="img"
              src={entry.iconURL}
              alt={t("toolbar.folderIcon")}
              sx={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
            />
            {entry.name}
            <input type="hidden" name="template" value={entry.id} />
          </MenuItem>
        ))}
        {dynamicItems.length > 0 && (
          <MenuItem id="templateMenuLnk" data-test-id="create-btn-from-form">
            <FontAwesomeIcon icon={faFileAlt} style={{ paddingRight: "10px" }} aria-hidden="true" />
            {t("toolbar.fromForm")}
          </MenuItem>
        )}
        <MenuItem id="createFromTemplate" data-test-id="create-btn-template">
          <Box component="span" sx={{ paddingRight: "6px" }}>
            <FontAwesomeIcon icon={faFolderOpen} aria-hidden="true" />
            <Box
              component="span"
              aria-hidden="true"
              sx={{
                position: "absolute",
                color: "white",
                fontSize: "9px",
                left: "21px",
                top: "17px",
                fontWeight: "bold",
              }}
            >
              T
            </Box>
          </Box>
          {t("toolbar.fromTemplate")}
        </MenuItem>
        {props.asposeEnabled && (
          <MenuItem id="createFromWord" data-test-id="create-btn-word">
            <FontAwesomeIcon icon={faFileWord} style={{ paddingRight: "10px" }} aria-hidden="true" />
            {t("toolbar.fromWord")}
          </MenuItem>
        )}
        {props.evernoteEnabled && (
          <MenuItem id="createFromEvernote" data-test-id="create-btn-evernote">
            <FontAwesomeIcon icon={faEvernote} style={{ paddingRight: "10px" }} aria-hidden="true" />
            {t("toolbar.fromEvernote")}
          </MenuItem>
        )}
        {props.pioEnabled && (
          <MenuItem id="createFromProtocolsIo" data-test-id="create-btn-protocols">
            <Box
              component="img"
              src="/images/integrations/protocolsio.png"
              alt={t("toolbar.protocolsIoIcon")}
              sx={{ paddingRight: "5px", width: "22px", marginLeft: "-5px" }}
            />
            {t("toolbar.fromProtocolsIo")}
          </MenuItem>
        )}
        <Divider className="createMenuItemDivider" />
        <MenuItem id="createNewForm" data-test-id="create-btn-new-form">
          <FontAwesomeIcon icon={faFileAlt} style={{ paddingRight: "10px" }} aria-hidden="true" />
          {t("toolbar.newForm")}
        </MenuItem>
      </Menu>
      <NewFolder />
      <NewNotebook />
    </>
  );
}
