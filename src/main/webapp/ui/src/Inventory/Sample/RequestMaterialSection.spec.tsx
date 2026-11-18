import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render } from "@testing-library/react";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, expect, onTestFinished, test } from "vitest";
import { page } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import { personAttrs } from "@/stores/models/__tests__/PersonModel/mocking";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import PersonModel from "@/stores/models/PersonModel";
import getRootStore from "@/stores/stores/getRootStore";
import materialTheme from "@/theme";
import RequestMaterialSection from "./RequestMaterialSection";

beforeEach(() => {
  const { authStore, peopleStore } = getRootStore();
  runInAction(() => {
    authStore.isSynchronizing = false;
    peopleStore.currentUser = new PersonModel(personAttrs({ username: "sample-requester" }));
  });
  worker.use(http.get("/api/inventory/v1/sampleRequests", () => HttpResponse.json({ requests: [] })));
});

afterEach(() => {
  cleanup();
});

async function openDialogFor(sampleName: string): Promise<HTMLElement> {
  const { innerWidth, innerHeight } = window;
  await page.viewport(390, 844);
  onTestFinished(() => page.viewport(innerWidth, innerHeight));

  const sample = makeMockSample({
    name: sampleName,
    owner: personAttrs({ username: "sample-owner" }),
    requestable: true,
  });
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ThemeProvider theme={materialTheme}>
        <DeploymentPropertyContext.Provider value={new Map([["inventory.sampleRequests.available", "ALLOWED"]])}>
          <RequestMaterialSection activeResult={sample} />
        </DeploymentPropertyContext.Provider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
  await page.getByRole("button", { name: "Request Sample" }).click();
  return page.getByRole("dialog").getByRole("heading").element() as HTMLElement;
}

test("wraps a maximum-length sample name with no spaces inside the dialog", async () => {
  const title = await openDialogFor("x".repeat(255));
  expect(title.scrollWidth).toBeLessThanOrEqual(title.clientWidth);
});

test("wraps a long sample name with spaces at the spaces, without splitting words", async () => {
  const word = "Supercalifragilistic";
  const title = await openDialogFor(Array(12).fill(word).join(" "));
  expect(title.scrollWidth).toBeLessThanOrEqual(title.clientWidth);

  const textNode = title.firstChild as Text;
  const text = textNode.data;
  let start = text.indexOf(word);
  let lines = new Set<number>();
  while (start !== -1) {
    const range = document.createRange();
    range.setStart(textNode, start);
    range.setEnd(textNode, start + word.length);
    const rects = Array.from(range.getClientRects()).filter((r) => r.width > 0);
    expect(rects).toHaveLength(1);
    lines = lines.add(Math.round(rects[0].top));
    start = text.indexOf(word, start + word.length);
  }
  expect(lines.size).toBeGreaterThan(1);
});
