import "@/modules/common/styles/index.css";
import { Form, getInput, useForm } from "@formisch/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import * as v from "valibot";
import { afterEach, describe, expect, test } from "vitest";
import { page, userEvent } from "vitest/browser";
import { worker } from "@/__tests__/browserMocks";
import { OAUTH_TOKEN, oauthTokenHandler } from "@/__tests__/mocks/oauthTokenMocks";
import { resolveCollectionConfig } from "@/modules/common/collection/resolveConfig";
import { RenderFieldsPage } from "./pageObjects/RenderFieldsPage";
import { RenderFields } from "./RenderFields";
import { RenderFieldsStory } from "./RenderFields.story";

const form = new RenderFieldsPage();

type RemoteRelationshipDocument = {
  id: string;
  instrumentIds: number[];
};

const remoteRelationshipConfig = resolveCollectionConfig<RemoteRelationshipDocument>({
  slug: "remote-relationship-field-test",
  idField: "id",
  labels: {
    singularKey: "collectionForm.examples.record",
    pluralKey: "collectionForm.examples.records",
  },
  useAsTitle: "id",
  defaultColumns: ["id"],
  fields: [
    { name: "id", type: "text", labelKey: "collectionForm.examples.fields.id", form: false },
    {
      name: "instrumentIds",
      type: "relationship",
      labelKey: "collectionForm.examples.fields.collaborators",
      relationTo: "instruments",
      hasMany: true,
    },
  ],
});

function RemoteRelationshipForm() {
  const remoteForm = useForm({
    schema: v.object({ id: v.string(), instrumentIds: v.array(v.number()) }),
    initialInput: { id: "record-1", instrumentIds: [1, 2] },
  });

  return (
    <Form of={remoteForm} onSubmit={() => undefined}>
      <RenderFields fields={remoteRelationshipConfig.fields} form={remoteForm} />
      <output aria-label="Form values">{JSON.stringify(getInput(remoteForm))}</output>
    </Form>
  );
}

afterEach(() => {
  cleanup();
});

describe("RelationshipField", () => {
  test("reads and writes a to-one relationship value", async () => {
    render(<RenderFieldsStory />);

    await expect.element(form.owner).toHaveValue("Ada Lovelace");
    await form.chooseOwner("Grace Hopper");
    await form.addCollaborator("Katherine Johnson");
    await form.removeCollaborator("Grace Hopper");

    await expect.element(form.values).toHaveTextContent('"ownerId":{"relationTo":"users","value":"user-2"}');
    await expect.element(form.values).toHaveTextContent('"collaboratorIds":["user-3"]');
  });

  test("renders, preserves, and removes remote has-many relationship selections", async () => {
    const requests: { url: URL; authorization: string | null }[] = [];
    const firstInstrument = { id: 1, name: "First instrument", globalId: "IN1" };
    const thirdInstrument = { id: 3, name: "Third instrument", globalId: "IN3" };
    const instruments = [firstInstrument, { id: 2, name: "Second instrument", globalId: "IN2" }, thirdInstrument];
    const capture = (request: Request) => {
      requests.push({ url: new URL(request.url), authorization: request.headers.get("Authorization") });
    };

    worker.use(
      oauthTokenHandler(true),
      http.get("/api/v2/instruments/:id", ({ params, request }) => {
        capture(request);
        const id = Number(params.id);
        const instrument = instruments.find((candidate) => candidate.id === id);
        if (id === 2 || instrument === undefined) return new HttpResponse(null, { status: 404 });
        return HttpResponse.json(instrument);
      }),
      http.get("/api/v2/instruments", ({ request }) => {
        capture(request);
        const where = new URL(request.url).searchParams.get("where");
        return HttpResponse.json({ docs: where === "id=in=(1,2)" ? [firstInstrument] : [thirdInstrument] });
      }),
    );

    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <RemoteRelationshipForm />
      </QueryClientProvider>,
    );

    const values = page.getByRole("status", { name: "Form values" });
    await expect.element(page.getByText("First instrument")).toBeVisible();
    await expect.element(page.getByText("IN2")).toBeVisible();
    await expect.element(values).toHaveTextContent('"instrumentIds":[1,2]');

    await userEvent.type(page.getByRole("combobox", { name: "Collaborators" }), "Third");
    const thirdInstrumentOption = page.getByRole("option").filter({ hasText: "Third instrument" });
    const thirdInstrumentName = thirdInstrumentOption.getByText("Third instrument", { exact: true });
    await expect.element(thirdInstrumentName).toBeVisible();
    await userEvent.click(thirdInstrumentName);
    await expect.element(values).toHaveTextContent('"instrumentIds":[1,2,3]');

    const search = requests.find(({ url }) => url.searchParams.get("where") === "name=contains=Third");
    expect(search?.url.searchParams.get("where")).toBe("name=contains=Third");
    expect(search?.url.searchParams.get("fields[instruments]")).toBe("id,name,globalId");
    expect(search?.url.searchParams.get("limit")).toBe("20");
    expect(search?.authorization).toBe(`Bearer ${OAUTH_TOKEN}`);

    await userEvent.click(page.getByRole("button", { name: "Remove IN2" }));
    await expect.element(values).toHaveTextContent('"instrumentIds":[1,3]');
    await userEvent.click(page.getByRole("button", { name: "Remove First instrument" }));
    await expect.element(values).toHaveTextContent('"instrumentIds":[3]');
    await userEvent.click(page.getByRole("button", { name: "Remove Third instrument" }));
    await expect.element(values).toHaveTextContent('"instrumentIds":[]');
  });

  test("renders React content supplied for a relationship option", async () => {
    render(<RenderFieldsStory />);

    await form.openOwner();

    await expect.element(form.richRelationshipContent).toBeVisible();
  });
});
