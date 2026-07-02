import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, test } from "vitest";
import { IMAGE_LOCATIONS, VisualContainerWithEdgeLocations, VisualContainerWithLocations } from "./PreviewImage.story";
import { PreviewImagePage } from "./pageObjects/PreviewImagePage";

/*
 * Browser mode is required: jsdom has no ResizeObserver, returns zeros from
 * getBoundingClientRect, and doesn't reproduce the DOMRect prototype-getter
 * semantics that regressed the size extraction (PRT-1107).
 *
 * The regression signal is positional, not presence: with the size missing the
 * markers still render, they just stack at (0,0). The assertions therefore check
 * position, and use distinctness/spans rather than exact pixels so they don't
 * couple to LocationWrapper's icon-offset constants.
 */
describe("PreviewImage", () => {
  afterEach(cleanup);

  test("renders each location marker at a distinct position tracking its coordinates", async () => {
    render(<VisualContainerWithLocations />);
    const previewImage = new PreviewImagePage();

    await expect.element(previewImage.marker(1)).toBeInTheDocument();
    await expect.element(previewImage.image).toBeVisible();

    // Poll until the image settles on its real size; the observer can fire once
    // with a transient (smaller) size first.
    await expect.poll(() => previewImage.markerOffset(3).left - previewImage.markerOffset(1).left).toBeGreaterThan(50);

    const offsets = IMAGE_LOCATIONS.map((_, i) => previewImage.markerOffset(i + 1));

    for (const { left, top } of offsets) {
      expect(Number.isFinite(left)).toBe(true);
      expect(Number.isFinite(top)).toBe(true);
    }
    // Not collapsed onto the origin (the failure mode this guards against).
    expect(offsets.some(({ left, top }) => Math.abs(left) > 1 || Math.abs(top) > 1)).toBe(true);

    // Rising-diagonal coordinates → strictly increasing left and top.
    expect(offsets[1].left).toBeGreaterThan(offsets[0].left);
    expect(offsets[2].left).toBeGreaterThan(offsets[1].left);
    expect(offsets[1].top).toBeGreaterThan(offsets[0].top);
    expect(offsets[2].top).toBeGreaterThan(offsets[1].top);

    // Equal coordinate spacing → roughly equal pixel spacing.
    const leftStep1 = offsets[1].left - offsets[0].left;
    const leftStep2 = offsets[2].left - offsets[1].left;
    expect(leftStep2).toBeCloseTo(leftStep1, -1);
  });

  // Edge coordinates: minimum (0,0) and maximum (1000,1000). The corner-to-corner
  // pixel span should equal the full rendered image size; with the bug both
  // markers collapse onto the origin and the span drops to ~0.
  test("places minimum (0,0) and maximum (1000,1000) markers a full image apart", async () => {
    render(<VisualContainerWithEdgeLocations />);
    const previewImage = new PreviewImagePage();

    await expect.element(previewImage.marker(1)).toBeInTheDocument();
    await expect.element(previewImage.marker(2)).toBeInTheDocument();
    await expect.element(previewImage.image).toBeVisible();

    await expect.poll(() => previewImage.markerOffset(2).left - previewImage.markerOffset(1).left).toBeGreaterThan(50);

    const { width, height } = previewImage.imageSize();
    const min = previewImage.markerOffset(1);
    const max = previewImage.markerOffset(2);

    expect(max.left).toBeGreaterThan(min.left);
    expect(max.top).toBeGreaterThan(min.top);
    // ~5px tolerance for sub-pixel layout across engines.
    expect(max.left - min.left).toBeCloseTo(width, -1);
    expect(max.top - min.top).toBeCloseTo(height, -1);
  });
});
