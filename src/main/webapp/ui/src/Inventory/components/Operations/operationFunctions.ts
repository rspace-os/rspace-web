/**
 * The operation function registry (DevDocs/adr/0011). Each entry is a named, pure "Operation function" that
 * an operation's `effect.computed` selects to produce a single value at submit. An operation
 * declares *which* function and *how to source its arguments* in its own definition; the
 * computation itself lives here. This is the escape hatch for effects a declaration
 * cannot express (e.g. Passage's "parent's passage number + 1, else 1") without inventing a new
 * per-operation primitive each time. It is intentionally a curated, dev-authored registry, NOT an
 * end-user expression language.
 *
 * A function must be defensive about its inputs, since an argument may resolve to `undefined`
 * (e.g. an absent field).
 * All functions return a single string or number. To add a computation, add an entry here and
 * reference it by name from an operation; `Computed.fn` is typed to this registry's keys, so the
 * compiler rejects a name that is not here.
 */

export type OperationFunctionArgs = Record<string, string | number | undefined>;

export type OperationFunction = {
  fn: (args: OperationFunctionArgs) => string | number;
};

export const operationFunctions = {
  increment: {
    fn: ({ current, start }) => {
      const n = Number(current);
      const countable = Number.isSafeInteger(n) && n >= 0;
      return countable ? n + 1 : Number(start);
    },
  },
  /**
   * Built from local date parts (not toISOString, which is UTC) so it is the user's local "today"
   * even near midnight.
   */
  today: {
    fn: () => {
      const d = new Date();
      const pad = (n: number) => String(n).padStart(2, "0");
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    },
  },
} satisfies Record<string, OperationFunction>;

export type OperationFunctionName = keyof typeof operationFunctions;
