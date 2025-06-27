import React from "react";
import MoveToIrods from "../../eln/gallery/components/MoveToIrods";

export default function IrodsWrapper(): React.ReactNode {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [selectedIds, setSelectedIds] = React.useState<ReadonlyArray<string>>(
    []
  );

  React.useEffect(() => {
    const handler = (event: Event) => {
      // @ts-expect-error the event will have this detail
      setSelectedIds(event.detail.ids as Array<string>);
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
        // @ts-expect-error TS can't find the global gallery function
        if (!newState) gallery();
      }}
    />
  );
}
