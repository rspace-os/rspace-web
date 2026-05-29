import React, { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import Skeleton from "@mui/material/Skeleton";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import Alert from "@mui/material/Alert";
import NoValue from "../../../components/NoValue";
import ApiService from "../../../common/InvApiService";

type Doc = {
  globalId: string;
  name: string;
};

type InventoryReferencingItem = {
  sourceGlobalId: string;
  sourceName: string;
  sourceType: string;
  relationType: string;
  versionPin: number | null;
  modifiedAt: string;
};

type ElnState =
  | { kind: "init" }
  | { kind: "loading" }
  | { kind: "success"; docs: Doc[] }
  | { kind: "fail"; error: Error };

type InvState =
  | { kind: "init" }
  | { kind: "loading" }
  | { kind: "success"; items: InventoryReferencingItem[] }
  | { kind: "fail"; error: Error };

export type ReferencingRecordsProps = {
  globalId: string;
  /** One of "samples", "subSamples", "containers", "instruments" */
  invKind: "samples" | "subSamples" | "containers" | "instruments";
  /** Called with source GlobalID when an inventory referencing row is clicked. */
  onPeek: (sourceGlobalId: string) => void;
};

export default function ReferencingRecords(
  props: ReferencingRecordsProps,
): React.ReactElement {
  const [open, setOpen] = useState(false);
  const [eln, setEln] = useState<ElnState>({ kind: "init" });
  const [inv, setInv] = useState<InvState>({ kind: "init" });

  // numeric id from the GlobalId; the back-ref endpoint takes the inventory id.
  const numericId = (() => {
    const match = /\d+/.exec(props.globalId);
    return match ? match[0] : "";
  })();

  useEffect(() => {
    if (!open) return;
    setEln({ kind: "loading" });
    setInv({ kind: "loading" });

    void (async () => {
      try {
        const response = await ApiService.get<
          Array<{ elnDocument: { globalId: string; name: string } }>
        >(`listOfMaterials/forInventoryItem/${props.globalId}`);
        const docs = response.data.map((d) => ({
          globalId: d.elnDocument.globalId,
          name: d.elnDocument.name,
        }));
        setEln({ kind: "success", docs });
      } catch (e) {
        setEln({ kind: "fail", error: e as Error });
      }
    })();

    void (async () => {
      try {
        const response = await ApiService.get<{
          referencingItems: InventoryReferencingItem[];
        }>(`${props.invKind}/${numericId}/referencingItems`);
        setInv({ kind: "success", items: response.data.referencingItems });
      } catch (e) {
        setInv({ kind: "fail", error: e as Error });
      }
    })();
  }, [open, props.globalId, props.invKind, numericId]);

  return (
    <Grid item>
      <FormControl component="fieldset" style={{ alignItems: "flex-start" }}>
        <FormLabel component="legend">References</FormLabel>
        <FormGroup>
          <Button
            variant="outlined"
            disableElevation
            onClick={() => setOpen(true)}
          >
            Show references
          </Button>
          <Dialog open={open} onClose={() => setOpen(false)} maxWidth="md" fullWidth>
            <DialogTitle>References</DialogTitle>
            <DialogContent>
              <Typography variant="subtitle1">
                Referenced in ELN documents
              </Typography>
              <ElnSection state={eln} />
              <Box mt={3}>
                <Typography variant="subtitle1">
                  Linked from Inventory items
                </Typography>
                <InvSection state={inv} onPeek={props.onPeek} />
              </Box>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setOpen(false)}>Close</Button>
            </DialogActions>
          </Dialog>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

function ElnSection({ state }: { state: ElnState }): React.ReactElement | null {
  if (state.kind === "init") return null;
  if (state.kind === "loading")
    return <Skeleton variant="rectangular" width={210} height={60} />;
  if (state.kind === "fail")
    return <Alert severity="error">{state.error.message}</Alert>;
  if (state.docs.length === 0) return <NoValue label="None" />;
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Document</TableCell>
          <TableCell>Global ID</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {state.docs.map((d) => (
          <TableRow key={d.globalId}>
            <TableCell>{d.name}</TableCell>
            <TableCell>{d.globalId}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

function InvSection({
  state,
  onPeek,
}: {
  state: InvState;
  onPeek: (gid: string) => void;
}): React.ReactElement | null {
  if (state.kind === "init") return null;
  if (state.kind === "loading")
    return <Skeleton variant="rectangular" width={210} height={60} />;
  if (state.kind === "fail")
    return <Alert severity="error">{state.error.message}</Alert>;
  if (state.items.length === 0) return <NoValue label="None" />;
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Item</TableCell>
          <TableCell>Global ID</TableCell>
          <TableCell>Relation</TableCell>
          <TableCell>Modified</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {state.items.map((item) => (
          <TableRow
            key={item.sourceGlobalId}
            hover
            onClick={() => onPeek(item.sourceGlobalId)}
            style={{ cursor: "pointer" }}
          >
            <TableCell>{item.sourceName}</TableCell>
            <TableCell>
              {item.versionPin != null
                ? `${item.sourceGlobalId}v${item.versionPin}`
                : item.sourceGlobalId}
            </TableCell>
            <TableCell>{item.relationType}</TableCell>
            <TableCell>{item.modifiedAt}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
