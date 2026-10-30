import Alert, { alertClasses } from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TablePagination from "@mui/material/TablePagination";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { orderBy } from "es-toolkit";
import type React from "react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "@/components/GlobalId";
import UserDetails from "@/components/UserDetails";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import { paginationOptions } from "@/util/table";
import { isoToLocale } from "@/util/Util";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import FilterDropdown, { type FilterOption } from "./FilterDropdown";
import { useSampleRequestsListQuery } from "./queries";
import RequestsSearchbar from "./RequestsSearchbar";
import RequestsStatusChip, { requestStatusLabel } from "./RequestsStatusChip";
import RequestsTableHead, { type AdjustableColumnKey, type ColumnKey, type SortDirection } from "./RequestsTableHead";
import type { ApiSampleRequestListItem, RequestsFilter, StatusFilter } from "./sampleRequest";
import { useClientPagination } from "./useClientPagination";

// Smaller than the main Inventory search's (5, 10, 25, 100): this list is rarely as long.
const REQUEST_PAGE_SIZES = [10, 25, 50];
const DEFAULT_REQUEST_PAGE_SIZE = 25;
// Decoupled from REQUEST_PAGE_SIZES' own max (50): without this, "All" would disappear for any
// count over 50, even though fetching and rendering a few hundred requests at once is still fine.
const REQUEST_ALL_THRESHOLD = 200;

const REQUESTS_FILTER_OPTIONS: ReadonlyArray<FilterOption<RequestsFilter>> = [
  { value: "all", labelKey: "requestsManagement.filters.requests.all" },
  { value: "sent", labelKey: "requestsManagement.filters.requests.sent" },
  { value: "received", labelKey: "requestsManagement.filters.requests.received" },
];
const STATUS_FILTER_OPTIONS: ReadonlyArray<FilterOption<StatusFilter>> = [
  { value: "all", labelKey: "requestsManagement.filters.status.all" },
  { value: "active", labelKey: "requestsManagement.filters.status.active" },
  { value: "past", labelKey: "requestsManagement.filters.status.past" },
];

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

function renderAdjustableCell(request: ApiSampleRequestListItem, column: AdjustableColumnKey): React.ReactNode {
  switch (column) {
    case "status":
      return <RequestsStatusChip status={request.status} />;
    case "id":
      return request.id;
    case "submitted":
      return isoToLocale(request.created);
  }
}

export default function RequestsList({
  selectedRequestId,
  onSelect,
}: {
  selectedRequestId: number | null;
  onSelect: (request: ApiSampleRequestListItem) => void;
}): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [requestsFilter, setRequestsFilter] = useState<RequestsFilter>("all");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [sort, setSort] = useState<{ column: ColumnKey; direction: SortDirection }>({
    column: "submitted",
    direction: "desc",
  });
  const [adjustableColumn, setAdjustableColumn] = useState<AdjustableColumnKey>("status");

  // Refetches on its own whenever a mutation elsewhere invalidates sampleRequestsQueryKeys (e.g.
  // approved/rejected from the detail pane), since the active filters may mean it should appear,
  // disappear, or just show a different status - no manual refresh-counter/event wiring needed.
  const { data: requests = [], isLoading, isError, refetch } = useSampleRequestsListQuery(requestsFilter, statusFilter);

  const searchedRequests = useMemo(() => {
    const lowerCaseQuery = searchQuery.trim().toLowerCase();
    if (!lowerCaseQuery) return requests;
    return requests.filter((request) => requestMatchesQuery(request, lowerCaseQuery));
  }, [requests, searchQuery]);

  const sortedRequests = useMemo(
    () => orderBy(searchedRequests, [(request) => getColumnValue(request, sort.column)], [sort.direction]),
    [searchedRequests, sort],
  );

  const pagination = useClientPagination(sortedRequests, DEFAULT_REQUEST_PAGE_SIZE);
  const pagedRequests = pagination.pagedItems;

  const handleSort = (column: ColumnKey) => {
    setSort((previous) =>
      previous.column === column
        ? { column, direction: previous.direction === "asc" ? "desc" : "asc" }
        : { column, direction: "asc" },
    );
    pagination.reset();
  };

  return (
    <Box
      sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%", minWidth: 0, p: 1 }}
    >
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", width: "100%" }}>
        <RequestsSearchbar
          value={searchQuery}
          onChange={(query) => {
            setSearchQuery(query);
            pagination.reset();
          }}
        />
        <HelpLinkIcon link={helpDocsArticleUrl("sampleRequests")} title={t("requestsManagement.helpTitle")} />
      </Stack>
      <Grid container direction="row" spacing={1} sx={{ pt: 1 }}>
        <FilterDropdown
          chipLabelKey="requestsManagement.chips.requests"
          value={requestsFilter}
          options={REQUESTS_FILTER_OPTIONS}
          onChange={(filter) => {
            setRequestsFilter(filter);
            pagination.reset();
          }}
        />
        <FilterDropdown
          chipLabelKey="requestsManagement.chips.status"
          value={statusFilter}
          options={STATUS_FILTER_OPTIONS}
          onChange={(filter) => {
            setStatusFilter(filter);
            pagination.reset();
          }}
        />
      </Grid>
      <Divider orientation="horizontal" sx={{ my: 0.75 }} />
      {isError ? (
        <Alert
          severity="error"
          role="alert"
          action={
            <Button color="inherit" size="small" onClick={() => void refetch()}>
              {t("common:actions.retry")}
            </Button>
          }
        >
          {t("requestsManagement.loadError")}
        </Alert>
      ) : (
        <>
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
                  sortBy={sort.column}
                  sortDirection={sort.direction}
                  onSort={handleSort}
                  adjustableColumn={adjustableColumn}
                  onAdjustableColumnChange={setAdjustableColumn}
                />
                <TableBody>
                  {pagedRequests.map((request) => (
                    <TableRow
                      key={request.id}
                      hover
                      selected={request.id === selectedRequestId}
                      // aria-selected is only meaningful on a row inside a grid/treegrid (i.e. an
                      // ARIA interactive widget); on a row of a plain table - which is what this
                      // is, structurally - screen readers ignore it. aria-current marks "the
                      // current item in a set" and is valid anywhere, which is the actual
                      // relationship this row has to the rest of the list.
                      aria-current={request.id === selectedRequestId ? "true" : undefined}
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
                      <TableCell>
                        <GlobalId record={new LinkableRecordFromGlobalId(request.sample.globalId)} onClick={() => {}} />
                      </TableCell>
                      <TableCell>
                        <UserDetails
                          userId={request.requester.id}
                          fullName={`${request.requester.firstName} ${request.requester.lastName}`}
                          position={["bottom", "right"]}
                        />
                      </TableCell>
                      <TableCell>{renderAdjustableCell(request, adjustableColumn)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            {!isLoading && searchedRequests.length === 0 && (
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
                rowsPerPageOptions={paginationOptions(
                  searchedRequests.length,
                  REQUEST_PAGE_SIZES,
                  REQUEST_ALL_THRESHOLD,
                )}
                rowsPerPage={Math.min(pagination.pageSize, searchedRequests.length)}
                page={pagination.page}
                onPageChange={(_event: unknown, page: number) => pagination.setPage(page)}
                onRowsPerPageChange={(e) => pagination.setPageSize(Number(e.target.value))}
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
        </>
      )}
    </Box>
  );
}
