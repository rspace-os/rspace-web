export interface ApiFile {
  id: number;
  globalId: string;
  name: string;
  parentFolderId?: number;
}

export interface ApiFileUploadRequest {
  name: string;
  mimeType: string;
  buffer: Buffer;

  folderId?: number;
  caption?: string;
}
