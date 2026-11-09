/** An already-translated message safe to display in the table error state. */
export class LocalizedTableListError extends Error {}

/** A runtime-field projection exceeded the limit published for its namespace. */
export class RuntimeFieldProjectionLimitError extends Error {
  readonly limit: number;

  constructor(limit: number) {
    super("Runtime field projection limit exceeded");
    this.name = "RuntimeFieldProjectionLimitError";
    this.limit = limit;
  }
}
