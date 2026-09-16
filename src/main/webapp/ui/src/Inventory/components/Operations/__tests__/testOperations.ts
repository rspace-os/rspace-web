// Importing the backend's operations_config.json directly keeps these tests red if the config
// and the frontend schema ever disagree.
import rawConfig from "@resources/inventory/operations_config.json";
import { type InventoryOperation, parseOperationsConfig } from "../operationsConfig";

export const operations: Array<InventoryOperation> = parseOperationsConfig(rawConfig);

export { rawConfig };
