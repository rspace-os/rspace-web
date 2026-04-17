import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import KetcherTinyMce from "../KetcherTinyMce";

vi.mock("../../../components/Ketcher/KetcherDialog", () => ({
  default: ({
    isOpen,
    title,
    actionBtnText,
    validationResult,
  }: {
    isOpen: boolean;
    title: string;
    actionBtnText: string;
    validationResult: { message?: string };
  }) =>
    isOpen ? (
      <div role="dialog" aria-label={title}>
        <p>{validationResult.message ?? "Ready to insert"}</p>
        <button type="button">{actionBtnText}</button>
      </div>
    ) : null,
}));

vi.mock("@/hooks/api/useChemicalImport", () => ({
  default: () => ({
    save: vi.fn(),
  }),
}));

describe("KetcherTinyMce accessibility", () => {
  it("has no accessibility violations when the insert dialog is open", async () => {
    Object.assign(globalThis, {
      tinymce: {
        activeEditor: {
          id: "rtf_12",
          selection: {
            getNode: () => null,
          },
          execCommand: vi.fn(),
          windowManager: {
            close: vi.fn(),
          },
        },
      },
    });

    const { baseElement } = render(<KetcherTinyMce />);

    expect(
      await screen.findByRole("dialog", { name: "Ketcher Insert Chemical" }),
    ).toBeVisible();

    // @ts-expect-error toBeAccessible is from @sa11y/vitest
    // eslint-disable-next-line @typescript-eslint/no-unsafe-call
    await expect(baseElement).toBeAccessible();
  });
});

