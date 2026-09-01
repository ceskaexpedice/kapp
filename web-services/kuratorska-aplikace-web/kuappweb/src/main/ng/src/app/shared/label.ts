
export interface Label {
  name: string, note?: string, color?: string, text_color?: string, inconfig?: boolean,
  storageName?: string,
  visibility?: 'everyone' | 'private' | 'library',
  owner?: string
}
