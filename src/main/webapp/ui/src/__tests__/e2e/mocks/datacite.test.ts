import { expect, test } from "vitest";
import { server } from "@/__tests__/mswServer";
import { dataciteHandlers } from "./datacite";

test("DataCite reads back submitted publication metadata per DOI without filling in missing affiliations", async () => {
  server.use(...dataciteHandlers);
  const firstUrl = new URL("/dois/10.99999/first", window.location.href).href;
  const secondUrl = new URL("/dois/10.99999/second", window.location.href).href;
  const affiliation = {
    name: "Test institution",
    affiliationIdentifier: "https://ror.org/02mhbdp94",
    affiliationIdentifierScheme: "ROR",
  };
  const withAffiliation = { event: "publish", creators: [{ name: "First creator", affiliation: [affiliation] }] };
  const withoutAffiliation = { event: "publish", creators: [{ name: "Second creator" }] };

  for (const [url, attributes] of [
    [firstUrl, withAffiliation],
    [secondUrl, withoutAffiliation],
  ] as const) {
    const response = await fetch(url, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ data: { attributes } }),
    });
    expect(response.ok).toBe(true);
  }

  const first = await (await fetch(`${firstUrl}?affiliation=true`)).json();
  const second = await (await fetch(`${secondUrl}?affiliation=true`)).json();
  expect(first.data.attributes.creators).toEqual(withAffiliation.creators);
  expect(first.data.attributes.state).toBe("findable");
  expect(second.data.attributes.creators).toEqual(withoutAffiliation.creators);

  await fetch(firstUrl, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data: { attributes: withoutAffiliation } }),
  });
  const updated = await (await fetch(`${firstUrl}?affiliation=true`)).json();
  expect(updated.data.attributes.creators).toEqual(withoutAffiliation.creators);

  for (const url of [firstUrl, secondUrl]) {
    await fetch(url, { method: "DELETE" });
    expect((await fetch(url)).status).toBe(404);
  }
});
