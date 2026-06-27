import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { Group } from "../../../../stores/definitions/Group";

type OwnersGroupsTableArgs = {
  groups: Array<Group>;
};

export default function OwnersGroupsTable({ groups }: OwnersGroupsTableArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const groupData = groups.map(({ name, id }) => {
    if (!id) throw new Error("Group id is missing");
    return { name, id };
  });

  return (
    <Table size="small" stickyHeader>
      <TableHead>
        <TableRow>
          <TableCell variant="head">{t("fields.accessPermissions.groupName")}</TableCell>
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
