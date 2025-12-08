import AddIcon from "@mui/icons-material/Add";
import Checkbox from "@mui/material/Checkbox";
import Grid from "@mui/material/Grid";
import Popover from "@mui/material/Popover";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useState } from "react";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import type { Group, SharedWithGroup } from "../../../../stores/definitions/Group";
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
                        <Grid container justifyContent="space-between" alignItems="center" wrap="nowrap">
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
                                            return sharedWith.some(({ group: { id } }) => group.id === id);
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
