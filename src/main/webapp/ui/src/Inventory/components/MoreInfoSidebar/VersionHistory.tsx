import React, { useState, useEffect, useContext } from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Table from "@mui/material/Table";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import Alert from "@mui/material/Alert";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Skeleton from "@mui/material/Skeleton";
import ApiService from "../../../common/InvApiService";
import NavigateContext from "../../../stores/contexts/Navigate";
import { getErrorMessage } from "../../../util/error";
import { isoToLocale } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type RevisionsListResponse = {
  revisions: Array<{
    revisionId: number;
    revisionType: string;
    record: {
      version?: number | null;
      lastModified?: string | null;
      modifiedByFullName?: string | null;
    };
  }>;
  revisionsCount: number;
};

type VersionRow = {
  version: number;
  lastModified: string | null;
  modifiedByFullName: string | null;
};

type State =
  | { state: "init" }
  | { state: "loading" }
  | { state: "success"; versions: Array<VersionRow> }
  | { state: "fail"; error: Error };

/**
 * Groups the raw revisions list by user-facing version, keeping the newest
 * revision of each version (non-version-bumping edits create several
 * revisions sharing a version; the version's final state is the relevant
 * one). Revisions are sorted by ascending revisionId first so "last write
 * wins" holds regardless of the order the endpoint returns them in. Returns
 * the versions newest first.
 */
function groupByVersion(response: RevisionsListResponse): Array<VersionRow> {
  const byVersion = new Map<number, VersionRow>();
  const oldestFirst = [...response.revisions].sort(
    (a, b) => a.revisionId - b.revisionId,
  );
  for (const revision of oldestFirst) {
    const version = revision.record.version;
    if (version === null || typeof version === "undefined") continue;
    byVersion.set(version, {
      version,
      lastModified: revision.record.lastModified ?? null,
      modifiedByFullName: revision.record.modifiedByFullName ?? null,
    });
  }
  return [...byVersion.values()].sort((a, b) => b.version - a.version);
}

function DialogContents({
  state,
  currentVersion,
  historical,
  versionUrl,
  onNavigate,
}: {
  state: State;
  currentVersion: number | null;
  historical: boolean;
  versionUrl: (version: number) => string;
  onNavigate: (url: string) => void;
}): React.ReactNode {
  if (state.state === "loading")
    return <Skeleton variant="rectangular" width="100%" height={118} />;
  if (state.state === "fail")
    return <Alert severity="error">{state.error.message}</Alert>;
  if (state.state === "success") {
    if (state.versions.length === 0)
      return (
        <Alert severity="info">
          No version history is available for this item yet.
        </Alert>
      );
    return (
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Version</TableCell>
            <TableCell>Modified</TableCell>
            <TableCell>By</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {state.versions.map((row) => (
            <TableRow key={row.version}>
              <TableCell>
                <Link
                  href={versionUrl(row.version)}
                  onClick={(e: React.MouseEvent) => {
                    e.preventDefault();
                    onNavigate(versionUrl(row.version));
                  }}
                >
                  Version {row.version}
                </Link>
                {/* on a historical view, record.version is the pinned version, not the live one */}
                {row.version === currentVersion &&
                  (historical ? " (viewing)" : " (current)")}
              </TableCell>
              <TableCell>
                {row.lastModified ? isoToLocale(row.lastModified) : "—"}
              </TableCell>
              <TableCell>{row.modifiedByFullName ?? "—"}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    );
  }
  return null;
}

type VersionHistoryArgs = {
  record: InventoryRecord;
};

/**
 * A MoreInfoSidebar row showing the record's current version, with a dialog
 * listing all of its versions; each row opens the read-only versioned viewer.
 */
function VersionHistory({ record }: VersionHistoryArgs): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [state, setState] = useState<State>({ state: "init" });

  const supported =
    ["sample", "subSample", "container", "sampleTemplate"].includes(
      record.recordType,
    ) && record.id !== null;

  useEffect(() => {
    if (open && supported) {
      // ignore responses landing after the record changed or the dialog closed
      let cancelled = false;
      setState({ state: "loading" });
      void (async () => {
        try {
          const { data } = await ApiService.get<RevisionsListResponse>(
            `${record.recordType}s/${record.id}/revisions`,
          );
          if (!cancelled)
            setState({ state: "success", versions: groupByVersion(data) });
        } catch (e) {
          if (!cancelled)
            setState({
              state: "fail",
              error: new Error(
                getErrorMessage(e, "Could not load version history."),
              ),
            });
        }
      })();
      return () => {
        cancelled = true;
      };
    }
  }, [open, supported, record.recordType, record.id]);

  if (!supported) return null;

  const versionUrl = (version: number) =>
    `/inventory/${record.recordType.toLowerCase()}/${record.id}?version=${version}`;

  // reset on close so reopening shows a fresh loading state, not stale content
  const close = () => {
    setOpen(false);
    setState({ state: "init" });
  };

  return (
    <Grid item>
      <FormControl component="fieldset" style={{ alignItems: "flex-start" }}>
        <FormLabel component="legend">Version</FormLabel>
        <FormGroup>
          {record.version ?? "—"}
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              setOpen(true);
            }}
          >
            View version history
          </Button>
          <Dialog open={open} onClose={close}>
            <DialogTitle>Version history</DialogTitle>
            <DialogContent>
              <DialogContents
                state={state}
                currentVersion={record.version ?? null}
                historical={record.historicalVersion ?? false}
                versionUrl={versionUrl}
                onNavigate={(url) => {
                  close();
                  navigate(url);
                }}
              />
            </DialogContent>
            <DialogActions>
              <Button onClick={close}>Close</Button>
            </DialogActions>
          </Dialog>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

export default observer(VersionHistory);
