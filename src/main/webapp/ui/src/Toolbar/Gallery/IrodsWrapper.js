//@flow

import React, { type Node } from "react";
import MoveToIrods from "./MoveToIrods";

export default function IrodsWrapper(): Node {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [selectedIds, setSelectedIds] = React.useState<$ReadOnlyArray<string>>(
    []
  );

  React.useEffect(() => {
    const handler = (
      event: Event & { detail: { ids: Array<string>, ... }, ... }
    ) => {
      setSelectedIds(event.detail.ids);
      setDialogOpen(true);
    };
    window.addEventListener("OPEN_IRODS_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_IRODS_DIALOG", handler);
    };
  }, []);

  return (
    <MoveToIrods
      selectedIds={selectedIds}
      dialogOpen={dialogOpen}
      setDialogOpen={(newState) => {
        setDialogOpen(newState);
        // eslint-disable-next-line no-undef
        if (!newState) gallery();
      }}
    />
  );
}
