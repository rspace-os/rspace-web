import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import FilterListIcon from "@mui/icons-material/FilterList";
import TableChartIcon from "@mui/icons-material/TableChart";
import VisibilityIcon from "@mui/icons-material/Visibility";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import CircularProgress from "@mui/material/CircularProgress";
import Collapse from "@mui/material/Collapse";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TablePagination from "@mui/material/TablePagination";
import TableRow, { tableRowClasses } from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";

const DBREPO_LOGO_PATH = "/images/icons/dbrepo.svg";

export type DBRepoDatabase = {
  id: string;
  name: string;
  description?: string;
  url: string;
};

type DBRepoResourceType = "table" | "view" | "subset";

type DBRepoLinkedResource = {
  id: string;
  type: DBRepoResourceType;
  label: string;
  secondaryText?: string;
  url: string;
};

type DBRepoDatabaseResources = {
  databaseId: string;
  tables: DBRepoLinkedResource[];
  views: DBRepoLinkedResource[];
  subsets: DBRepoLinkedResource[];
  failedTypes: DBRepoResourceType[];
};

type ResourceState = {
  loading: boolean;
  data?: DBRepoDatabaseResources;
  error?: string;
};

type DBRepoColumn = {
  id: string;
  name: string;
  internalName: string;
  type: string;
  size?: number;
};

type DBRepoResourceMetadata = {
  id: string;
  type: "table" | "view";
  name: string;
  query: string;
  columns: DBRepoColumn[];
};

type DBRepoRowPage = {
  rows: DBRepoRow[];
  page: number;
  size: number;
  totalCount: number | null;
};

type DBRepoRow = Record<string, unknown>;

type TemplateTarget = {
  name: string;
  url: string;
  dbrepoType: "database" | DBRepoResourceType;
  databaseId: string;
  resourceId: string;
  databaseName: string;
  query: string;
};

type RowTemplateTarget = TemplateTarget & { dbrepoType: "table" | "view" };

type TinyMceEditor = {
  getBody: () => HTMLElement;
  execCommand: (command: string, ui: boolean, value?: string) => void;
  on?: (eventName: string, callback: () => void) => void;
  off?: (eventName: string, callback?: () => void) => void;
  windowManager: {
    close: () => void;
  };
};

export type DBRepoLinkTemplateData = {
  id: string;
  recordURL: string;
  name: string;
  dbrepoType: "database" | DBRepoResourceType;
  databaseId: string;
  resourceId: string;
  databaseName: string;
  query: string;
  iconPath: string;
};

type ParentWindow = {
  tinymce?: { activeEditor?: TinyMceEditor };
  RS?: {
    insertTemplateIntoTinyMCE?: (templateId: string, data: DBRepoLinkTemplateData, editor?: TinyMceEditor) => void;
  };
};

const RESOURCE_GROUPS: Array<{
  type: DBRepoResourceType;
  key: "tables" | "views" | "subsets";
  accent: string;
  Icon: typeof TableChartIcon;
}> = [
  { type: "table", key: "tables", accent: "#1565c0", Icon: TableChartIcon },
  { type: "view", key: "views", accent: "#6a1b9a", Icon: VisibilityIcon },
  { type: "subset", key: "subsets", accent: "#2e7d32", Icon: FilterListIcon },
];

function hashString(value: string): number {
  return value.split("").reduce((hash, character) => {
    return ((hash << 5) - hash + character.charCodeAt(0)) | 0;
  }, 0);
}

export function buildDBRepoLinkTemplateData(target: TemplateTarget): DBRepoLinkTemplateData {
  return {
    id: `dbrepo-${hashString(target.url)}`,
    recordURL: target.url,
    name: target.name,
    dbrepoType: target.dbrepoType,
    databaseId: target.databaseId,
    resourceId: target.resourceId,
    databaseName: target.databaseName,
    query: target.query,
    iconPath: DBREPO_LOGO_PATH,
  };
}

