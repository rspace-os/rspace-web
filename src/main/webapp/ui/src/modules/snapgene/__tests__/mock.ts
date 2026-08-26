import { HttpResponse, http } from "msw";

const PNG_1x1 = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

const MOCK_FASTA = ">MockSeq\nACGTACGTACGTACGTACGT\n";

const ENZYMES_RESPONSE = {
  code: 200,
  response: "OK",
  serverIndex: 1,
  count: 1,
  setName: "UNIQUE_SIX_PLUS",
  enzymes: [{ name: "EcoRI", id: 1, hits: [{ topCutPosition: 10, bottomCutPosition: 14 }] }],
};

const ORFS_RESPONSE = {
  code: 200,
  response: "OK",
  serverIndex: 1,
  ORFs: [
    {
      id: 1,
      fullRangeBegin: 1,
      fullRangeEnd: 300,
      molecularWeight: 11.2,
      readingFrame: 1,
      translation: "MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEKAVQVKVKALPDAQFEVVHSLAKWKR",
    },
  ],
};

export const snapgeneHandlers = [
  http.get("/snapgene/status", () => HttpResponse.json({ code: 200, response: "OK" })),

  http.post("/snapgene/importDNAFile", () =>
    HttpResponse.json({ code: 200, response: "OK", serverIndex: 1, outputFileName: "converted.dna" }),
  ),

  http.post("/snapgene/exportPng", () =>
    HttpResponse.json({ code: 200, response: "OK", serverIndex: 1, outputFileName: "map.png" }),
  ),

  http.post("/snapgene/exportDNAFile", () =>
    HttpResponse.json({ code: 200, response: "OK", serverIndex: 1, outputFileName: "export.fasta" }),
  ),

  http.post("/snapgene/reportEnzymes", () => new HttpResponse(JSON.stringify(ENZYMES_RESPONSE))),
  http.post("/snapgene/reportORFs", () => new HttpResponse(JSON.stringify(ORFS_RESPONSE))),

  http.get("/snapgene/downloadFile", ({ request }) => {
    const fileName = new URL(request.url).searchParams.get("fileName") ?? "";
    if (fileName.endsWith(".png")) {
      return new HttpResponse(PNG_1x1, { headers: { "Content-Type": "image/png" } });
    }
    if (fileName.endsWith(".fasta")) {
      return new HttpResponse(MOCK_FASTA, { headers: { "Content-Type": "text/plain" } });
    }
    return new HttpResponse(Buffer.from("mock-dna-file-contents"));
  }),
];
