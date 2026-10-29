import * as v from "valibot";

export const booleanScalar = v.pipe(
  v.picklist(["true", "false"]),
  v.transform((value) => value === "true"),
);

export const numberScalar = v.pipe(
  v.string(),
  v.transform((value) => (value.trim() === "" ? Number.NaN : Number(value))),
  v.finite(),
);

export const dateTimeScalar = v.pipe(
  v.string(),
  v.transform((value) => new Date(value)),
  v.check((value) => !Number.isNaN(value.getTime())),
);
