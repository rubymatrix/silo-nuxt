interface Env {
  DAT_BUCKET: R2Bucket
  DAT_ACCESS_TOKEN: string
  ALLOWED_ORIGINS: string
}

/**
 * Cloudflare Worker that gates access to the phoenix-dat-files R2 bucket
 * behind a Bearer token. Requests without a valid Authorization header
 * are rejected with 403.
 */
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const origin = request.headers.get('Origin') ?? ''
    const allowedOrigins = env.ALLOWED_ORIGINS.split(',').map((s) => s.trim())
    const corsOrigin = allowedOrigins.includes(origin) ? origin : ''

    // Handle CORS preflight (Authorization header triggers preflight)
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: corsHeaders(corsOrigin),
      })
    }

    if (request.method !== 'GET' && request.method !== 'HEAD') {
      return new Response('Method Not Allowed', {
        status: 405,
        headers: corsHeaders(corsOrigin),
      })
    }

    // Validate Bearer token
    if (!isAuthorized(request, env.DAT_ACCESS_TOKEN)) {
      return new Response('Forbidden', {
        status: 403,
        headers: corsHeaders(corsOrigin),
      })
    }

    // Derive object key from URL path (strip leading slash)
    const url = new URL(request.url)
    const key = decodeURIComponent(url.pathname.replace(/^\/+/, ''))

    if (!key) {
      return new Response('Not Found', {
        status: 404,
        headers: corsHeaders(corsOrigin),
      })
    }

    const object = await env.DAT_BUCKET.get(key)
    if (!object) {
      return new Response('Not Found', {
        status: 404,
        headers: corsHeaders(corsOrigin),
      })
    }

    const headers = new Headers(corsHeaders(corsOrigin))
    headers.set('Content-Type', 'application/octet-stream')
    headers.set('Cache-Control', 'public, max-age=31536000, immutable')
    headers.set('ETag', object.httpEtag)

    if (object.size !== undefined) {
      headers.set('Content-Length', String(object.size))
    }

    if (request.method === 'HEAD') {
      return new Response(null, { status: 200, headers })
    }

    return new Response(object.body, { status: 200, headers })
  },
} satisfies ExportedHandler<Env>

/** Constant-time string comparison to prevent timing attacks on the token. */
function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false

  const encoder = new TextEncoder()
  const bufA = encoder.encode(a)
  const bufB = encoder.encode(b)

  // crypto.subtle.timingSafeEqual is available in Workers runtime
  let result = 0
  for (let i = 0; i < bufA.length; i++) {
    result |= bufA[i] ^ bufB[i]
  }
  return result === 0
}

function isAuthorized(request: Request, expectedToken: string): boolean {
  const auth = request.headers.get('Authorization')
  if (!auth) return false

  const parts = auth.split(' ')
  if (parts.length !== 2 || parts[0] !== 'Bearer') return false

  return timingSafeEqual(parts[1], expectedToken)
}

function corsHeaders(origin: string): HeadersInit {
  if (!origin) return {}

  return {
    'Access-Control-Allow-Origin': origin,
    'Access-Control-Allow-Methods': 'GET, HEAD, OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization',
    'Access-Control-Max-Age': '86400',
  }
}
