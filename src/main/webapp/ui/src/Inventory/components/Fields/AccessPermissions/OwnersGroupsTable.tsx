import React from "react";
import Table from "@mui/material/Table";
import TableCell from "@mui/material/TableCell";
import TableRow from "@mui/material/TableRow";
import TableBody from "@mui/material/TableBody";
import TableHead from "@mui/material/TableHead";
import { type Group } from "../../../../stores/definitions/Group";

type OwnersGroupsTableArgs = {
  groups: Array<Group>;
};

export default function OwnersGroupsTable({
  groups,
}: OwnersGroupsTableArgs): React.ReactNode {
  const groupData = groups.map(({ name, id }) => {
    if (!id) throw new Error("Group id is missing");
    return { name, id };
  });

  return (
    <Table size="small" stickyHeader>
      <TableHead>
        <TableRow>
          <TableCell variant="head">Group Name</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {groupData.map((group) => (
          <TableRow key={group.id}>
            <TableCell>
              <a href={`/groups/view/${group.id}`}>{group.name}</a>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}