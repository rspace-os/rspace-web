export interface ApiFolder {
  id: number;
  globalId: string;
  name: string;
  parentFolderId?: number;
  notebook: boolean;
}

export interface ApiFolderCreateRequest {
  name: string;
  parentFolderId?: number;
  notebook?: boolean;
}
