/**
 * The real operation definitions for tests, loaded from the backend's single authoritative
 * operations_config.json (DevDocs/adr/0007) and parsed exactly as the wizard does after fetching
 * GET /operations/config. Importing the backend file directly keeps these tests red if the config
 * and the frontend schema ever disagree.
 */
import rawConfig from "../../../../../../../resources/inventory/operations_config.json";
import { type InventoryOperation, parseOperationsConfig } from "../operationsConfig";

export const operations: Array<InventoryOperation> = parseOperationsConfig(rawConfig);

export { rawConfig };
