export default defineNuxtRouteMiddleware(async (to) => {
  if (!to.path.startsWith('/admin') || to.path === '/admin/login') return
  try { await useAuth().load() }
  catch { return navigateTo({ path: '/admin/login', query: { redirect: to.fullPath } }) }
})
