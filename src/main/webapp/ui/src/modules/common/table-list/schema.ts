import * as v from "valibot";
import { parseInstant, truncateToMilliseconds } from "@/modules/common/time/instant";

export const booleanScalar = v.pipe(
  v.picklist(["true", "false"]),
  v.transform((value) => value === "true"),
);

export const numberScalar = v.pipe(
  v.string(),
  v.transform((value) => (value.trim() === "" ? Number.NaN : Number(value))),
  v.finite(),
);

/** An ISO 8601 instant with an offset, cut to milliseconds; other text is an issue, never a throw. */
export const dateTimeScalar = v.pipe(
  v.string(),
  v.rawTransform(({ dataset, addIssue, NEVER }) => {
    const instant = parseInstant(dataset.value);
    if (instant === null) {
      addIssue({ message: "Expected an ISO 8601 date-time with an offset" });
      return NEVER;
    }
    return truncateToMilliseconds(instant);
  }),
);
