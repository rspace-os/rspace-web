import { HttpResponse, http } from "msw";
import { server } from "@/__tests__/mswServer";
import type { ListInstruments200 } from "@/modules/common/api/v2/generated/models/listInstruments200";

const instruments: ListInstruments200 = {
  docs: [{ id: 123, name: "Confocal microscope", globalId: "IN123" }],
  totalDocs: 1,
  limit: 20,
  page: 1,
  hasNextPage: false,
};
export function listInstruments(onRequest?: (url: URL) => void) {
  server.use(
    http.get("/api/v2/instruments", ({ request }) => {
      onRequest?.(new URL(request.url));
      return HttpResponse.json(instruments);
    }),
  );
}
