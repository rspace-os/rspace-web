export interface ApiInventoryExportJob {
  id: number | null;
  status: string;
  percentComplete: number;
  result?: {
    expiryDate: string;
    size: number;
    checksum: string;
    algorithm: string;
  };
  _links: Array<{ link: string; rel: string }>;
}
