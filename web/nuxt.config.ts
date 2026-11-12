export default defineNuxtConfig({
  compatibilityDate: '2026-10-21',
  devtools: { enabled: false },
  css: ['~/assets/main.css', '~/assets/reader.css', '~/assets/personalization.css'],
  typescript: { strict: true },
  runtimeConfig: {
    apiBase: 'http://127.0.0.1:8080',
    public: { siteUrl: 'http://localhost:3000', umamiScriptUrl: '', umamiWebsiteId: '' }
  },
  app: { head: { htmlAttrs: { lang: 'zh-CN' }, meta: [{ name: 'theme-color', content: '#697655' }], link: [{ rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }] } },
  routeRules: {
    '/admin': { ssr: false },
    '/admin/**': { ssr: false },
    // NOTE: Nitro route-rule caching cannot wrap the /api proxy handler (it
    // tries to serialize the raw proxied response stream and hangs), so
    // public API caching is done with backend Cache-Control headers only.
    '/**': { headers: { 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'strict-origin-when-cross-origin', 'Content-Security-Policy': "base-uri 'self'; object-src 'none'; frame-ancestors 'self'" } }
  }
})
