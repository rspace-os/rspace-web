import getRootStore from "../stores/stores/RootStore";
import { mkAlert } from "../stores/contexts/Alert";
import { toTitleCase } from "./Util";
import * as ArrayUtils from "./ArrayUtils";
import Result from "../stores/models/Result";
import { type InventoryRecord } from "../stores/definitions/InventoryRecord";
import React from "react";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons";
import { library } from "@fortawesome/fontawesome-svg-core";
import { traverseObjectTree } from "./unsafeUtils";
library.add(faSpinner);

type Operation =
  | "trashed"
  | "restored"
  | "duplicated"
  | "split"
  | "moved"
  | "updated"
  | "transferred";

const bulkSuccessAlert = (
  records: Array<InventoryRecord>,
  suffix: string
): string => {
  if (records.length === 0) {
    return "No changes.";
  }
  const prefix =
    new Set(records.map((r) => r.type)).size === 1
      ? `${records[0].recordTypeLabel}${records.length === 1 ? "" : "s"}`
      : "Items";
  return `${prefix} successfully ${suffix}.`;
};

/**
 * Handles the success of a bulk operation, displaying a success alert with
 * details of the operation.
 */
export const handleDetailedSuccesses = (
  records: Array<InventoryRecord>,
  operation: Operation,
  recordAltOperation: (record: InventoryRecord) => string = () => operation,
  message?: ?string = null
) => {
  const variant = "success";
  getRootStore().uiStore.addAlert(
    mkAlert({
      title: message ? bulkSuccessAlert(records, operation) : null,
      message: message ?? bulkSuccessAlert(records, operation),
      variant,
      details: records.map((record) => ({
        title: `${toTitleCase(recordAltOperation(record))} "${record.name}".`,
        variant,
        record,
      })),
    })
  );
};

// see ../../../../resources/bundles/inventory/inventory.properties
const messages = {
  container: {
    deletion: {
      failure: {
        not: {
          empty: ({ name }: { name: string }) => ({
            title: `The container '${name}' is not empty.`,
            help: "Delete the contents first.",
          }),
        },
      },
    },
  },
  sample: {
    deletion: {
      failure: {
        subsamples: {
          in: {
            containers: ({ name }: { name: string }) => ({
              title: `The sample '${name}' includes subsamples that are in containers.`,
              help: "Delete these subsamples first or remove them from their container.",
            }),
          },
        },
      },
    },
  },
  move: {
    failure: {
      cannot: {
        locate: {
          target: {
            container: () => ({
              title: "Could not locate target container.",
              help: "Please try again. If this error persists then contact support.",
            }),
          },
        },
      },
    },
  },
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
        errors: Array<string>,
      },
    },
    record?: InventoryRecord,
  }>,
  operation: string,
  retryFunction: ((records: Array<InventoryRecord>) => Promise<void>) | null, // requires record on data array
  defaultHelp: ?string
): boolean => {
  const errorData = data.filter(({ response }) => Boolean(response.error));
  const variant = "error";
  if (errorCount) {
    getRootStore().uiStore.addAlert(
      mkAlert({
        title: `Could not perform the ${operation} action.`,
        message: "Expand to see details.",
        variant,
        details: errorData.flatMap(({ record, response }) =>
          response.error.errors.map((error) => ({
            ...traverseObjectTree(messages, error, () => ({
              title: error,
              help: defaultHelp ?? "Please refresh and try again.",
            }))(record ?? {}),
            variant,
            record,
          }))
        ),
        ...(retryFunction
          ? Object.freeze({
              retryFunction: () => {
                return retryFunction(
                  ArrayUtils.filterClass(
                    Result,
                    errorData.map((r) => r.record)
                  )
                );
              },
            })
          : Object.freeze({})),
      })
    );
  }
  return Boolean(errorCount);
};

/**
 * Shows a loading alert whilst a promise is pending.
 */
export const showToastWhilstPending = <A>(
  message: string,
  promise: Promise<A>
): Promise<A> => {
  const processingAlert = mkAlert({
    message,
    variant: "notice",
    isInfinite: true,
    allowClosing: false,
    icon: <FontAwesomeIcon icon="spinner" spin size="1x" />,
  });
  getRootStore().uiStore.addAlert(processingAlert);
  return promise.finally(() => {
    getRootStore().uiStore.removeAlert(processingAlert);
  });
};
