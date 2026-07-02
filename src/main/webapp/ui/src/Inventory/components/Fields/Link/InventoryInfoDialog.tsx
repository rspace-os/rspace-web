import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import ApiService from "../../../../common/InvApiService";
import type { GlobalId } from "../../../../stores/definitions/BaseRecord";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import AlwaysNewFactory from "../../../../stores/models/Factory/AlwaysNewFactory";
import SidebarBody from "../../MoreInfoSidebar/SidebarBody";
import { iconForInventoryGlobalId } from "./iconForGlobalId";

export interface InventoryInfoDialogProps {
  open: boolean;
  globalId: string;
  /**
   * When set, load the historical snapshot at this user-facing version (via the
   * /versions/{n} endpoint added in RSDEV-1141) rather than the live record.
   */
  versionPin?: number | null;
  onClose: () => void;
}

import { GLOBAL_ID_PATTERN, INVENTORY_PREFIX_TO_API_PATH } from "./linkTarget";

function recordEndpoint(globalId: string, versionPin?: number | null): string | null {
  const match = GLOBAL_ID_PATTERN.exec(globalId);
  if (!match) return null;
  const segment = INVENTORY_PREFIX_TO_API_PATH[match[1]];
  if (!segment) return null;
  const base = `${segment}/${match[2]}`;
  return versionPin == null ? base : `${base}/versions/${versionPin}`;
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
export default function InventoryInfoDialog(props: InventoryInfoDialogProps): React.ReactElement | null {
  const { t } = useTranslation(["inventory", "common"]);
  const [state, setState] = useState<State>({ state: "init" });
  const [factory] = useState(() => new AlwaysNewFactory());

  useEffect(() => {
    if (!props.open) return;
    const endpoint = recordEndpoint(props.globalId, props.versionPin);
    if (!endpoint) {
      setState({
        state: "fail",
        error: new Error(`Cannot resolve endpoint for ${props.globalId}`),
      });
      return;
    }
    // Guard against out-of-order resolution: if the props change or the dialog
    // closes before this request resolves, ignore its result so a stale response
    // cannot overwrite newer state (or update state after the dialog is hidden).
    let cancelled = false;
    setState({ state: "loading" });
    void (async () => {
      try {
        const { data } = await ApiService.get<Record<string, unknown> & { globalId: GlobalId }>(endpoint);
        if (cancelled) return;
        const record = factory.newRecord(data);
        setState({ state: "success", record });
      } catch (e) {
        if (cancelled) return;
        setState({ state: "fail", error: e as Error });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [props.open, props.globalId, props.versionPin, factory]);

  if (!props.open) return null;
  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      aria-label={t("fields.link.infoDialog.label", { globalId: props.globalId })}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>{props.globalId}</DialogTitle>
      <DialogContent>
        {state.state === "loading" && <Skeleton variant="rectangular" width="100%" height={240} />}
        {state.state === "fail" && <Alert severity="error">{state.error.message}</Alert>}
        {state.state === "success" && (
          <>
            {props.versionPin != null && (
              <Box
                role="note"
                sx={{
                  border: "1px solid",
                  borderColor: "warning.main",
                  borderRadius: 1,
                  p: 1,
                  mb: 1,
                }}
              >
                <Typography variant="body2">
                  <TransRichText
                    ns="inventory"
                    i18nKey="fields.link.infoDialog.versionNote"
                    values={{
                      versionPin: props.versionPin,
                      recordTypeLabel:
                        iconForInventoryGlobalId(props.globalId)?.recordTypeLabel.toLowerCase() ?? "record",
                      globalId: props.globalId.replace(/v\d+$/, ""),
                    }}
                  />
                </Typography>
              </Box>
            )}
            <SidebarBody record={state.record} factory={factory} />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={props.onClose}>{t("common:actions.close")}</Button>
      </DialogActions>
    </Dialog>
  );
}
