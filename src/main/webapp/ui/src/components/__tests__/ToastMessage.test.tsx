import { act, render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import Snackbars from "../ToastMessage";

const dispatchToast = (message: string) => {
  document.dispatchEvent(
    new CustomEvent("show-toast-message", {
      detail: { message, variant: "success", infinite: true },
    }),
  );
};

describe("Snackbars", () => {
  test("shows a toast dispatched after mount", async () => {
    render(<Snackbars />);
    act(() => {
      dispatchToast("post-mount toast");
    });
    expect(await screen.findByText("post-mount toast")).toBeInTheDocument();
  });

  test("shows toasts dispatched before mount", async () => {
    // legacy page scripts fire RS.confirm from document-ready handlers, which
    // can run before this component has mounted its listener
    dispatchToast("pre-mount toast 1");
    dispatchToast("pre-mount toast 2");
    render(<Snackbars />);
    expect(await screen.findByText("pre-mount toast 1")).toBeInTheDocument();
    expect(screen.getByText("pre-mount toast 2")).toBeInTheDocument();
  });
});
