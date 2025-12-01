export type GalaxyDataSummary = {
  rspaceFieldName: string;
  galaxyHistoryName: string;
  galaxyHistoryId: string;
  galaxyDataNames: Array<{id: number, fileName: string}>;
  galaxyInvocationName: string;
  galaxyInvocationStatus: string;
  galaxyInvocationId: string;
  galaxyBaseUrl: string;
  createdOn: number;
};