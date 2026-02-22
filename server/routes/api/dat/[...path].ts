import {
  createError,
  defineEventHandler,
  getRouterParam,
  setResponseHeader,
} from '#imports'

const upstreamBaseUrl = 'http://localhost:3005'

export default defineEventHandler(async (event) => {
  const resourcePath = getRouterParam(event, 'path')
  if (!resourcePath) {
    throw createError({ statusCode: 400, statusMessage: 'Missing DAT resource path' })
  }

  const upstreamUrl = `${upstreamBaseUrl}/${resourcePath}`
  const response = await fetch(upstreamUrl)

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: `Upstream DAT fetch failed (${response.status} ${response.statusText})`,
    })
  }

  const contentType = response.headers.get('content-type')
  if (contentType) {
    setResponseHeader(event, 'content-type', contentType)
  }

  const body = await response.arrayBuffer()
  return new Uint8Array(body)
})
