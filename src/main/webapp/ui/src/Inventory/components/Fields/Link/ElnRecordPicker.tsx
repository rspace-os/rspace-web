import React, { useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import List from "@mui/material/List";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import ListItemIcon from "@mui/material/ListItemIcon";
import Alert from "@mui/material/Alert";
import CircularProgress from "@mui/material/CircularProgress";
import Typography from "@mui/material/Typography";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import axios from "@/common/axios";
import * as Parsers from "../../../../util/parsers";
import Result from "../../../../util/result";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import { iconForGlobalId, isElnGlobalId } from "./iconForGlobalId";
import ElnFolderBrowser from "./ElnFolderBrowser";

/** The workspace simpleSearch endpoint caps its result set at this many records. */
const SIMPLE_SEARCH_CAP = 50;

export type ElnRecordPickerResult = {
  globalId: string;
  name: string;
  type: string;
};

export interface ElnRecordPickerProps {
  open: boolean;
  onPick: (target: ElnRecordPickerResult) => void;
  onCancel: () => void;
}

type SearchRow = { globalId: string; name: string; type: string };

/**
 * Parses a /workspace/ajax/simpleSearch response. The endpoint returns an
 * AjaxReturnObject ({ data: RecordInformation[], error }); each RecordInformation
 * carries its Global ID under oid.idString and a human-readable display type.
 * Gallery files are not part of the workspace folder tree, so search is the only
 * way to reach them as link targets.
 */
function parseSearchResponse(payload: unknown): {
  elnRows: ReadonlyArray<SearchRow>;
  rawCount: number;
} {
  const rows: Array<SearchRow> = [];
  let rawCount = 0;
  Parsers.objectPath(["data"], payload)
    .flatMap(Parsers.isArray)
    .do((arr) => {
      rawCount = arr.length;
      for (const item of arr) {
        Parsers.isObject(item)
          .flatMap(Parsers.isNotNull)
          .flatMap((obj) => {
            const globalId = Parsers.getValueWithKey("oid")(obj)
              .flatMap(Parsers.isObject)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("idString"))
              .flatMap(Parsers.isString);
            const name = Parsers.getValueWithKey("name")(obj).flatMap(
              Parsers.isString,
            );
            const type = Parsers.getValueWithKey("type")(obj).flatMap(
              Parsers.isString,
            );
            return Result.all(globalId, name, type).map(([g, n, t]) => ({
              globalId: g,
              name: n,
              type: t,
            }));
          })
          .do((row) => rows.push(row));
      }
    });
  return { elnRows: rows.filter((r) => isElnGlobalId(r.globalId)), rawCount };
}

/**
 * A search-driven picker for ELN link targets (documents, notebooks, gallery files).
 * Decoupled from TinyMCE and MobX: it takes plain props and returns the chosen target
 * via onPick, so it can be reused outside the Inventory dialog later.
 */
export default function ElnRecordPicker(
  props: ElnRecordPickerProps,
): React.ReactElement {
  const [tab, setTab] = useState<"search" | "browse">("search");
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<ReadonlyArray<SearchRow>>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searched, setSearched] = useState(false);
  const [truncated, setTruncated] = useState(false);

  const runSearch = async () => {
    if (query.trim() === "") return;
    setLoading(true);
    setError(null);
    setSearched(true);
    try {
      const { data } = await axios.get<unknown>(
        "/workspace/ajax/simpleSearch",
        { params: { searchQuery: query } },
      );
      const { elnRows, rawCount } = parseSearchResponse(data);
      setRows(elnRows);
      setTruncated(rawCount >= SIMPLE_SEARCH_CAP);
    } catch {
      setError("Search failed. Please try again.");
      setRows([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      open={props.open}
      onClose={props.onCancel}
      fullWidth
      maxWidth="sm"
      aria-label="Browse the ELN for a link target"
    >
      <DialogTitle>Browse ELN</DialogTitle>
      <DialogContent dividers sx={{ minHeight: "40vh" }}>
        <Tabs
          value={tab}
          onChange={(_event, value: "search" | "browse") => setTab(value)}
          aria-label="ELN target source"
          sx={{ mb: 1 }}
        >
          <Tab value="search" label="Search" />
          <Tab value="browse" label="Browse" />
        </Tabs>

        {tab === "browse" ? (
          <ElnFolderBrowser onPick={props.onPick} />
        ) : (
          <>
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault();
            void runSearch();
          }}
        >
          <Stack direction="row" spacing={1} alignItems="flex-end">
            <TextField
              fullWidth
              size="small"
              variant="standard"
              label="Search the ELN by name or global ID"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              inputProps={{ "aria-label": "Search the ELN" }}
            />
            <Button type="submit" variant="outlined" disabled={loading}>
              Search
            </Button>
          </Stack>
        </Box>

        {loading && (
          <Box sx={{ display: "flex", justifyContent: "center", mt: 3 }}>
            <CircularProgress size={28} />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {error}
          </Alert>
        )}

        {truncated && !loading && (
          <Alert severity="info" sx={{ mt: 2 }}>
            Showing the first {SIMPLE_SEARCH_CAP} results. Refine your search to
            narrow them down.
          </Alert>
        )}

        {!loading && searched && !error && rows.length === 0 && (
          <Typography variant="body2" sx={{ mt: 2 }} color="text.secondary">
            No matching ELN documents, notebooks or gallery files found.
          </Typography>
        )}

        {rows.length > 0 && (
          <List aria-label="ELN search results">
            {rows.map((row) => {
              const icon = iconForGlobalId(row.globalId);
              return (
                <ListItemButton
                  key={row.globalId}
                  onClick={() => props.onPick(row)}
                >
                  {icon && (
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <RecordTypeIcon record={icon} aria-hidden />
                    </ListItemIcon>
                  )}
                  <ListItemText primary={row.name} secondary={row.globalId} />
                </ListItemButton>
              );
            })}
          </List>
        )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onCancel}>Cancel</Button>
      </DialogActions>
    </Dialog>
  );
}
