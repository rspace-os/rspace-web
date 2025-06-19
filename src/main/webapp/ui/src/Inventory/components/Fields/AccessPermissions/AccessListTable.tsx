import React, { useState } from "react";
import Table from "@mui/material/Table";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TableBody from "@mui/material/TableBody";
import TableHead from "@mui/material/TableHead";
import Grid from "@mui/material/Grid";
import AddIcon from "@mui/icons-material/Add";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import Checkbox from "@mui/material/Checkbox";
import {
  type SharedWithGroup,
  type Group,
} from "../../../../stores/definitions/Group";
import Popover from "@mui/material/Popover";
import GroupsField from "../../Inputs/GroupsField";

type AccessListTableArgs = {
  sharedWith: Array<SharedWithGroup>;
  disabled: boolean;
  onCheckboxClick: (group: Group) => void;
  onAdditionalGroup: (group: Group) => void;
};

export default function AccessListTable({
  sharedWith,
  disabled,
  onCheckboxClick,
  onAdditionalGroup,
}: AccessListTableArgs): React.ReactNode {
  const [addButton, setAddButton] = useState<HTMLElement | null>(null);

  return (
    <Table size="small" stickyHeader>
      <TableHead>
        <TableRow>
          <TableCell></TableCell>
          <TableCell>
            <Grid
              container
              justifyContent="space-between"
              alignItems="center"
              wrap="nowrap"
            >
              <Grid item>Group Name</Grid>
              <Grid item>
                <IconButtonWithTooltip
                  title="Add a group"
                  icon={<AddIcon />}
                  onClick={(event) => {
                    setAddButton(event.currentTarget);
                  }}
                  size="small"
                  disabled={disabled}
                />
                <Popover
                  open={Boolean(addButton)}
                  anchorEl={addButton}
                  onClose={() => setAddButton(null)}
                  anchorOrigin={{
                    vertical: "bottom",
                    horizontal: "center",
                  }}
                  transformOrigin={{
                    vertical: "top",
                    horizontal: "center",
                  }}
                  PaperProps={{
                    variant: "outlined",
                    style: {
                      minWidth: 300,
                    },
                  }}
                >
                  <GroupsField
                    onSelection={(group) => {
                      setAddButton(null);
                      onAdditionalGroup(group);
                    }}
                    label=""
                    getOptionDisabled={(group) => {
                      return sharedWith.some(
                        ({ group: { id } }) => group.id === id
                      );
                    }}
                  />
                </Popover>
              </Grid>
            </Grid>
          </TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {sharedWith.map(({ group, shared }) => (
          <TableRow key={group.id}>
            <TableCell padding="checkbox">
              <Checkbox
                onClick={() => onCheckboxClick(group)}
                checked={shared}
                color="default"
                disabled={disabled}
              />
            </TableCell>
            <TableCell>{group.name}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
