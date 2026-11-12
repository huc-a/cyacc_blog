export type ContentType = 'article'
export interface Taxon { id: number; name: string; slug: string; contentType?: ContentType; count?: number; sortOrder?: number }
export interface Category { id: number; name: string; slug: string; count?: number; sortOrder: number }
export interface Content {
  id: number; contentType: ContentType; slug: string; title: string; summary: string;
  publishStatus: 'draft' | 'published' | 'offline'; revisionId: number; revisionNo: number;
  revisionState?: 'editing' | 'frozen'; lockVersion?: number; createdAt?: string; updatedAt?: string;
  firstPublishedAt?: string; lastPublishedAt?: string; deletedAt?: string;
  coverAssetId?: number | null; bodyMarkdown?: string;
  tags?: Taxon[]; tagNames?: string[]; tagIds?: number[]; galleryAssetIds?: number[]; categoryId?: number | null; categoryName?: string | null;
  navigation?: { previous: ContentNeighbor | null; next: ContentNeighbor | null };
}
export type ContentNeighbor = Pick<Content, 'id' | 'contentType' | 'slug' | 'title'>
export interface Page<T> { items: T[]; total: number; page: number; size: number }
export interface SiteSettings {
  name: string; description: string; author: string; bio: string; github: string; email: string;
  footer: string; headline: string; subtitle: string; background: 'mist' | 'blue' | 'dusk';
  backgroundAssetId: number | null; glassOpacity: number; articleOpacity: number; blur: number;
  fontSize: number; lineHeight: number; homeModules: ContentType[];
  avatarAssetId: number | null; icpNumber: string; navTopOpacity: number; navScrolledOpacity: number;
  policeNumber: string; loginBackgroundAssetId: number | null; defaultCoverAssetId: number | null;
}
export interface MediaAsset { id: number; originalName: string; mimeType: string; width: number; height: number; sizeBytes: number; createdAt: string }
export interface Account { username: string; displayName: string; avatarAssetId: number | null; lockVersion: number }
