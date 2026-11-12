import type { Content, ContentType, SiteSettings } from '~/types'
export const sections = [
  { type: 'article', path: 'articles', label: '文章', icon: '▤', eyebrow: 'JOURNAL / WORDS & IDEAS', headline: '把每一次思考，认真留下。', description: '技术、阅读与日常，值得慢慢记录的片刻。' },
  { type: 'work', path: 'works', label: '作品', icon: '◇', eyebrow: 'SELECTED PROJECTS', headline: '让想法，长成看得见的作品。', description: '从一个小小的念头，到可以分享的创造。' },
  { type: 'tutorial', path: 'tutorials', label: '教程', icon: '☷', eyebrow: 'GUIDES / STEP BY STEP', headline: '把探索的过程，写成清楚的步骤。', description: '整理走过的路，让下一次出发更从容。' },
  { type: 'deal', path: 'deals', label: '羊毛', icon: '✧', eyebrow: 'GOOD FINDS / LITTLE SURPRISES', headline: '发现好资源，也记清使用条件。', description: '每一条发现，都附上来源、条件与有效时间。' }
] as const
export const sectionOf = (type: ContentType) => sections.find(s => s.type === type)!
export const contentUrl = (item: Pick<Content, 'contentType' | 'slug'>) => `/${sectionOf(item.contentType).path}/${item.slug}`
export const dateText = (date?: string) => date ? new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(date)) : '尚未发布'
export const defaultSite: SiteSettings = {
  name: '菜牙爱吃菜', description: '记录技术里的发现，也收藏生活中值得停留的片刻。', author: '菜牙', bio: '热爱技术，也认真生活。', github: '', email: '',
  footer: '风景作底，文字为先。', headline: '山色入笺，日常成诗。', subtitle: '记录技术里的发现，也收藏生活中值得停留的片刻。', background: 'mist', backgroundAssetId: null,
  glassOpacity: .66, articleOpacity: .88, blur: 18, fontSize: 19, lineHeight: 1.95, homeModules: ['article', 'work', 'tutorial', 'deal'],
  avatarAssetId: null, icpNumber: '', navTopOpacity: 0, navScrolledOpacity: .92,
  policeNumber: '', loginBackgroundAssetId: null, defaultCoverAssetId: null
}
export const themeStyle = (site: SiteSettings, adminPreview = false) => ({
  '--scene': site.backgroundAssetId ? `url("/api/${adminPreview ? 'admin' : 'public'}/media/${site.backgroundAssetId}${adminPreview ? '/file' : ''}")` : 'none',
  '--glass': site.glassOpacity, '--paper': site.articleOpacity, '--blur': `${site.blur}px`, '--reading-size': `${site.fontSize}px`, '--reading-line': site.lineHeight
  , '--nav-top-opacity': site.navTopOpacity ?? 0, '--nav-scrolled-opacity': site.navScrolledOpacity ?? .92
})
export const statusText = (status: string) => ({ draft: '草稿', published: '已发布', offline: '已下线', editing: '工作稿', frozen: '已冻结', valid: '已核验有效', invalid: '已失效', unknown: '待核验', upcoming: '尚未开始', expired: '已过期' }[status] || status)
