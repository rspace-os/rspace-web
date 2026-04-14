import React, { useCallback, useEffect } from "react";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ChemCard from "./ChemCard";
import styled from "@emotion/styled";
import { createRoot } from "react-dom/client";
import materialTheme from "../theme";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";

interface SidebarItem extends Record<string, string | undefined> {
  id: string;
  imageSrc?: string;
}

interface SidebarInfoProps {
  iframe: HTMLIFrameElement;
}

declare const tinymce: {
  activeEditor: {
    execCommand: (command: string, ui: boolean, value?: string) => void;
  };
};

const SidebarWrapper = styled.div`
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;

  .MuiPaper-root {
    overflow: visible;
  }
`;

function isChemImage(element: Element | null): element is HTMLImageElement {
  return element instanceof HTMLImageElement && element.classList.contains("chem");
}

function getIframeDocument(iframe: HTMLIFrameElement): Document | null {
  return iframe.contentDocument ?? iframe.contentWindow?.document ?? null;
}

function getRemovedChemElements(node: Node): HTMLImageElement[] {
  const removedChemElements: HTMLImageElement[] = [];

  if (node instanceof HTMLImageElement && node.classList.contains("chem")) {
    removedChemElements.push(node);
  }

  if (node instanceof Element) {
    removedChemElements.push(
      ...Array.from(node.querySelectorAll<HTMLImageElement>("img.chem")),
    );
  }

  return removedChemElements;
}

export default function SidebarInfo({ iframe }: SidebarInfoProps) {
  const [items, setItems] = React.useState<SidebarItem[]>([]);
  const [open, setOpen] = React.useState(false);

  const addItem = useCallback((element: HTMLImageElement): void => {
    if (!element.id) {
      return;
    }

    const item: SidebarItem = {
      id: element.id,
      imageSrc: element.getAttribute("src") ?? undefined,
    };

    setItems((oldItems) => [
      item,
      ...oldItems.filter((existingItem) => existingItem.id !== item.id),
    ]);
  }, []);

  const removeItem = useCallback((id?: string): void => {
    setItems((oldItems) => oldItems.filter((item) => item.id !== id));
  }, []);

  const closeAll = useCallback(
    (event?: React.MouseEvent<HTMLButtonElement>): void => {
      event?.preventDefault();
      setItems([]);
    },
    [],
  );

  useEffect(() => {
    const iframeDocument = getIframeDocument(iframe);
    if (!iframeDocument) {
      return undefined;
    }

    const handleChemClick = (event: MouseEvent): void => {
      const chemElement =
        event.target instanceof Element
          ? event.target.closest("img.chem")
          : null;

      if (isChemImage(chemElement)) {
        addItem(chemElement);
      }
    };

    const handleChemInserted = (event: Event): void => {
      const chemId = (event as CustomEvent<string | number>).detail;
      const chemElement = iframeDocument.getElementById(String(chemId));

      if (isChemImage(chemElement)) {
        addItem(chemElement);
      }
    };

    const handleChemUpdated = (): void => {
      closeAll();
    };

    const handleSidebarToggle = (event: Event): void => {
      setOpen(Boolean((event as CustomEvent<boolean>).detail));
    };

    iframeDocument.addEventListener("click", handleChemClick);

    const observer = watchEditor(iframeDocument, (element) => {
      removeItem(element.id);
    });

    document.addEventListener("tinymce-chem-inserted", handleChemInserted);
    document.addEventListener("tinymce-chem-updated", handleChemUpdated);
    document.addEventListener("tinymce-chem-sidebar", handleSidebarToggle);

    return () => {
      iframeDocument.removeEventListener("click", handleChemClick);
      observer.disconnect();
      document.removeEventListener("tinymce-chem-inserted", handleChemInserted);
      document.removeEventListener("tinymce-chem-updated", handleChemUpdated);
      document.removeEventListener("tinymce-chem-sidebar", handleSidebarToggle);
    };
  }, [addItem, closeAll, iframe, removeItem]);

  useEffect(() => {
    if (items.length === 1 && !open) {
      tinymce.activeEditor.execCommand("togglesidebar", false, "cheminfo");
      setOpen(true);
    } else if (items.length === 0 && open) {
      tinymce.activeEditor.execCommand("togglesidebar", false, "cheminfo");
      setOpen(false);
    }
  }, [items.length, open]);

  if (items.length === 0) {
    return null;
  }

  return (
    <SidebarWrapper>
      {items.length > 1 && (
        <Box sx={{ textAlign: "right" }}>
          <Button sx={{ p: "10px" }} onClick={closeAll}>
            Close All
          </Button>
        </Box>
      )}
      {items.map((item, idx) =>
        item.id ? (
          <ChemCard key={item.id} onClose={removeItem} item={item} idx={idx} />
        ) : null,
      )}
    </SidebarWrapper>
  );
}

function SidebarInfoEntrypoint(props: SidebarInfoProps) {
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <SidebarInfo {...props} />
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("tinymce-iframe-loaded", (event: Event) => {
  const iframeSelector = (event as CustomEvent<string>).detail;
  const iframe = document.querySelector(iframeSelector);
  const container = document.querySelector(".tox-sidebar__pane-container");

  if (!(iframe instanceof HTMLIFrameElement) || !(container instanceof HTMLElement)) {
    return;
  }

  const root = createRoot(container);
  root.render(<SidebarInfoEntrypoint iframe={iframe} />);
});

function watchEditor(
  container: Document,
  callbackRemoved: (element: HTMLImageElement) => void,
): MutationObserver {
  const onMutationsObserved: MutationCallback = (mutations) => {
    mutations.forEach((mutation) => {
      mutation.removedNodes.forEach((removedNode) => {
        getRemovedChemElements(removedNode).forEach(callbackRemoved);
      });
    });
  };

  const observer = new MutationObserver(onMutationsObserved);
  observer.observe(container, { childList: true, subtree: true });
  return observer;
}
