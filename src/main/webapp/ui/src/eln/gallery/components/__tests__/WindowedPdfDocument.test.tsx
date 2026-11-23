import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import WindowedPdfDocument from "../WindowedPdfDocument";

const PAGE_COUNT = 200;

/*
 * react-pdf cannot rasterise in jsdom, so the mock Document reports a long
 * document and each mock Page reports a 600x800pt size once mounted.
 */
vi.mock("react-pdf", async () => {
  const { useEffect } = await import("react");
  type DocumentProps = {
    onLoadSuccess: (pdf: { numPages: number }) => void;
    children: React.ReactNode;
  };
  type PageProps = {
    pageNumber: number;
    onLoadSuccess: (page: { pageNumber: number; originalWidth: number; originalHeight: number }) => void;
  };
  return {
    Document: ({ onLoadSuccess, children }: DocumentProps) => {
      useEffect(() => {
        onLoadSuccess({ numPages: PAGE_COUNT });
      }, []);
      return <div>{children}</div>;
    },
    Page: ({ pageNumber, onLoadSuccess }: PageProps) => {
      useEffect(() => {
        onLoadSuccess({ pageNumber, originalWidth: 600, originalHeight: 800 });
      }, []);
      return <p>{`page ${pageNumber}`}</p>;
    },
  };
});

class ControllableIntersectionObserver {
  static latest: ControllableIntersectionObserver | null = null;
  readonly targets: Array<Element> = [];

  constructor(
    private readonly callback: IntersectionObserverCallback,
    readonly options: IntersectionObserverInit,
  ) {
    ControllableIntersectionObserver.latest = this;
  }

  observe(target: Element): void {
    this.targets.push(target);
  }

  unobserve(): void {}

  disconnect(): void {}

  takeRecords(): Array<IntersectionObserverEntry> {
    return [];
  }

  report(pageNumbers: ReadonlyArray<number>, isIntersecting: boolean): void {
    const entries = pageNumbers.map((pageNumber) => ({ target: this.targets[pageNumber - 1], isIntersecting }));
    act(() => {
      this.callback(entries as Array<IntersectionObserverEntry>, this as unknown as IntersectionObserver);
    });
  }
}

async function observerForAllPages(): Promise<ControllableIntersectionObserver> {
  await waitFor(() => expect(ControllableIntersectionObserver.latest?.targets).toHaveLength(PAGE_COUNT));
  return ControllableIntersectionObserver.latest as ControllableIntersectionObserver;
}

function renderedPages(): Array<HTMLElement> {
  return screen.queryAllByText(/^page \d+$/);
}

beforeEach(() => {
  ControllableIntersectionObserver.latest = null;
  vi.stubGlobal("IntersectionObserver", ControllableIntersectionObserver);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("WindowedPdfDocument", () => {
  test("mounts only the pages near the visible area of a long PDF", async () => {
    render(<WindowedPdfDocument file="/long.pdf" scale={1} />);
    const observer = await observerForAllPages();

    observer.report([1, 2], true);

    expect(renderedPages()).toHaveLength(2);
    expect(screen.getByText("page 1")).toBeInTheDocument();
    expect(screen.getByText("page 2")).toBeInTheDocument();
  });

  test("swaps pages in and out as the user scrolls", async () => {
    render(<WindowedPdfDocument file="/long.pdf" scale={1} />);
    const observer = await observerForAllPages();
    observer.report([1, 2], true);

    observer.report([1, 2], false);
    observer.report([150, 151], true);

    expect(renderedPages()).toHaveLength(2);
    expect(screen.getByText("page 150")).toBeInTheDocument();
    expect(screen.queryByText("page 1")).not.toBeInTheDocument();
  });

  test("measures nearness against the closest scrollable ancestor", async () => {
    render(
      <section aria-label="viewer" style={{ overflowY: "auto" }}>
        <WindowedPdfDocument file="/long.pdf" scale={1} />
      </section>,
    );
    const observer = await observerForAllPages();

    expect(observer.options.root).toBe(screen.getByRole("region", { name: "viewer" }));
  });

  test("reserves the size of unmounted pages from the first page, scaled by zoom", async () => {
    const { rerender } = render(<WindowedPdfDocument file="/long.pdf" scale={1} />);
    const observer = await observerForAllPages();
    const lastPagePlaceholder = observer.targets[PAGE_COUNT - 1];

    observer.report([1], true);
    await waitFor(() => expect(lastPagePlaceholder).toHaveStyle({ minWidth: "600px", minHeight: "800px" }));

    rerender(<WindowedPdfDocument file="/long.pdf" scale={1.5} />);
    expect(lastPagePlaceholder).toHaveStyle({ minWidth: "900px", minHeight: "1200px" });
  });

  test("sizes placeholders to a fixed page width when one is given", async () => {
    render(<WindowedPdfDocument file="/long.pdf" scale={2} width={300} />);
    const observer = await observerForAllPages();
    const lastPagePlaceholder = observer.targets[PAGE_COUNT - 1];

    observer.report([1], true);

    await waitFor(() => expect(lastPagePlaceholder).toHaveStyle({ minWidth: "600px", minHeight: "800px" }));
  });
});
