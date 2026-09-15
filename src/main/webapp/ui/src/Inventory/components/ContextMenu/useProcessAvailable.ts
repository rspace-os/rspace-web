import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import * as FetchingData from "../../../util/fetchingData";
import * as Parsers from "../../../util/parsers";

/**
 * Whether the Inventory operations wizard is switched on for this deployment (RSDEV-1231).
 *
 * `inventory.operations.available` is seeded DENIED, so the Process context-menu entry is hidden
 * until a sysadmin sets it to ALLOWED. Loading and error both answer false, which matches the
 * backend: while the property is DENIED every `/operations` route answers 404.
 */
export function useProcessAvailable(): boolean {
  const property = useDeploymentProperty("inventory.operations.available");
  return FetchingData.getSuccessValue(property)
    .flatMap(Parsers.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);
}
