import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import React from "react";

/**
 * Save menu for structured document editing.
 */
type CreateMenuProps = {
  canCopy?: boolean;
};

export default function CreateMenu(props: CreateMenuProps) {
  const [open, setOpen] = React.useState(false);
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);

  function openMenu(event: React.MouseEvent<HTMLButtonElement>) {
    setOpen(true);
    setAnchorEl(event.currentTarget);
  }

  return (
    <>
      <Button
        data-test-id="notebook-save-btn"
        onClick={(e) => openMenu(e)}
        variant="outlined"
        className="editMode"
        sx={{
          color: "white",
          fontSize: 20,
          fontWeight: "normal",
          borderColor: "white",
          marginRight: 5,
        }}
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
            <MenuItem id="saveAsTemplateSaveMenuBtn" data-test-id="save-btn-template">
              Save as Template
            </MenuItem>
          </span>
        )}
      </Menu>
    </>
  );
}
