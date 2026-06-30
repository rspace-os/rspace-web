import Alerts from "@/components/Alerts/Alerts";
import MoveToIrods from "./MoveToIrods";

function renderWithProviders(selectedIds: ReadonlyArray<string>) {
  return (
    <Alerts>
      <MoveToIrods selectedIds={selectedIds} dialogOpen={true} setDialogOpen={() => {}} />
    </Alerts>
  );
}

export function MoveToIrodsDialogWithOneFile() {
  return renderWithProviders(["123"]);
}

export function MoveToIrodsDialogWithTwoFiles() {
  return renderWithProviders(["123", "456"]);
}
