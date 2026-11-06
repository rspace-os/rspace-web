import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import type React from "react";
import { useTranslation } from "react-i18next";
import NoValue from "@/components/NoValue";
import UserDetails from "@/components/UserDetails";
import { isoToLocale } from "@/util/Util";
import RequestsStatusChip from "./RequestsStatusChip";
import { userDisplayName } from "./sampleRequest";

export type ApiSampleRequestStatusChangeItem = {
  id: number;
  status: string;
  created: string;
  createdBy: { id: number; username: string; firstName: string | null; lastName: string | null };
  reason: string | null;
  transferredSample: { id: number; globalId: string; name: string } | null;
};

/** The PENDING row has no reason of its own - only the requester's note explains it. */
function additionalNotes(change: ApiSampleRequestStatusChangeItem, requesterNote: string | null): string | null {
  return change.status === "PENDING" ? requesterNote : change.reason;
}

/**
 * A record of every status change for a SampleRequest, in the same plain-table
 * format as the "Sample Locations" table. Not sortable by the user (always
 * shown newest-first). Both "Date" and "Additional Notes" (the reason supplied
 * for that change, or - for the PENDING row - the requester's own note) are
 * always shown.
 */
export default function RequestHistoryTable({
  statusChanges,
  requesterNote,
}: {
  statusChanges: Array<ApiSampleRequestStatusChangeItem>;
  /** The request's own note (SampleRequest.note) - see additionalNotes() above. */
  requesterNote: string | null;
}): React.ReactNode {
  const { t } = useTranslation("inventory");

  const sortedChanges = [...statusChanges].sort(
    (a, b) => new Date(b.created).getTime() - new Date(a.created).getTime(),
  );

  return (
    <TableContainer>
      <Table size="small" stickyHeader>
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: 120, whiteSpace: "nowrap" }}>
              {t("requestsManagement.detail.history.columns.status")}
            </TableCell>
            <TableCell>{t("requestsManagement.detail.history.columns.user")}</TableCell>
            <TableCell>{t("requestsManagement.detail.history.columns.date")}</TableCell>
            <TableCell>{t("requestsManagement.detail.history.columns.additionalNotes")}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {sortedChanges.map((change) => (
            <TableRow key={change.id}>
              <TableCell sx={{ width: 120, whiteSpace: "nowrap" }}>
                <RequestsStatusChip status={change.status} />
              </TableCell>
              <TableCell>
                {change.createdBy.firstName === null && change.createdBy.lastName === null ? (
                  userDisplayName(change.createdBy, t)
                ) : (
                  <UserDetails
                    userId={change.createdBy.id}
                    fullName={userDisplayName(change.createdBy, t)}
                    position={["bottom", "right"]}
                    maxWidth={160}
                  />
                )}
              </TableCell>
              <TableCell>{isoToLocale(change.created)}</TableCell>
              <TableCell>
                {additionalNotes(change, requesterNote) || (
                  <NoValue label={t("requestsManagement.detail.fields.noComment")} />
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
