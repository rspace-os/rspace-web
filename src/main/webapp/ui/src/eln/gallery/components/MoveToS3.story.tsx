import React from "react";
import MoveToS3 from "./MoveToS3";
import Alerts from "@/components/Alerts/Alerts";

function renderWithProviders(selectedIds: ReadonlyArray<string>) {
  return (
    <Alerts>
      <MoveToS3
        selectedIds={selectedIds}
        dialogOpen={true}
        setDialogOpen={() => {}}
      />
    </Alerts>
  );
}

export function MoveToS3DialogWithOneFile() {
  return renderWithProviders(["123"]);
}

export function MoveToS3DialogWithTwoFiles() {
  return renderWithProviders(["123", "456"]);
}

export function MoveToS3DialogInTransferMode() {
  return (
    <Alerts>
      <MoveToS3
        transferSources={[{ sourceFilestoreId: 2, sourcePath: "/data/file.jpg" }]}
        dialogOpen={true}
        setDialogOpen={() => {}}
      />
    </Alerts>
  );
}

export function MoveToS3DialogInTransferModeWithTwoFiles() {
  return (
    <Alerts>
      <MoveToS3
        transferSources={[
          { sourceFilestoreId: 2, sourcePath: "/data/file1.jpg" },
          { sourceFilestoreId: 2, sourcePath: "/data/file2.jpg" },
        ]}
        dialogOpen={true}
        setDialogOpen={() => {}}
      />
    </Alerts>
  );
}
