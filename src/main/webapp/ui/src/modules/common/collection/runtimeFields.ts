/** The value types a runtime field can declare. */
export const runtimeFieldValueTypes = ["text", "number", "date", "time", "radio", "choice"] as const;
export type RuntimeFieldValueType = (typeof runtimeFieldValueTypes)[number];
