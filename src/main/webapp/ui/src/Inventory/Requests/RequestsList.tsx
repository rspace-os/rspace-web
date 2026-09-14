import Alert, { alertClasses } from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "@/components/GlobalId";
import UserDetails from "@/components/UserDetails";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import { isoToLocale } from "@/util/Util";
import ApiService from "../../common/InvApiService";
import DropdownButton from "../../components/DropdownButton";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import StyledMenu from "../../components/StyledMenu";
import RequestsParameterChips from "./RequestsParameterChips";
import RequestsSearchbar from "./RequestsSearchbar";
import RequestsStatusChip from "./RequestsStatusChip";
import RequestsTableHead, { type ColumnKey, type SortDirection } from "./RequestsTableHead";

export type ApiSampleRequestListItem = {
  id: number;
  status: string;
  created: string;
  note: string | null;
  requester: { id: number; firstName: string; lastName: string };
  sample: { id: number; globalId: string; name: string };
};

export type From = "me" | "others";
export type StatusFilter = "all" | "active" | "past";

const ACTIVE_STATUSES = "PENDING,APPROVED";
const PAST_STATUSES = "REJECTED,FULFILLED,CANCELLED";

function getColumnValue(request: ApiSampleRequestListItem, column: ColumnKey): number | string {
  switch (column) {
    case "id":
      return request.id;
    case "sample":
      return request.sample.id;
    case "requester":
      return request.requester.id;
    case "status":
      return request.status;
    case "submitted":
      return Date.parse(request.created);
  }
}

function renderColumnCell(request: ApiSampleRequestListItem, column: ColumnKey): React.ReactNode {
  if (column === "sample") {
    return <GlobalId record={new LinkableRecordFromGlobalId(request.sample.globalId)} onClick={() => {}} />;
  }
  if (column === "requester") {
    return (
      <UserDetails
        userId={request.requester.id}
        fullName={`${request.requester.firstName} ${request.requester.lastName}`}
        position={["bottom", "right"]}
      />
    );
  }
  if (column === "status") {
    return <RequestsStatusChip status={request.status} />;
  }
  if (column === "submitted") {
    return isoToLocale(request.created);
  }
  return getColumnValue(request, column);
}

