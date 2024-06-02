import React from "react";
import axios from "axios";
import styled from "@emotion/styled";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Divider from "@mui/material/Divider";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faBook,
  faFileWord,
  faFolderOpen,
  faFileAlt,
} from "@fortawesome/free-solid-svg-icons";
import { faEvernote } from "@fortawesome/free-brands-svg-icons";
library.add(faBook, faFileWord, faFolderOpen, faFileAlt, faEvernote);

import NewFolder from "./Workspace/Misc/NewFolder";
import NewNotebook from "./Workspace/Misc/NewNotebook";

const CreateMenuWrapper = styled.div`
  .create-button {
    color: white;
    font-size: 20px;
    font-weight: normal;
    border-color: white;
    margin-right: 20px;
  }
`;

export default function CreateMenu(props) {
  const [open, setOpen] = React.useState(false);
  const [anchorEl, setAnchorEl] = React.useState(null);
  const [dynamicItems, setDynamicItems] = React.useState([]);
  const [gotDynamicItems, setGotDynamicItems] = React.useState(false);

  function getDynamicItems() {
    if (gotDynamicItems) return;

    axios.get("/workspace/ajax/createMenuEntries").then((response) => {
      setGotDynamicItems(true);
      setDynamicItems(response.data.data);
    });
  }

  function openMenu(event) {
    getDynamicItems();
    setOpen(true);
    setAnchorEl(event.currentTarget);
    setTimeout(() => {
      configurePermittedActions();
    }, 200);
  }

  return (
    <CreateMenuWrapper>
      <Button
        id="create"
        data-test-id="create-btn"
        onClick={(e) => openMenu(e)}
        variant="outlined"
        className="create-button"
        aria-label="Create a record"
      >
        Create
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
          <FontAwesomeIcon icon="file-alt" style={{ paddingRight: "10px" }} />
          New entry
        </MenuItem>
        <MenuItem id="createFolder" data-test-id="create-btn-folder">
          <img
            src="/images/icons/folder.png"
            style={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
          />
          Folder
        </MenuItem>
        <MenuItem data-test-id="create-btn-notebook" id="createNotebook">
          <img
            src="/images/icons/notebook.png"
            style={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
          />
          Notebook
        </MenuItem>
        <Divider className="createMenuItemDivider"/>
        {dynamicItems.map((entry) => (
          <MenuItem
            key={entry.id}
            className="directList"
            data-test-id={`create-btn-${entry.name
              .toLowerCase()
              .replace(" ", "-")}`}
          >
            <img
              src={entry.iconURL}
              style={{ paddingRight: "7px", width: "22px", marginLeft: "-5px" }}
            />
            {entry.name}
            <input type="hidden" name="template" value={entry.id} />
          </MenuItem>
        ))}
        {dynamicItems.length > 0 && (
          <MenuItem id="templateMenuLnk" data-test-id="create-btn-from-form">
            <FontAwesomeIcon icon="file-alt" style={{ paddingRight: "10px" }} />
            From Form
          </MenuItem>
        )}
        <MenuItem id="createFromTemplate" data-test-id="create-btn-template">
          <span style={{ paddingRight: "6px" }}>
            <FontAwesomeIcon icon="folder" />
            <span
              style={{
                position: "absolute",
                color: "white",
                fontSize: "9px",
                left: "21px",
                top: "17px",
                fontWeight: "bold",
              }}
            >
              T
            </span>
          </span>
          From Template
        </MenuItem>
        {props.asposeEnabled && (
          <MenuItem id="createFromWord" data-test-id="create-btn-word">
            <FontAwesomeIcon icon="file-word" style={{ paddingRight: "10px" }} />
            From Word
          </MenuItem>
        )}
        {props.evernoteEnabled && (
          <MenuItem id="createFromEvernote" data-test-id="create-btn-evernote">
            <FontAwesomeIcon
              icon={["fab", "evernote"]}
              style={{ paddingRight: "10px" }}
            />
            From Evernote
          </MenuItem>
        )}
        {props.pioEnabled && (
          <MenuItem
            id="createFromProtocolsIo"
            data-test-id="create-btn-protocols"
          >
            <img
              src="/images/protocolsio.png"
              style={{ paddingRight: "5px", width: "22px", marginLeft: "-5px" }}
            />
            From Protocols.io
          </MenuItem>
        )}
        <Divider className="createMenuItemDivider"/>
        <MenuItem id="createNewForm" data-test-id="create-btn-new-form">
          <FontAwesomeIcon icon="file-alt" style={{ paddingRight: "10px" }} />
          New Form
        </MenuItem>
      </Menu>
      <NewFolder />
      <NewNotebook />
    </CreateMenuWrapper>
  );
}
