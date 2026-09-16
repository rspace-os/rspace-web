import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import * as FetchingData from "../../../util/fetchingData";
import * as Parsers from "../../../util/parsers";

export function useProcessAvailable(): boolean {
  const property = useDeploymentProperty("inventory.operations.available");
  return FetchingData.getSuccessValue(property)
    .flatMap(Parsers.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);
}
