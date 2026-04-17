import React from "react";
import StoichiometryDialogEntrypoint from "../StoichiometryDialogEntrypoint";

export const StoichiometryDialogWithCalculateButtonStory = ({
  onTableCreated,
}: {
  onTableCreated?: () => void;
} = {}) => {
  const [open, setOpen] = React.useState(true);
  const [stoichiometryId, setStoichiometryId] = React.useState<
    number | undefined
  >(undefined);
  const [stoichiometryRevision, setStoichiometryRevision] = React.useState<
    number | undefined
  >(undefined);

  const handleTableCreated = () => {
    setStoichiometryId(1);
    setStoichiometryRevision(1);
    onTableCreated?.();
  };

  return (
    <StoichiometryDialogEntrypoint
      open={open}
      onClose={() => setOpen(false)}
      chemId={12345}
      recordId={1}
      stoichiometryId={stoichiometryId}
      stoichiometryRevision={stoichiometryRevision}
      onTableCreated={handleTableCreated}
    />
  );
};

export const StoichiometryDialogWithTableStory = ({
  onSave,
  onDelete,
}: {
  onSave?: () => void;
  onDelete?: () => void;
} = {}) => {
  const [open, setOpen] = React.useState(true);

  return (
    <StoichiometryDialogEntrypoint
      open={open}
      onClose={() => setOpen(false)}
      chemId={12345}
      recordId={1}
      stoichiometryId={1}
      stoichiometryRevision={1}
      onSave={onSave}
      onDelete={onDelete}
    />
  );
};

export const StoichiometryDialogClosedStory = () => {
  const [stoichiometryId, setStoichiometryId] = React.useState<
    number | undefined
  >(undefined);
  const [stoichiometryRevision, setStoichiometryRevision] = React.useState<
    number | undefined
  >(undefined);

  const handleTableCreated = () => {
    setStoichiometryId(1);
    setStoichiometryRevision(1);
  };

  return (
    <>
      <div>Dialog is closed</div>
      <StoichiometryDialogEntrypoint
        open={false}
        onClose={() => {}}
        chemId={12345}
        recordId={1}
        stoichiometryId={stoichiometryId}
        stoichiometryRevision={stoichiometryRevision}
        onTableCreated={handleTableCreated}
      />
    </>
  );
};
