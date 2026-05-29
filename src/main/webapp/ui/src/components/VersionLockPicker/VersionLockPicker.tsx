import React, { useEffect, useState } from "react";
import Radio from "@mui/material/Radio";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";

export const LATEST_SELECTION = "__latest__" as const;

export interface VersionRecord {
  version: number;
  revisionId: number;
  modificationDate: string;
}

export type VersionLockSelection = number | typeof LATEST_SELECTION;

export interface VersionLockPickerProps {
  recordId: number;
  prefix: string;
  currentSelection: VersionLockSelection;
  fetchVersions: (recordId: number) => Promise<VersionRecord[]>;
  onChange: (selection: VersionLockSelection) => void;
}

export default function VersionLockPicker(
  props: VersionLockPickerProps,
): React.ReactElement {
  const [versions, setVersions] = useState<VersionRecord[]>([]);

  useEffect(() => {
    let cancelled = false;
    props.fetchVersions(props.recordId).then((rows) => {
      if (!cancelled) {
        setVersions(rows);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [props.recordId, props.fetchVersions]);

  const isLatest = props.currentSelection === LATEST_SELECTION;

  return (
    <TableContainer>
      <Table size="small" aria-label="version-lock-picker">
        <TableHead>
          <TableRow>
            <TableCell padding="checkbox" />
            <TableCell>Version</TableCell>
            <TableCell>Modified</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          <TableRow
            hover
            onClick={() => props.onChange(LATEST_SELECTION)}
            data-test-id="VersionLockPicker-latest"
          >
            <TableCell padding="checkbox">
              <Radio checked={isLatest} value={LATEST_SELECTION} />
            </TableCell>
            <TableCell>Latest</TableCell>
            <TableCell />
          </TableRow>
          {versions.map((v) => {
            const checked =
              !isLatest && props.currentSelection === v.version;
            return (
              <TableRow
                key={v.version}
                hover
                onClick={() => props.onChange(v.version)}
                data-test-id={`VersionLockPicker-row-${v.version}`}
              >
                <TableCell padding="checkbox">
                  <Radio checked={checked} value={v.version} />
                </TableCell>
                <TableCell>{`Version ${v.version}`}</TableCell>
                <TableCell>{v.modificationDate}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
