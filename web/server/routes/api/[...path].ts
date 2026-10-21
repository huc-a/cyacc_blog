export default defineEventHandler((event) => {
  const config = useRuntimeConfig(event)
  const url = getRequestURL(event)
  return proxyRequest(event, `${config.apiBase}${url.pathname}${url.search}`)
})
