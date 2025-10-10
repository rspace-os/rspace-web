import { match } from "@/util/Util";
import { Axis } from "@/stores/definitions/container/types";

export const encodeA1Z26 = (num: number): string =>
  String.fromCharCode(64 + num);

export const layoutToLabel = (
  layout: Axis,
  n: number,
  position: number,
): string => {
  if (n > 24) {
    throw new Error("grid larger than 24x24 are currently not supported");
  }

  if (position < 1 || position > n) {
    throw new Error("position must be between 1 and n");
  }

  return match<Axis, string>([
    [(l) => l === "N123", String(position)],
    [(l) => l === "N321", String(n - position + 1)],
    [(l) => l === "ABC", encodeA1Z26(position)],
    [(l) => l === "CBA", encodeA1Z26(n - position + 1)],
  ])(layout);
};

export const layoutToLabels = (
  layout: Axis,
  n: number,
): Array<{ value: number; label: string }> => {
  if (n > 24) {
    throw new Error("grid larger than 24x24 are currently not supported.");
  }

  return Array.from({ length: n }).map((_, i) => ({ label: layoutToLabel(layout, n, i + 1), value: i + 1 }));
}
