import "@/stores/stores/RootStore";
import { describe, expect, it } from "vitest";
import type { Location } from "@/stores/definitions/Container";
import { boxWithA1Taken } from "../../GridView/__tests__/gridFixtures";
import { canStartDrag } from "../Draggable";

const enabled = { uiConfig: { dragAndDropDisabled: false } };
const notMoving = { isMoving: false };

function boxWithA1Selected() {
  const box = boxWithA1Taken();
  const a1 = box.findLocation(1, 1) as Location;
  a1.toggleSelected(true);
  return { box, a1 };
}

describe("canStartDrag", () => {
  it("allows a drag from a filled location when every selected location is filled", () => {
    const { box, a1 } = boxWithA1Selected();
    expect(canStartDrag(box, a1, enabled, notMoving)).toBe(true);
  });

  it("refuses an empty location", () => {
    const { box } = boxWithA1Selected();
    expect(canStartDrag(box, box.findLocation(2, 1) as Location, enabled, notMoving)).toBe(false);
  });

  it("refuses while the selection includes an empty location", () => {
    const { box, a1 } = boxWithA1Selected();
    box.findLocation(2, 1)?.toggleSelected(true);
    expect(canStartDrag(box, a1, enabled, notMoving)).toBe(false);
  });

  it("refuses inside the Move dialog", () => {
    const { box, a1 } = boxWithA1Selected();
    expect(canStartDrag(box, a1, enabled, { isMoving: true })).toBe(false);
  });

  it("refuses when the grid turns drag-and-drop off", () => {
    const { box, a1 } = boxWithA1Selected();
    expect(canStartDrag(box, a1, { uiConfig: { dragAndDropDisabled: true } }, notMoving)).toBe(false);
  });
});
