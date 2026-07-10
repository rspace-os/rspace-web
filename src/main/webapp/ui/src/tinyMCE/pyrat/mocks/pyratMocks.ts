import { HttpResponse, http } from "msw";

export type AnimalsRequest = { l: number; o: number };

/**
 * A single fake PyRAT animal. Only the fields the results table reads need to
 * be present; `eartag_or_id` doubles as the row's selection id.
 */
export function makeAnimal(index: number) {
  return {
    eartag_or_id: `A${String(index).padStart(4, "0")}`,
    sex: index % 2 === 0 ? "f" : "m",
    age_days: 30 + index,
    strain_name: "C57BL/6",
    dateborn: "2025-01-01",
    datesacrificed: "",
    classification: "experimental",
    licence_number: "LIC-123",
    labid: `LAB${index}`,
    building_name: "Main",
    responsible_fullname: "Jane Doe",
  };
}

/**
 * Handlers that stand in for the RSpace PyRAT proxy endpoints.
 *
 * - `/integration/integrationInfo` returns exactly one configured server so the
 *   dialog auto-selects it and mounts the listing.
 * - `/apps/pyrat/animals` returns `l` rows starting at offset `o`, capped at
 *   `totalCount`, and echoes `X-Total-Count` so the pagination controls render.
 *   Every animals request is pushed onto `requests` so tests can assert exactly
 *   which page/size was fetched, and how many times.
 */
export function pyratHandlers({
  requests,
  totalCount = 137,
}: {
  requests: Array<AnimalsRequest>;
  totalCount?: number;
}) {
  return [
    http.get("/integration/integrationInfo", () =>
      HttpResponse.json({
        data: {
          options: {
            "1": {
              PYRAT_ALIAS: "fakepyrat",
              PYRAT_URL: "https://demo.pyrat.example/api/v3",
            },
          },
        },
      }),
    ),
    http.get("/apps/pyrat/version", () => HttpResponse.json({ api_version: 3 })),
    http.get("/apps/pyrat/locations", () => HttpResponse.json([])),
    http.get("/apps/pyrat/animals", ({ request }) => {
      const params = new URL(request.url).searchParams;
      const l = Number(params.get("l") ?? "10");
      const o = Number(params.get("o") ?? "0");
      requests.push({ l, o });

      const rows = [];
      for (let i = o; i < Math.min(o + l, totalCount); i++) {
        rows.push(makeAnimal(i));
      }
      return HttpResponse.json(rows, {
        headers: { "X-Total-Count": String(totalCount) },
      });
    }),
    // Not exercised by the pagination tests, but present so filter interactions
    // never fall through to a 404.
    http.get("/apps/pyrat/licenses", () => HttpResponse.json([])),
    http.get("/apps/pyrat/users", () => HttpResponse.json([])),
    http.get("/apps/pyrat/projects", () => HttpResponse.json([])),
  ];
}
