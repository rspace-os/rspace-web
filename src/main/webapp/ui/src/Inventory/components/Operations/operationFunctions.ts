/**
 * The operation function registry (adr/0006). Each entry is a named, pure "Operation function" that
 * an operation's `effect.computed` selects to produce a single value at submit. Operations declare
 * *which* function and *how to source its arguments* in operations_config.json; the computation
 * itself lives here, in code. This is the framework's escape hatch for effects the declarative config
 * cannot express (e.g. Passage's "parent's passage number + 1, else 1") without inventing a new
 * per-operation primitive each time. It is intentionally a curated, dev-authored registry, NOT an
 * end-user expression language.
 *
 * A function declares its parameter names only (the config binds each to a source); it must be
 * defensive about its inputs, since an argument may resolve to `undefined` (e.g. an absent field).
 * All functions return a single string or number. To add a computation, add an entry here and
 * reference it from config; operationsConfig validates the reference at load.
 */

export type OperationFunctionArgs = Record<string, string | number | undefined>;

export type OperationFunction = {
  /** The argument names this function reads; config must bind exactly these. */
  params: ReadonlyArray<string>;
  fn: (args: OperationFunctionArgs) => string | number;
};

export const operationFunctions = {
  /**
   * A running counter: `current + 1`, or `start` when `current` is absent or not a number. Passage
   * uses it as "parent sample's passage number + 1, else 1".
   */
  increment: {
    params: ["current", "start"],
    fn: ({ current, start }) => {
      const n = Number(current);
      return Number.isFinite(n) ? n + 1 : Number(start);
    },
  },
} satisfies Record<string, OperationFunction>;

export type OperationFunctionName = keyof typeof operationFunctions;
