import React, { useEffect, useState } from "react";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Skeleton from "@mui/material/Skeleton";
import Alert from "@mui/material/Alert";
import ApiService from "../../../../common/InvApiService";
import AlwaysNewFactory from "../../../../stores/models/Factory/AlwaysNewFactory";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import { type GlobalId } from "../../../../stores/definitions/BaseRecord";
import SidebarBody from "../../MoreInfoSidebar/SidebarBody";

export interface InventoryInfoDialogProps {
  open: boolean;
  globalId: string;
  onClose: () => void;
}

const GLOBAL_ID_PATTERN = /^([A-Z]{2})(\d+)(?:v\d+)?$/;
const PREFIX_TO_PATH: Record<string, string> = {
  SA: "samples",
  SS: "subSamples",
  IC: "containers",
  IT: "sampleTemplates",
};

function recordEndpoint(globalId: string): string | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  const segment = PREFIX_TO_PATH[match[1]];
  if (!segment) return null;
  return `${segment}/${match[2]}`;
}

type State =
  | { state: "init" }
  | { state: "loading" }
  | { state: "success"; record: InventoryRecord }
  | { state: "fail"; error: Error };

/**
 * Modal wrapper that loads an inventory record by globalId and renders the
 * MoreInfoSidebar body for it. Reuses the same SidebarBody component as the
 * drawer so both surfaces display identical info.
 */
export default function InventoryInfoDialog(
  props: InventoryInfoDialogProps,
): React.ReactElement | null {
  const [state, setState] = useState<State>({ state: "init" });
  const [factory] = useState(() => new AlwaysNewFactory());

  useEffect(() => {
    if (!props.open) return;
    const endpoint = recordEndpoint(props.globalId);
    if (!endpoint) {
      setState({
        state: "fail",
        error: new Error(`Cannot resolve endpoint for ${props.globalId}`),
      });
      return;
    }
    setState({ state: "loading" });
    void (async () => {
      try {
        const { data } = await ApiService.get<
          Record<string, unknown> & { globalId: GlobalId }
        >(endpoint);
        const record = factory.newRecord(data);
        setState({ state: "success", record });
      } catch (e) {
        setState({ state: "fail", error: e as Error });
      }
    })();
  }, [props.open, props.globalId, factory]);

  if (!props.open) return null;
  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      aria-label={`Info for ${props.globalId}`}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{props.globalId}</DialogTitle>
      <DialogContent>
        {state.state === "loading" && (
          <Skeleton variant="rectangular" width="100%" height={240} />
        )}
        {state.state === "fail" && (
          <Alert severity="error">{state.error.message}</Alert>
        )}
        {state.state === "success" && (
          <SidebarBody record={state.record} factory={factory} />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
