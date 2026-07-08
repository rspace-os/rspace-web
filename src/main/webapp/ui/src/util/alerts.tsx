import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import i18n from "@/modules/common/i18n";
import { mkAlert } from "../stores/contexts/Alert";
import type { InventoryRecord } from "../stores/definitions/InventoryRecord";
import getRootStore from "../stores/stores/getRootStore";
import * as ArrayUtils from "./ArrayUtils";
import { Optional } from "./optional";

type Operation = "trashed" | "restored" | "duplicated" | "split" | "moved" | "updated" | "transferred";

// ponytail: "created" isn't an Operation but is a recordAltOperation override
// (Search.ts's duplicate flow); keyed loosely so unrecognised words still translate
const OPERATION_LABEL_KEYS = {
  trashed: "inventory:bulkAlerts.operations.trashed",
  restored: "inventory:bulkAlerts.operations.restored",
  duplicated: "inventory:bulkAlerts.operations.duplicated",
  split: "inventory:bulkAlerts.operations.split",
  moved: "inventory:bulkAlerts.operations.moved",
  updated: "inventory:bulkAlerts.operations.updated",
  transferred: "inventory:bulkAlerts.operations.transferred",
  created: "inventory:bulkAlerts.operations.created",
} as const;
const OPERATION_DETAIL_LABEL_KEYS = {
  trashed: "inventory:bulkAlerts.operationsForDetail.trashed",
  restored: "inventory:bulkAlerts.operationsForDetail.restored",
  duplicated: "inventory:bulkAlerts.operationsForDetail.duplicated",
  split: "inventory:bulkAlerts.operationsForDetail.split",
  moved: "inventory:bulkAlerts.operationsForDetail.moved",
  updated: "inventory:bulkAlerts.operationsForDetail.updated",
  transferred: "inventory:bulkAlerts.operationsForDetail.transferred",
  created: "inventory:bulkAlerts.operationsForDetail.created",
} as const;
const translateOperation = (operation: string): string => {
  const key = OPERATION_LABEL_KEYS[operation as keyof typeof OPERATION_LABEL_KEYS];
  return key ? i18n.t(key) : operation;
};
const translateOperationForDetail = (operation: string): string => {
  const key = OPERATION_DETAIL_LABEL_KEYS[operation as keyof typeof OPERATION_DETAIL_LABEL_KEYS];
  return key ? i18n.t(key) : translateOperation(operation);
};

const bulkSuccessAlert = (records: Array<InventoryRecord>, suffix: string): string => {
  if (records.length === 0) {
    return i18n.t("inventory:bulkAlerts.success.noChanges");
  }
  const prefix =
    new Set(records.map((r) => r.type)).size === 1
      ? i18n.t("inventory:bulkAlerts.success.prefixSingleType", {
          count: records.length,
          label: records[0].recordTypeLabel,
        })
      : i18n.t("inventory:bulkAlerts.success.items");
  return i18n.t("inventory:bulkAlerts.success.message", { prefix, operation: translateOperation(suffix) });
};

/**
 * Handles the success of a bulk operation, displaying a success alert with
 * details of the operation.
 */
export const handleDetailedSuccesses = (
  records: Array<InventoryRecord>,
  operation: Operation,
  recordAltOperation: (record: InventoryRecord) => string = () => operation,
  message: string | null = null,
) => {
  const variant = "success";
  getRootStore().uiStore.addAlert(
    mkAlert({
      title: message ? bulkSuccessAlert(records, operation) : null,
      message: message ?? bulkSuccessAlert(records, operation),
      variant,
      details: records.map((record) => ({
        title: i18n.t("inventory:bulkAlerts.success.detail", {
          operation: translateOperationForDetail(recordAltOperation(record)),
          name: record.name,
        }),
        variant,
        record,
      })),
    }),
  );
};

// Keyed by the literal error code the API returns (a message-bundle key from
// ../../../../resources/bundles/inventory/inventory.properties). These are
// flat dot-delimited strings, so a direct lookup beats any tree traversal.
const messages: Record<string, (record: { name?: string }) => { title: string; help: string }> = {
  "container.deletion.failure.not.empty": ({ name }) => ({
    title: i18n.t("inventory:bulkAlerts.error.containerNotEmpty.title", { name }),
    help: i18n.t("inventory:bulkAlerts.error.containerNotEmpty.help"),
  }),
  "sample.deletion.failure.subsamples.in.containers": ({ name }) => ({
    title: i18n.t("inventory:bulkAlerts.error.sampleSubsamplesInContainers.title", { name }),
    help: i18n.t("inventory:bulkAlerts.error.sampleSubsamplesInContainers.help"),
  }),
  "move.failure.cannot.locate.target.container": () => ({
    title: i18n.t("inventory:bulkAlerts.error.cannotLocateTargetContainer.title"),
    help: i18n.t("inventory:bulkAlerts.error.cannotLocateTargetContainer.help"),
  }),
};

/**
 * Handles the failure of a bulk operation, displaying an error alert with
 * details of the operation.
 */
export const handleDetailedErrors = (
  errorCount: number,
  data: Array<{
    response: {
      error: {
        errors: Array<string>;
      };
    };
    record?: InventoryRecord;
  }>,
  operation: string,
  retryFunction: ((records: Array<InventoryRecord>) => Promise<void>) | null = null, // requires record on data array
  defaultHelp: string | null = null,
): boolean => {
  const errorData = data.filter(({ response }) => Boolean(response.error));
  const variant = "error";
  if (errorCount) {
    getRootStore().uiStore.addAlert(
      mkAlert({
        title: i18n.t("inventory:bulkAlerts.error.actionFailed", { operation }),
        message: i18n.t("inventory:errors.expandForMoreDetails"),
        variant,
        details: errorData.flatMap(({ record, response }) =>
          response.error.errors.map((error) => {
            const resolve =
              messages[error] ??
              (() => ({
                title: error,
                help: defaultHelp ?? i18n.t("inventory:bulkAlerts.error.refreshAndTryAgain"),
              }));
            return {
              ...resolve(record ?? {}),
              variant,
              record,
            };
          }),
        ),
        ...(retryFunction
          ? Object.freeze({
              retryFunction: () => {
                return retryFunction(ArrayUtils.mapOptional((r) => Optional.fromNullable(r.record), errorData));
              },
            })
          : Object.freeze({})),
      }),
    );
  }
  return Boolean(errorCount);
};

/**
 * Shows a loading alert whilst a promise is pending.
 */
export async function showToastWhilstPending<A>(message: string, promise: Promise<A>): Promise<A> {
  const loadingIcon = <FontAwesomeIcon icon={faSpinner} spin size="1x" />;
  const processingAlert = mkAlert({
    message,
    variant: "notice",
    isInfinite: true,
    allowClosing: false,
    icon: loadingIcon,
  });
  getRootStore().uiStore.addAlert(processingAlert);
  try {
    return await promise;
  } finally {
    getRootStore().uiStore.removeAlert(processingAlert);
  }
}
