import React from "react";
import styled from "@emotion/styled";
import Button from "@mui/material/Button";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Divider from "@mui/material/Divider";

const SaveMenuWrapper = styled.div`
  .save-button {
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

  function openMenu(event) {
    setOpen(true);
    setAnchorEl(event.currentTarget);
  }

  return (
    <SaveMenuWrapper>
      <Button
        data-test-id="notebook-save-btn"
        onClick={(e) => openMenu(e)}
        variant="outlined"
        className="save-button editMode"
      >
        Save
      </Button>
      <Menu
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        anchorEl={anchorEl}
        keepMounted
        open={open}
        onClick={() => setOpen(false)}
        onClose={() => setOpen(false)}
      >
        <MenuItem id="save" data-test-id="save-btn-save">
          Save
        </MenuItem>
        <Divider />
        <MenuItem id="saveClose" data-test-id="save-btn-close">
          Save & Close
        </MenuItem>
        <MenuItem id="saveView" data-test-id="save-btn-view">
          Save & View
        </MenuItem>
        {props.canCopy && (
          <span>
            <MenuItem id="saveClone" data-test-id="save-btn-clone">
              Save & Clone
            </MenuItem>
            <MenuItem id="saveNew" data-test-id="save-btn-new">
              Save & New
            </MenuItem>
            <Divider />
            <MenuItem
              id="saveAsTemplateSaveMenuBtn"
              data-test-id="save-btn-template"
            >
              Save as Template
            </MenuItem>
          </span>
        )}
      </Menu>
    </SaveMenuWrapper>
  );
}
