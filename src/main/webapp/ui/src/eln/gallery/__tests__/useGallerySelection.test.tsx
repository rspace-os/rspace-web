import { act, render } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import type { GalleryFile } from "../useGalleryListing";
import { GallerySelection, useGallerySelection } from "../useGallerySelection";

type Selection = ReturnType<typeof useGallerySelection>;

const file = { key: "file-1" } as GalleryFile;

describe("GallerySelection", () => {
  test("keeps the selection when the component that renders it re-renders", () => {
    const selections: Array<Selection> = [];
    function Probe({ label }: { label: string }) {
      selections.push(useGallerySelection());
      return <span>{label}</span>;
    }
    const { rerender } = render(
      <GallerySelection>
        <Probe label="first" />
      </GallerySelection>,
    );
    act(() => selections[0].append(file));

    rerender(
      <GallerySelection>
        <Probe label="second" />
      </GallerySelection>,
    );

    const latest = selections[selections.length - 1];
    expect(latest).toBe(selections[0]);
    expect(latest.includes(file)).toBe(true);
  });

  test("still allows only one file when single selection is on", () => {
    const selections: Array<Selection> = [];
    function Probe() {
      selections.push(useGallerySelection());
      return null;
    }
    render(
      <GallerySelection onlyAllowSingleSelection>
        <Probe />
      </GallerySelection>,
    );
    const other = { key: "file-2" } as GalleryFile;
    act(() => {
      selections[0].append(file);
      selections[0].append(other);
    });

    expect(selections[0].includes(file)).toBe(false);
    expect(selections[0].includes(other)).toBe(true);
  });
});
