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
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "@/components/GlobalId";
import UserDetails from "@/components/UserDetails";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import { paginationOptions } from "@/util/table";
import { isoToLocale } from "@/util/Util";
import DropdownButton from "../../components/DropdownButton";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import StyledMenu from "../../components/StyledMenu";
import { useSampleRequestsListQuery } from "./queries";
import RequestsParameterChips from "./RequestsParameterChips";
import RequestsSearchbar from "./RequestsSearchbar";
import RequestsStatusChip, { requestStatusLabel } from "./RequestsStatusChip";
import RequestsTableHead, { type ColumnKey, type SortDirection } from "./RequestsTableHead";

export type ApiSampleRequestListItem = {
  id: number;
  status: string;
  created: string;
  note: string | null;
  requester: { id: number; username: string; firstName: string; lastName: string };
  sample: { id: number; globalId: string; name: string; owner: { id: number } };
};

export type RequestsFilter = "all" | "sent" | "received";
export type StatusFilter = "all" | "active" | "past";

// Smaller than the main Inventory search's (5, 10, 25, 100): this list is rarely as long.
const REQUEST_PAGE_SIZES = [10, 25, 50];
const DEFAULT_REQUEST_PAGE_SIZE = 25;
// Decoupled from REQUEST_PAGE_SIZES' own max (50): without this, "All" would disappear for any
// count over 50, even though fetching and rendering a few hundred requests at once is still fine.
const REQUEST_ALL_THRESHOLD = 200;

function getColumnValue(request: ApiSampleRequestListItem, column: ColumnKey): number | string {
  switch (column) {
    case "id":
      return request.id;
    case "sample":
      return request.sample.id;
    case "requester":
      // Matches the name shown in the UserDetails chip below, rather than the
      // underlying id, since that's the value a user sorting this column sees.
      return `${request.requester.firstName} ${request.requester.lastName}`;
    case "status":
      return request.status;
    case "submitted":
      return Date.parse(request.created);
  }
}

/**
 * The /sampleRequests endpoint has no free-text query parameter (see
 * SampleRequestApiSearchConfig), so the searchbar filters the already-fetched
 * `requests` array client-side instead - the same approach already taken for
 * sorting and pagination, for the same reason.
 */