function DBRepo(): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [databases, setDatabases] = useState<DBRepoDatabase[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [expandedDatabaseId, setExpandedDatabaseId] = useState("");
  const [resourcesByDatabase, setResourcesByDatabase] = useState<Record<string, ResourceState>>({});
  const [rowPickerTarget, setRowPickerTarget] = useState<TemplateTarget | undefined>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    void axios
      .get<DBRepoDatabase[]>("/apps/dbrepo/databases")
      .then(({ data }) => {
        if (cancelled) return;
        setDatabases(data);
        setSelectedId(data[0]?.id ? databaseSelectionId(data[0].id) : "");
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : t("tinymce.dbrepo.errors.load"));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [t]);

  const loadResources = (databaseId: string) => {
    const existing = resourcesByDatabase[databaseId];
    if (existing?.loading || existing?.data) return;
    setResourcesByDatabase((current) => ({
      ...current,
      [databaseId]: { loading: true },
    }));
    void axios
      .get<DBRepoDatabaseResources>(`/apps/dbrepo/databases/${encodeURIComponent(databaseId)}/resources`)
      .then(({ data }) => {
        setResourcesByDatabase((current) => ({
          ...current,
          [databaseId]: { loading: false, data },
        }));
      })
      .catch((e: unknown) => {
        setResourcesByDatabase((current) => ({
          ...current,
          [databaseId]: {
            loading: false,
            error: e instanceof Error ? e.message : t("tinymce.dbrepo.errors.resources"),
          },
        }));
      });
  };

  const toggleDatabase = (databaseId: string) => {
    const nextExpandedDatabaseId = expandedDatabaseId === databaseId ? "" : databaseId;
    setSelectedId(databaseSelectionId(databaseId));
    setExpandedDatabaseId(nextExpandedDatabaseId);
    if (nextExpandedDatabaseId) {
      loadResources(nextExpandedDatabaseId);
    }
  };

  const selectedTarget = selectedTemplateTarget(selectedId, databases, resourcesByDatabase);
  const canInsertRows = selectedTarget?.dbrepoType === "table" || selectedTarget?.dbrepoType === "view";

  const insertSelectedDatabase = useCallback(() => {
    const parentWindow = parent as unknown as ParentWindow;
    const editor = parentWindow.tinymce?.activeEditor;
    const insertTemplateIntoTinyMCE = parentWindow.RS?.insertTemplateIntoTinyMCE;
    if (!selectedTarget || !editor) return;
    insertTemplateIntoTinyMCE?.("dbrepoLink", buildDBRepoLinkTemplateData(selectedTarget), editor);
    editor.windowManager.close();
  }, [selectedTarget]);

  const openRowPicker = useCallback(() => {
    if (canInsertRows) {
      setRowPickerTarget(selectedTarget);
    }
  }, [canInsertRows, selectedTarget]);

  useEffect(() => {
    window.parent.postMessage(
      {
        mceAction: selectedTarget && !rowPickerTarget ? "enable" : "disable",
        rowsAction: canInsertRows && !rowPickerTarget ? "enable" : "disable",
      },
      "*",
    );
  }, [canInsertRows, rowPickerTarget, selectedTarget]);

  useEffect(() => {
    const parentWindow = parent as unknown as ParentWindow;
    const editor = parentWindow.tinymce?.activeEditor;
    editor?.off?.("dbrepo-insert");
    editor?.off?.("dbrepo-insert-rows");
    editor?.on?.("dbrepo-insert", insertSelectedDatabase);
    editor?.on?.("dbrepo-insert-rows", openRowPicker);
    return () => {
      editor?.off?.("dbrepo-insert", insertSelectedDatabase);
      editor?.off?.("dbrepo-insert-rows", openRowPicker);
    };
  }, [insertSelectedDatabase, openRowPicker]);

  if (loading) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", p: 2 }}>
        <CircularProgress size={20} />
        <Typography>{t("tinymce.dbrepo.loading")}</Typography>
      </Stack>
    );
  }

  if (error) {
    return (
      <Alert severity="error" sx={{ m: 2 }}>
        {t("tinymce.dbrepo.errors.load")}
      </Alert>
    );
  }

  if (databases.length === 0) {
    return (
      <Alert severity="info" sx={{ m: 2 }}>
        {t("tinymce.dbrepo.empty")}
      </Alert>
    );
  }

  return (
    <Stack spacing={2} sx={{ p: 2 }}>
      <RadioGroup
        aria-label={t("tinymce.dbrepo.databaseList")}
        value={selectedId}
        onChange={({ target: { value } }) => setSelectedId(value)}
      >
        {databases.map((database) => (
          <Box
            key={database.id}
            sx={{
              border: "1px solid",
              borderColor: "divider",
              borderRadius: 1,
              mb: 1,
              overflow: "hidden",
            }}
          >
            <Stack direction="row" spacing={1} sx={{ alignItems: "center", px: 1 }}>
              <FormControlLabel
                value={databaseSelectionId(database.id)}
                control={<Radio />}
                label={
                  <Stack>
                    <Typography>{database.name}</Typography>
                    {database.description && <Typography variant="caption">{database.description}</Typography>}
                  </Stack>
                }
                sx={{ flex: 1, mr: 0 }}
              />
              <IconButton
                aria-label={t(
                  expandedDatabaseId === database.id
                    ? "tinymce.dbrepo.collapseDatabase"
                    : "tinymce.dbrepo.expandDatabase",
                  { name: database.name },
                )}
                aria-expanded={expandedDatabaseId === database.id}
                onClick={() => toggleDatabase(database.id)}
                size="small"
              >
                <ExpandMoreIcon
                  sx={{
                    transform: expandedDatabaseId === database.id ? "rotate(180deg)" : "rotate(0deg)",
                    transition: "transform 150ms ease-in-out",
                  }}
                />
              </IconButton>
            </Stack>
            <Collapse in={expandedDatabaseId === database.id}>
              <Divider />
              <DatabaseResources
                databaseId={database.id}
                resourceState={resourcesByDatabase[database.id]}
                selectedId={selectedId}
                t={t}
              />
            </Collapse>
          </Box>
        ))}
      </RadioGroup>
      {rowPickerTarget && isRowTemplateTarget(rowPickerTarget) && (
        <DBRepoRowPicker
          target={rowPickerTarget}
          onClose={() => setRowPickerTarget(undefined)}
          onInserted={() => {
            const parentWindow = parent as unknown as ParentWindow;
            parentWindow.tinymce?.activeEditor?.windowManager.close();
          }}
        />
      )}
    </Stack>
  );
}

