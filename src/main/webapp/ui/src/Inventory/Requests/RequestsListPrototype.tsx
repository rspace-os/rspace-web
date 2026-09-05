/** @prototype Storybook-only UI exploration; not production-ready. */
import Badge from "@mui/material/Badge";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../components/GlobalId";
import TableCell from "../Search/components/TableCell";
import RequestStatusChipPrototype from "./RequestStatusChipPrototype";
import type { SampleRequest } from "./types";

export type RequestsTab = "incoming" | "outgoing";

type RequestsListArgs = {
  /** Requests raised against items the current user owns. */
  incoming: ReadonlyArray<SampleRequest>;
  /** Requests the current user raised. */
  outgoing: ReadonlyArray<SampleRequest>;
  tab: RequestsTab;
  onTabChange: (tab: RequestsTab) => void;
  onOpen: (request: SampleRequest) => void;
};

/**
 * The Requests page's list: one tab for requests to review as an owner, one
 * for the user's own requests. Notifications link here, so this is where an
 * owner lands to approve or reject.
 */
export default function RequestsListPrototype({
  incoming,
  outgoing,
  tab,
  onTabChange,
  onOpen,
}: RequestsListArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const id = React.useId();
  const rows = tab === "incoming" ? incoming : outgoing;
  const pending = incoming.filter((r) => r.status === "pending").length;
  // an owner still owes something on a pending, approved or prepared request
  const owed = (request: SampleRequest) =>
    tab === "incoming" && request.status !== "fulfilled" && request.status !== "rejected";
  const actionLabel = (request: SampleRequest) =>
    !owed(request)
      ? t("requests.actions.view")
      : request.status === "pending"
        ? t("requests.actions.review")
        : t("requests.actions.resume");

  return (
    <Stack spacing={1}>
      <Tabs
        textColor="inherit"
        value={tab}
        onChange={(_event, value: RequestsTab) => onTabChange(value)}
        aria-label={t("requests.sectionTitle")}
      >
        <Tab
          value="incoming"
          id={`${id}-incoming`}
          aria-controls={`${id}-panel`}
          label={
            <Badge badgeContent={pending} color="error" sx={{ pr: pending > 0 ? 2 : 0 }}>
              {t("requests.list.tabs.incoming")}
            </Badge>
          }
        />
        <Tab
          value="outgoing"
          id={`${id}-outgoing`}
          aria-controls={`${id}-panel`}
          label={t("requests.list.tabs.outgoing")}
        />
      </Tabs>
      <TableContainer id={`${id}-panel`} role="tabpanel" aria-labelledby={`${id}-${tab}`} sx={{ overflowX: "auto" }}>
        <Table size="small" aria-label={t(`requests.list.tabs.${tab}`)}>
          <TableHead>
            <TableRow>
              <TableCell>{t("requests.list.columns.request")}</TableCell>
              <TableCell>
                {t(tab === "incoming" ? "requests.list.columns.requester" : "requests.list.columns.owner")}
              </TableCell>
              <TableCell>{t("requests.list.columns.item")}</TableCell>
              <TableCell>{t("requests.list.columns.note")}</TableCell>
              <TableCell>{t("requests.list.columns.raised")}</TableCell>
              <TableCell align="right">{t("requests.list.columns.actions")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary" sx={{ textAlign: "center", py: 3 }}>
                    {t(`requests.list.empty.${tab}`)}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {rows.map((request) => (
              <TableRow key={request.id} hover>
                <TableCell>
                  <Stack spacing={0.5} sx={{ alignItems: "flex-start" }}>
                    <Typography variant="body2" sx={{ fontFamily: "monospace", fontWeight: "fontWeightMedium" }}>
                      {request.id}
                    </Typography>
                    <RequestStatusChipPrototype status={request.status} />
                  </Stack>
                </TableCell>
                <TableCell>{tab === "incoming" ? request.requester.fullName : request.sample.owner.fullName}</TableCell>
                <TableCell>
                  <GlobalId
                    record={{
                      id: Number(request.sample.globalId.slice(2)),
                      name: request.sample.name,
                      globalId: request.sample.globalId,
                      permalinkURL: `/inventory/sample/${request.sample.globalId.slice(2)}`,
                      iconName: "sample",
                      recordTypeLabel: t("recordTypes.sample.singular"),
                    }}
                  />
                </TableCell>
                <TableCell sx={{ maxWidth: 320 }}>
                  <Typography
                    variant="body2"
                    sx={{ display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}
                  >
                    {request.note}
                  </Typography>
                </TableCell>
                <TableCell sx={{ whiteSpace: "nowrap" }}>{request.when}</TableCell>
                <TableCell align="right">
                  <Button
                    size="small"
                    variant={owed(request) ? "contained" : "outlined"}
                    color={owed(request) ? "callToAction" : undefined}
                    onClick={() => onOpen(request)}
                    aria-label={`${actionLabel(request)} ${request.id}`}
                  >
                    {actionLabel(request)}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Stack>
  );
}