export default function RequestsList({
  selectedRequestId,
  onSelect,
}: {
  selectedRequestId: number | null;
  onSelect: (request: ApiSampleRequestListItem) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [from, setFrom] = useState<From>("me");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [fromDropdown, setFromDropdown] = useState<HTMLElement | null>(null);
  const [statusDropdown, setStatusDropdown] = useState<HTMLElement | null>(null);
  const [requests, setRequests] = useState<Array<ApiSampleRequestListItem>>([]);
  const [loading, setLoading] = useState(true);
  // Not wired up to any filtering yet; that will come once the /sampleRequests
  // endpoint supports a free-text query.
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<ColumnKey>("sample");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");
  const [adjustableColumn, setAdjustableColumn] = useState<ColumnKey>("status");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const params = new URLSearchParams({
      role: from === "me" ? "REQUESTER" : "OWNER",
      pageSize: "100",
    });
    if (statusFilter === "active") params.set("status", ACTIVE_STATUSES);
    if (statusFilter === "past") params.set("status", PAST_STATUSES);

    ApiService.query<{ requests: Array<ApiSampleRequestListItem> }>("sampleRequests", params)
      .then(({ data }) => {
        if (cancelled) return;
        setRequests(data.requests);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to fetch sample requests", error);
        setRequests([]);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [from, statusFilter]);

  const handleSort = (column: ColumnKey) => {
    if (sortBy === column) {
      setSortDirection((direction) => (direction === "asc" ? "desc" : "asc"));
    } else {
      setSortBy(column);
      setSortDirection("asc");
    }
  };

  const handleAdjustableColumnChange = (newColumn: ColumnKey) => {
    setAdjustableColumn(newColumn);
  };

  const sortedRequests = useMemo(() => {
    const sorted = [...requests].sort((a, b) => {
      const aValue = getColumnValue(a, sortBy);
      const bValue = getColumnValue(b, sortBy);
      if (aValue < bValue) return -1;
      if (aValue > bValue) return 1;
      return 0;
    });
    if (sortDirection === "desc") sorted.reverse();
    return sorted;
  }, [requests, sortBy, sortDirection]);

  const fromLabel =
    from === "me" ? t("requestsManagement.filters.from.me") : t("requestsManagement.filters.from.others");
  const statusLabel =
    statusFilter === "all"
      ? t("requestsManagement.filters.status.all")
      : statusFilter === "active"
        ? t("requestsManagement.filters.status.active")
        : t("requestsManagement.filters.status.past");

  return (
    <Box
      sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%", minWidth: 0, p: 1 }}
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", width: "100%" }}>
        <RequestsSearchbar value={searchQuery} onChange={setSearchQuery} />
        <HelpLinkIcon link={helpDocsArticleUrl("search")} title={t("search.helpTitle")} />
      </Stack>
      <Grid container direction="row" spacing={1} sx={{ pt: 1 }}>
        <DropdownButton
          name={`${t("requestsManagement.filters.from.label")}: ${fromLabel}`}
          onClick={({ target }) => setFromDropdown(target as HTMLElement)}
        >
          <StyledMenu anchorEl={fromDropdown} open={Boolean(fromDropdown)} onClose={() => setFromDropdown(null)}>
            <MenuItem
              selected={from === "me"}
              onClick={() => {
                setFrom("me");
                setFromDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.from.me")} />
            </MenuItem>
            <MenuItem
              selected={from === "others"}
              onClick={() => {
                setFrom("others");
                setFromDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.from.others")} />
            </MenuItem>
          </StyledMenu>
        </DropdownButton>
        <DropdownButton
          name={`${t("requestsManagement.filters.status.label")}: ${statusLabel}`}
          onClick={({ target }) => setStatusDropdown(target as HTMLElement)}
        >
          <StyledMenu anchorEl={statusDropdown} open={Boolean(statusDropdown)} onClose={() => setStatusDropdown(null)}>
            <MenuItem
              selected={statusFilter === "all"}
              onClick={() => {
                setStatusFilter("all");
                setStatusDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.status.all")} />
            </MenuItem>
            <MenuItem
              selected={statusFilter === "active"}
              onClick={() => {
                setStatusFilter("active");
                setStatusDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.status.active")} />
            </MenuItem>
            <MenuItem
              selected={statusFilter === "past"}
              onClick={() => {
                setStatusFilter("past");
                setStatusDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.status.past")} />
            </MenuItem>
          </StyledMenu>
        </DropdownButton>
      </Grid>
      <Box sx={{ pt: 1 }}>
        <RequestsParameterChips from={from} statusFilter={statusFilter} />
      </Box>
      <Divider orientation="horizontal" sx={{ my: 0.75 }} />
      <Alert
        sx={(theme) => ({
          p: `${theme.spacing(0.5)} ${theme.spacing(2)}`,
          backgroundColor: theme.palette.primary.background,
          color: theme.palette.primary.contrastText,
          minHeight: theme.spacing(4),
          [`& .${alertClasses.message}`]: { p: 0, alignSelf: "center" },
        })}
        icon={false}
        severity="info"
        role="status"
      >
        {t("requestsManagement.feedback", { count: requests.length })}
      </Alert>
      <Box sx={{ flexGrow: 1, overflow: "auto" }}>
        <TableContainer>
          <Table size="small" stickyHeader>
            <RequestsTableHead
              sortBy={sortBy}
              sortDirection={sortDirection}
              onSort={handleSort}
              adjustableColumn={adjustableColumn}
              onAdjustableColumnChange={handleAdjustableColumnChange}
            />
            <TableBody>
              {sortedRequests.map((request) => (
                <TableRow
                  key={request.id}
                  hover
                  selected={request.id === selectedRequestId}
                  onClick={() => onSelect(request)}
                  sx={{ cursor: "pointer" }}
                >
                  <TableCell>{renderColumnCell(request, "sample")}</TableCell>
                  <TableCell>{renderColumnCell(request, "requester")}</TableCell>
                  <TableCell>{renderColumnCell(request, adjustableColumn)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        {!loading && requests.length === 0 && (
          <Typography variant="body2" sx={{ p: 2 }} color="text.secondary">
            {t("requestsManagement.noResults")}
          </Typography>
        )}
      </Box>
    </Box>
  );
}