function DBRepoRowPicker({
  target,
  onClose,
  onInserted,
}: {
  target: RowTemplateTarget;
  onClose: () => void;
  onInserted: () => void;
}): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  const [metadata, setMetadata] = useState<DBRepoResourceMetadata | undefined>();
  const [rows, setRows] = useState<DBRepoRow[]>([]);
  const [selectedRows, setSelectedRows] = useState<Map<string, DBRepoRow>>(new Map());
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalCount, setTotalCount] = useState<number | null>(null);
  const [loadingMetadata, setLoadingMetadata] = useState(true);
  const [loadingRows, setLoadingRows] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoadingMetadata(true);
    setError("");
    void axios
      .get<DBRepoResourceMetadata>(resourceApiPath(target, "metadata"))
      .then(({ data }) => {
        if (!cancelled) setMetadata(data);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : t("tinymce.dbrepo.rows.metadataError"));
      })
      .finally(() => {
        if (!cancelled) setLoadingMetadata(false);
      });
    return () => {
      cancelled = true;
    };
  }, [target, t]);

  useEffect(() => {
    let cancelled = false;
    setLoadingRows(true);
    setError("");
    void axios
      .get<DBRepoRowPage>(resourceApiPath(target, "rows"), {
        params: { page, size: rowsPerPage },
      })
      .then(({ data }) => {
        if (cancelled) return;
        setRows(data.rows);
        setTotalCount(data.totalCount);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : t("tinymce.dbrepo.rows.rowsError"));
      })
      .finally(() => {
        if (!cancelled) setLoadingRows(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, rowsPerPage, target, t]);

  const columns = metadata?.columns ?? [];
  const selectedRowKeys = new Set(selectedRows.keys());

  const toggleRow = (key: string, row: DBRepoRow) => {
    setSelectedRows((current) => {
      const next = new Map(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.set(key, row);
      }
      return next;
    });
  };

  const toggleCurrentPage = (checked: boolean) => {
    setSelectedRows((current) => {
      const next = new Map(current);
      rows.forEach((row, index) => {
        const key = rowKey(page, index, row);
        if (checked) {
          next.set(key, row);
        } else {
          next.delete(key);
        }
      });
      return next;
    });
  };

  const insertRows = () => {
    const parentWindow = parent as unknown as ParentWindow;
    const editor = parentWindow.tinymce?.activeEditor;
    if (!editor || !metadata) return;
    const table = createDBRepoTinyMceTable(target, metadata, Array.from(selectedRows.values()));
    editor.execCommand("mceInsertContent", false, table.outerHTML);
    onInserted();
  };

  const selectedOnPage = rows.filter((row, index) => selectedRowKeys.has(rowKey(page, index, row))).length;
  const count = totalCount ?? -1;

  return (
    <Dialog
      open={true}
      onClose={onClose}
      fullWidth
      maxWidth="xl"
      slotProps={{
        paper: {
          sx: {
            height: "calc(100vh - 32px)",
            maxHeight: "max-content",
          },
        },
      }}
    >
      <DialogTitle sx={{ pb: 1 }}>{t("tinymce.dbrepo.rows.dialogTitle", { name: target.name })}</DialogTitle>
      <DialogContent sx={{ display: "flex", flexDirection: "column", minHeight: 0, overflow: "hidden", pt: 1 }}>
        <Stack spacing={1} sx={{ flex: 1, minHeight: 0 }}>
          <Typography variant="body2" color="text.secondary">
            {t("tinymce.dbrepo.rows.resourceContext", {
              name: <b>{target.name}</b>,
              database: <b>{target.databaseName}</b>,
            })}
          </Typography>
          {error && <Alert severity="error">{error}</Alert>}
          {(loadingMetadata || loadingRows) && (
            <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
              <CircularProgress size={18} />
              <Typography variant="body2">{t("tinymce.dbrepo.rows.loading")}</Typography>
            </Stack>
          )}
          {!loadingMetadata && columns.length === 0 && (
            <Alert severity="warning">{t("tinymce.dbrepo.rows.noColumns")}</Alert>
          )}
          {metadata && columns.length > 0 && (
            <DBRepoRowsTable
              columns={columns}
              rows={rows}
              page={page}
              selectedRowKeys={selectedRowKeys}
              selectedOnPage={selectedOnPage}
              totalSelected={selectedRows.size}
              rowsPerPage={rowsPerPage}
              count={count}
              loading={loadingRows}
              onToggleRow={toggleRow}
              onToggleCurrentPage={toggleCurrentPage}
              onPageChange={setPage}
              onRowsPerPageChange={(nextRowsPerPage) => {
                setRowsPerPage(nextRowsPerPage);
                setPage(0);
              }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          disabled={!metadata || columns.length === 0 || selectedRows.size === 0}
          color="primary"
          variant="contained"
          onClick={insertRows}
        >
          {t("tinymce.dbrepo.rows.insert")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function DBRepoRowsTable({
  columns,
  rows,
  page,
  selectedRowKeys,
  selectedOnPage,
  totalSelected,
  rowsPerPage,
  count,
  loading,
  onToggleRow,
  onToggleCurrentPage,
  onPageChange,
  onRowsPerPageChange,
}: {
  columns: DBRepoColumn[];
  rows: DBRepoRow[];
  page: number;
  selectedRowKeys: Set<string>;
  selectedOnPage: number;
  totalSelected: number;
  rowsPerPage: number;
  count: number;
  loading: boolean;
  onToggleRow: (key: string, row: DBRepoRow) => void;
  onToggleCurrentPage: (checked: boolean) => void;
  onPageChange: (page: number) => void;
  onRowsPerPageChange: (rowsPerPage: number) => void;
}): React.ReactNode {
  const { t } = useTranslation(["workspace", "common"]);
  return (
    <Box sx={{ display: "flex", flex: 1, flexDirection: "column", minHeight: 0 }}>
      <TableContainer sx={{ flex: 1, minHeight: 0 }}>
        <Table stickyHeader aria-label={t("tinymce.dbrepo.rows.tableLabel")}>
          <TableHead>
            <TableRow sx={{ background: "#F6F6F6" }}>
              <TableCell padding="checkbox">
                <Checkbox
                  color="primary"
                  checked={rows.length > 0 && selectedOnPage === rows.length}
                  indeterminate={selectedOnPage > 0 && selectedOnPage < rows.length}
                  disabled={rows.length === 0 || loading}
                  slotProps={{
                    input: { "aria-label": t("tinymce.dbrepo.rows.selectCurrentPage") },
                  }}
                  onChange={({ target: { checked } }) => onToggleCurrentPage(checked)}
                />
              </TableCell>
              {columns.map((column) => (
                <TableCell key={columnKey(column)}>
                  <Stack>
                    <Typography variant="body2">
                      <b>{column.name}</b>
                    </Typography>
                    {column.type && (
                      <Typography variant="caption" color="text.secondary">
                        {column.size ? `${column.type}(${column.size})` : column.type}
                      </Typography>
                    )}
                  </Stack>
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row, index) => {
              const key = rowKey(page, index, row);
              const selected = selectedRowKeys.has(key);
              return (
                <TableRow
                  key={key}
                  sx={{
                    [`&.${tableRowClasses.selected}`]: {
                      backgroundColor: "#e3f2fd",
                    },
                    [`&.${tableRowClasses.selected}:hover`]: {
                      backgroundColor: "#e3f2fd",
                    },
                  }}
                  hover
                  tabIndex={-1}
                  role="checkbox"
                  onClick={() => onToggleRow(key, row)}
                  aria-checked={selected}
                  selected={selected}
                >
                  <TableCell padding="checkbox">
                    <Checkbox color="primary" checked={selected} />
                  </TableCell>
                  {columns.map((column) => (
                    <TableCell key={columnKey(column)}>{stringValue(valueForColumn(row, column))}</TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          width: "100%",
          backgroundColor: "#f6f6f6",
        }}
      >
        <Typography sx={{ pl: "16px" }} component="span" variant="body2" color="textPrimary">
          {t("tinymce.dbrepo.rows.selectedCount", { count: totalSelected })}
        </Typography>
        <TablePagination
          rowsPerPageOptions={[5, 10, 25, 50]}
          component="div"
          count={count}
          rowsPerPage={rowsPerPage}
          page={page}
          onPageChange={(_, nextPage) => onPageChange(nextPage)}
          onRowsPerPageChange={(event) => onRowsPerPageChange(Number(event.target.value))}
        />
      </Box>
    </Box>
  );
}

function DatabaseResources({
  databaseId,
  resourceState,
  selectedId,
  t,
}: {
  databaseId: string;
  resourceState?: ResourceState;
  selectedId: string;
  t: ReturnType<typeof useTranslation<["workspace", "common"]>>["t"];
}): React.ReactNode {
  if (resourceState?.loading || !resourceState) {
    return (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center", p: 1.5, pl: 4 }}>
        <CircularProgress size={16} />
        <Typography variant="body2">{t("tinymce.dbrepo.loadingResources")}</Typography>
      </Stack>
    );
  }

  if (resourceState.error) {
    return (
      <Alert severity="error" sx={{ m: 1.5 }}>
        {t("tinymce.dbrepo.errors.resources")}
      </Alert>
    );
  }

  const data = resourceState.data;
  if (!data) return null;

  return (
    <Stack spacing={1.5} sx={{ p: 1.5, pl: 4 }}>
      {RESOURCE_GROUPS.map(({ type, key, accent, Icon }) => {
        const resources = data[key];
        const failed = data.failedTypes.includes(type);
        return (
          <Stack key={type} spacing={0.5}>
            <Stack direction="row" spacing={1} sx={{ alignItems: "center", color: accent }}>
              <Icon fontSize="small" />
              <Typography variant="subtitle2">{t(`tinymce.dbrepo.categories.${key}`)}</Typography>
              <Typography variant="caption">
                {t("tinymce.dbrepo.categoryCount", { count: resources.length })}
              </Typography>
            </Stack>
            {failed && (
              <Alert severity="warning" sx={{ py: 0 }}>
                {t(`tinymce.dbrepo.errors.${type}`)}
              </Alert>
            )}
            {resources.length === 0 && !failed && (
              <Typography variant="caption" color="text.secondary">
                {t("tinymce.dbrepo.emptyCategory")}
              </Typography>
            )}
            {resources.map((resource) => (
              <FormControlLabel
                key={resourceSelectionId(databaseId, resource)}
                value={resourceSelectionId(databaseId, resource)}
                control={<Radio size="small" />}
                checked={selectedId === resourceSelectionId(databaseId, resource)}
                label={
                  <Stack>
                    <Typography variant="body2">{resource.label}</Typography>
                    {resource.secondaryText && (
                      <Typography
                        variant="caption"
                        sx={{
                          color: "text.secondary",
                          fontFamily: "monospace",
                          overflowWrap: "anywhere",
                        }}
                      >
                        {resource.secondaryText}
                      </Typography>
                    )}
                  </Stack>
                }
                sx={{ ml: 0 }}
              />
            ))}
          </Stack>
        );
      })}
    </Stack>
  );
}

function databaseSelectionId(databaseId: string): string {
  return `database:${databaseId}`;
}

function resourceSelectionId(databaseId: string, resource: DBRepoLinkedResource): string {
  return `${resource.type}:${databaseId}:${resource.id}`;
}

function isRowTemplateTarget(target: TemplateTarget | undefined): target is RowTemplateTarget {
  return target?.dbrepoType === "table" || target?.dbrepoType === "view";
}

function resourceApiPath(target: RowTemplateTarget, suffix: "metadata" | "rows"): string {
  return `/apps/dbrepo/databases/${encodeURIComponent(target.databaseId)}/${target.dbrepoType}/${encodeURIComponent(
    target.resourceId,
  )}/${suffix}`;
}

function rowKey(page: number, index: number, row: DBRepoRow): string {
  return `${page}:${index}:${JSON.stringify(row)}`;
}

function columnKey(column: DBRepoColumn): string {
  return column.id || column.internalName || column.name;
}

function valueForColumn(row: DBRepoRow, column: DBRepoColumn): unknown {
  if (hasOwn(row, column.name)) {
    return row[column.name];
  }
  if (hasOwn(row, column.internalName)) {
    return row[column.internalName];
  }
  if (hasOwn(row, column.id)) {
    return row[column.id];
  }
  return undefined;
}

function hasOwn(row: DBRepoRow, key: string): boolean {
  return key ? Object.hasOwn(row, key) : false;
}

function stringValue(value: unknown): string {
  if (value === null || typeof value === "undefined") {
    return "";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export function createDBRepoTinyMceTable(
  target: RowTemplateTarget,
  metadata: DBRepoResourceMetadata,
  selectedRows: DBRepoRow[],
): HTMLTableElement {
  const dbrepoTable = document.createElement("table");
  dbrepoTable.setAttribute("data-tableSource", "dbrepo");
  dbrepoTable.style.fontSize = "1em";

  const linkRow = document.createElement("tr");
  const linkCell = document.createElement("th");
  linkCell.style.fontSize = "0.7em";
  linkCell.appendChild(document.createTextNode("Imported from DBRepo "));
  linkCell.appendChild(document.createTextNode(`${target.dbrepoType} `));
  const anchor = document.createElement("a");
  anchor.href = target.url;
  anchor.rel = "noreferrer";
  anchor.textContent = `${target.name} (${target.databaseName})`;
  linkCell.appendChild(anchor);
  linkCell.appendChild(document.createTextNode(" on "));
  linkCell.appendChild(document.createTextNode(new Date().toDateString()));
  linkCell.appendChild(document.createTextNode(" "));
  linkCell.appendChild(document.createTextNode(new Date().toLocaleTimeString()));
  linkCell.colSpan = metadata.columns.length;
  linkCell.style.fontWeight = "400";
  linkRow.appendChild(linkCell);
  dbrepoTable.appendChild(linkRow);

  const tableHeader = document.createElement("tr");
  metadata.columns.forEach((column) => {
    const columnName = document.createElement("th");
    columnName.textContent = column.name;
    tableHeader.appendChild(columnName);
  });
  dbrepoTable.appendChild(tableHeader);

  selectedRows.forEach((selectedRow) => {
    const row = document.createElement("tr");
    metadata.columns.forEach((column) => {
      const cell = document.createElement("td");
      cell.textContent = stringValue(valueForColumn(selectedRow, column));
      row.appendChild(cell);
    });
    dbrepoTable.appendChild(row);
  });

  return dbrepoTable;
}

function selectedTemplateTarget(
  selectedId: string,
  databases: DBRepoDatabase[],
  resourcesByDatabase: Record<string, ResourceState>,
): TemplateTarget | undefined {
  if (selectedId.startsWith("database:")) {
    const databaseId = selectedId.slice("database:".length);
    const database = databases.find((candidate) => candidate.id === databaseId);
    return database
      ? {
          name: database.name,
          url: database.url,
          dbrepoType: "database",
          databaseId: database.id,
          resourceId: "",
          databaseName: database.name,
          query: "",
        }
      : undefined;
  }
  for (const [databaseId, resourceState] of Object.entries(resourcesByDatabase)) {
    const database = databases.find((candidate) => candidate.id === databaseId);
    const resources = [
      ...(resourceState.data?.tables ?? []),
      ...(resourceState.data?.views ?? []),
      ...(resourceState.data?.subsets ?? []),
    ];
    const resource = resources.find((candidate) => selectedId === resourceSelectionId(databaseId, candidate));
    if (resource) {
      return {
        name: resource.label,
        url: resource.url,
        dbrepoType: resource.type,
        databaseId,
        resourceId: resource.id,
        databaseName: database?.name ?? "",
        query: resource.type === "subset" ? resource.label : (resource.secondaryText ?? ""),
      };
    }
  }
  return undefined;
}

export default DBRepo;