function requestMatchesQuery(request: ApiSampleRequestListItem, lowerCaseQuery: string): boolean {
  const searchableText = [
    request.sample.globalId,
    request.sample.name,
    `${request.requester.firstName} ${request.requester.lastName}`,
    request.requester.username,
    request.note ?? "",
  ];
  return searchableText.some((text) => text.toLowerCase().includes(lowerCaseQuery));
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
  const [requestsFilter, setRequestsFilter] = useState<RequestsFilter>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [fromDropdown, setFromDropdown] = useState<HTMLElement | null>(null);
  const [statusDropdown, setStatusDropdown] = useState<HTMLElement | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<ColumnKey>("submitted");
  const [sortDirection, setSortDirection] = useState<SortDirection>("desc");
  const [adjustableColumn, setAdjustableColumn] = useState<ColumnKey>("status");
  const [pageNumber, setPageNumber] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_REQUEST_PAGE_SIZE);

  // Refetches on its own whenever a mutation elsewhere invalidates sampleRequestsQueryKeys (e.g.
  // approved/rejected from the detail pane), since the active filters may mean it should appear,
  // disappear, or just show a different status - no manual refresh-counter/event wiring needed.
  const { data: requests = [], isLoading: loading } = useSampleRequestsListQuery(requestsFilter, statusFilter);

  // A page number left over from a longer list would otherwise point past the end of a shorter
  // one once the filters, search, sort, or page size change what "the list" even is.
  useEffect(() => {
    setPageNumber(0);
  }, [requestsFilter, statusFilter, searchQuery, sortBy, sortDirection, pageSize]);

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

  const searchedRequests = useMemo(() => {
    const lowerCaseQuery = searchQuery.trim().toLowerCase();
    if (!lowerCaseQuery) return requests;
    return requests.filter((request) => requestMatchesQuery(request, lowerCaseQuery));
  }, [requests, searchQuery]);

  const sortedRequests = useMemo(() => {
    const sorted = [...searchedRequests].sort((a, b) => {
      const aValue = getColumnValue(a, sortBy);
      const bValue = getColumnValue(b, sortBy);
      if (aValue < bValue) return -1;
      if (aValue > bValue) return 1;
      return 0;
    });
    if (sortDirection === "desc") sorted.reverse();
    return sorted;
  }, [searchedRequests, sortBy, sortDirection]);

  const pagedRequests = useMemo(
    () => sortedRequests.slice(pageNumber * pageSize, pageNumber * pageSize + pageSize),
    [sortedRequests, pageNumber, pageSize],
  );

  const requestsFilterLabel =
    requestsFilter === "all"
      ? t("requestsManagement.filters.requests.all")
      : requestsFilter === "sent"
        ? t("requestsManagement.filters.requests.sent")
        : t("requestsManagement.filters.requests.received");
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
        <HelpLinkIcon link={helpDocsArticleUrl("sampleRequests")} title={t("requestsManagement.helpTitle")} />
      </Stack>
      <Grid container direction="row" spacing={1} sx={{ pt: 1 }}>
        <DropdownButton
          name={`${t("requestsManagement.filters.requests.label")}: ${requestsFilterLabel}`}
          onClick={({ target }) => setFromDropdown(target as HTMLElement)}
        >
          <StyledMenu anchorEl={fromDropdown} open={Boolean(fromDropdown)} onClose={() => setFromDropdown(null)}>
            <MenuItem
              selected={requestsFilter === "all"}
              onClick={() => {
                setRequestsFilter("all");
                setFromDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.requests.all")} />
            </MenuItem>
            <MenuItem
              selected={requestsFilter === "sent"}
              onClick={() => {
                setRequestsFilter("sent");
                setFromDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.requests.sent")} />
            </MenuItem>
            <MenuItem
              selected={requestsFilter === "received"}
              onClick={() => {
                setRequestsFilter("received");
                setFromDropdown(null);
              }}
            >
              <ListItemText primary={t("requestsManagement.filters.requests.received")} />
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
        <RequestsParameterChips requestsFilter={requestsFilter} statusFilter={statusFilter} />
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
        {t("requestsManagement.feedback", { count: searchedRequests.length })}
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
              {pagedRequests.map((request) => (
                <TableRow
                  key={request.id}
                  hover
                  selected={request.id === selectedRequestId}
                  aria-selected={request.id === selectedRequestId}
                  // Makes each row its own Tab stop, in document order, so a keyboard user can
                  // reach any request without a mouse; Enter/Space below then opens it, mirroring
                  // the onClick handler rather than requiring a separate keyboard-only code path.
                  tabIndex={0}
                  aria-label={t("requestsManagement.rowLabel", {
                    sampleGlobalId: request.sample.globalId,
                    requester: `${request.requester.firstName} ${request.requester.lastName}`,
                    status: requestStatusLabel(request.status, t),
                  })}
                  onClick={() => onSelect(request)}
                  onKeyDown={(e) => {
                    if (e.key !== "Enter" && e.key !== " ") return;
                    // Space would otherwise scroll the page, as it does for any other focused,
                    // non-form element.
                    e.preventDefault();
                    onSelect(request);
                  }}
                  sx={(theme) => ({
                    cursor: "pointer",
                    "&:focus-visible": {
                      outline: `2px solid ${theme.palette.primary.main}`,
                      outlineOffset: "-2px",
                    },
                  })}
                >
                  <TableCell>{renderColumnCell(request, "sample")}</TableCell>
                  <TableCell>{renderColumnCell(request, "requester")}</TableCell>
                  <TableCell>{renderColumnCell(request, adjustableColumn)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        {!loading && searchedRequests.length === 0 && (
          <Typography variant="body2" sx={{ p: 2 }} color="text.secondary">
            {t("requestsManagement.noResults")}
          </Typography>
        )}
      </Box>
      {searchedRequests.length > 0 && (
        <nav>
          <TablePagination
            sx={{ overflow: "unset" }}
            labelRowsPerPage=""
            component="div"
            count={searchedRequests.length}
            rowsPerPageOptions={paginationOptions(searchedRequests.length, REQUEST_PAGE_SIZES, REQUEST_ALL_THRESHOLD)}
            rowsPerPage={Math.min(pageSize, searchedRequests.length)}
            page={pageNumber}
            onPageChange={(_event: unknown, page: number) => setPageNumber(page)}
            onRowsPerPageChange={(e) => setPageSize(Number(e.target.value))}
            slotProps={{
              select: {
                renderValue: (value: unknown) =>
                  typeof value === "number" && value < searchedRequests.length
                    ? value
                    : t("search.resultsTable.allRows", { count: String(value) }),
              },
            }}
          />
        </nav>
      )}
    </Box>
  );
}
