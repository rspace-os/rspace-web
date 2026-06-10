import React, { Suspense, useEffect, useRef, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Skeleton from "@mui/material/Skeleton";
import Alert from "@mui/material/Alert";
import Link from "@mui/material/Link";
import Box from "@mui/material/Box";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import { useGetWorkspaceRecordInformationAjaxQuery } from "@/modules/workspace/queries";
import { iconForGlobalId, prefixOf, openUrlForTarget } from "./iconForGlobalId";
import DocumentSections from "./DocumentSections";
import GallerySections from "./GallerySections";

export interface EnElnRecordInfoDialogProps {
  open: boolean;
  globalId: string;
  /**
   * When the link is pinned to a specific version of an SD target, the version number
   * (the `vN` in `SDxxxvN`). null/undefined means the link points at the latest version.
   */
  versionPin?: number | null;
  onClose: () => void;
}

const UNAVAILABLE_MESSAGE =
  "This item is not available, or you do not have permission to view it.";

/**
 * Extract the numeric DB id from a global id (e.g. `SD123` -> 123, `SD123v2` -> 123).
 * `getRecordInformation` takes the numeric id, not the global-id string.
 */
function numericIdOf(globalId: string): number | null {
  const match = /^[A-Z]{2}(\d+)(?:v\d+)?$/.exec(globalId);
  return match ? Number(match[1]) : null;
}

/**
 * Local error boundary so that a failed record-info fetch (e.g. 404 / not readable)
 * renders an inline Alert inside the dialog rather than bubbling to the app-level
 * ErrorBoundary. The surrounding subtree is keyed (by global id + open count) so a
 * fresh boundary is mounted each time the dialog is opened, clearing any prior error.
 */
class DialogErrorBoundary extends React.Component<
  {
    children: React.ReactNode;
    globalId: string;
    versionPin?: number | null;
  },
  { hasError: boolean }
> {
  constructor(props: {
    children: React.ReactNode;
    globalId: string;
    versionPin?: number | null;
  }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true };
  }

  render(): React.ReactNode {
    if (this.state.hasError) {
      // A pinned link that can no longer resolve its version gets a specific message
      // plus a link to the latest, rather than the generic not-available text.
      if (this.props.versionPin != null) {
        return (
          <Alert severity="error">
            Version {this.props.versionPin} of {this.props.globalId} is no longer
            available.{" "}
            <Link
              href={`/globalId/${this.props.globalId}`}
              target="_blank"
              rel="noopener noreferrer"
            >
              View the latest version
            </Link>
            .
          </Alert>
        );
      }
      return <Alert severity="error">{UNAVAILABLE_MESSAGE}</Alert>;
    }
    return this.props.children;
  }
}

/**
 * The per-type body. Calls the (suspense) record-information query, branches by the
 * global-id prefix, and renders the matching per-type sections. Throws into the
 * surrounding {@link DialogErrorBoundary} when the record cannot be loaded.
 */
function DialogBody({
  globalId,
  recordId,
  versionPin,
}: {
  globalId: string;
  recordId: number;
  versionPin?: number | null;
}): React.ReactElement {
  const { data: info, refetch } = useGetWorkspaceRecordInformationAjaxQuery({
    recordId,
    version: versionPin ?? undefined,
  });
  const prefix = prefixOf(globalId);
  if (prefix === "GL") {
    return (
      <GallerySections
        info={info}
        onRecordChanged={() => {
          void refetch();
        }}
      />
    );
  }
  return (
    <DocumentSections
      info={info}
      isNotebook={prefix === "NB"}
      pinnedVersion={versionPin}
    />
  );
}

/**
 * Modal that reproduces the ELN record-info dialog (`#recordInfoDialog`) for an ELN
 * link target (structured document, notebook or gallery file). It fetches the same
 * data the ELN info button does via `/workspace/getRecordInformation` and renders the
 * per-type body (documents/notebooks vs gallery files) to match the ELN exactly.
 *
 * Self-contained: provides its own React Query client (the Inventory Link field is not
 * wrapped in a `QueryClientProvider`), a Suspense boundary for the suspense query, and
 * a local error boundary that shows an inline "not available" message on failure.
 */
export default function EnElnRecordInfoDialog(
  props: EnElnRecordInfoDialogProps,
): React.ReactElement | null {
  // One client per dialog instance keeps cache lifetime tied to the dialog.
  const [queryClient] = useState(() => new QueryClient());
  // Bumped each time the dialog opens; used to remount the error boundary + query
  // subtree so a previous failure (e.g. a transient 404) is retried on re-open. The
  // cached query result is also cleared so the body refetches rather than re-throwing
  // the cached error.
  const [openCount, setOpenCount] = useState(0);
  const wasOpen = useRef(false);
  useEffect(() => {
    if (props.open && !wasOpen.current) {
      queryClient.clear();
      setOpenCount((c) => c + 1);
    }
    wasOpen.current = props.open;
  }, [props.open, queryClient]);

  if (!props.open) return null;

  const iconData = iconForGlobalId(props.globalId);
  const recordId = numericIdOf(props.globalId);
  // Only SD documents are versionable; ignore a pin on any other target (NB/GL).
  const effectiveVersionPin =
    prefixOf(props.globalId) === "SD" ? props.versionPin ?? null : null;

  return (
    <Dialog
      open={props.open}
      onClose={props.onClose}
      aria-label={`Info for ${props.globalId}`}
      fullWidth
      maxWidth="md"
    >
      <DialogTitle>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          {iconData && <RecordTypeIcon record={iconData} aria-hidden />}
          {props.globalId}
        </Box>
      </DialogTitle>
      <DialogContent dividers>
        {recordId === null ? (
          <Alert severity="error">{UNAVAILABLE_MESSAGE}</Alert>
        ) : (
          <QueryClientProvider client={queryClient}>
            <DialogErrorBoundary
              key={`${props.globalId}-${effectiveVersionPin ?? "latest"}-${openCount}`}
              globalId={props.globalId}
              versionPin={effectiveVersionPin}
            >
              <Suspense
                fallback={
                  <Skeleton variant="rectangular" width="100%" height={240} />
                }
              >
                <DialogBody
                  globalId={props.globalId}
                  recordId={recordId}
                  versionPin={effectiveVersionPin}
                />
              </Suspense>
            </DialogErrorBoundary>
          </QueryClientProvider>
        )}
      </DialogContent>
      <DialogActions>
        <Button
          size="small"
          startIcon={<OpenInNewIcon />}
          href={openUrlForTarget(props.globalId, effectiveVersionPin)}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Open"
        >
          Open
        </Button>
        <Button onClick={props.onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
