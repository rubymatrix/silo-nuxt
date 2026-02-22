import { describe, expect, it, vi } from 'vitest'

import { DatLoader } from '~/lib/loader/datLoader'

describe('DatLoader', () => {
  it('deduplicates inflight and cached loads by file path', async () => {
    const fetchImpl = vi.fn(async () => new Response(Uint8Array.from([1, 2, 3]).buffer, { status: 200 }))
    const parseDat = vi.fn((resourceName: string, bytes: Uint8Array) => ({ resourceName, size: bytes.length }))

    const loader = new DatLoader({ baseUrl: '/test', fetchImpl, parseDat })
    const [first, second, third] = await Promise.all([
      loader.load('ROM/0/1.DAT'),
      loader.load('ROM/0/1.DAT'),
      loader.load('ROM/0/1.DAT'),
    ])

    expect(fetchImpl).toHaveBeenCalledTimes(1)
    expect(parseDat).toHaveBeenCalledTimes(1)
    expect(first).toEqual(second)
    expect(second).toEqual(third)
  })

  it('throws a descriptive error when fetch fails', async () => {
    const fetchImpl = vi.fn(async () => new Response('missing', { status: 404, statusText: 'Not Found' }))
    const parseDat = vi.fn(() => ({ ok: true }))

    const loader = new DatLoader({ baseUrl: '/test', fetchImpl, parseDat })

    await expect(loader.load('ROM/0/missing.DAT')).rejects.toThrow(
      '[ROM/0/missing.DAT] Failed to fetch DAT (404 Not Found)',
    )
  })

  it('respects concurrency limits when loading multiple files', async () => {
    const release: Array<() => void> = []

    const fetchImpl = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          release.push(() => {
            resolve(new Response(Uint8Array.from([7]).buffer, { status: 200 }))
          })
        }),
    )

    const parseDat = vi.fn((resourceName: string, bytes: Uint8Array) => ({ resourceName, size: bytes.length }))

    const loader = new DatLoader({ baseUrl: '/test', concurrency: 1, fetchImpl, parseDat })

    const firstPromise = loader.load('ROM/0/first.DAT')
    const secondPromise = loader.load('ROM/0/second.DAT')

    await Promise.resolve()
    expect(fetchImpl).toHaveBeenCalledTimes(1)

    release.shift()?.()
    await firstPromise

    await Promise.resolve()
    expect(fetchImpl).toHaveBeenCalledTimes(2)

    release.shift()?.()
    await secondPromise
  })

  it('supports fetch implementations that require a bound this', async () => {
    const fetchHost = {
      fetch(input: RequestInfo | URL): Promise<Response> {
        if (this !== globalThis) {
          throw new TypeError('Illegal invocation')
        }

        return Promise.resolve(new Response(new TextEncoder().encode(String(input)), { status: 200 }))
      },
    }

    const loader = new DatLoader({
      baseUrl: '/test',
      fetchImpl: fetchHost.fetch,
      parseDat: (_resourceName, bytes) => bytes,
    })

    await expect(loader.load('ROM/0/1.DAT')).resolves.toBeInstanceOf(Uint8Array)
  })

  it('constructs fetch URLs from the configured base URL', async () => {
    const fetchImpl = vi.fn(async () => new Response(Uint8Array.from([1]).buffer, { status: 200 }))
    const loader = new DatLoader({
      baseUrl: 'https://dat.example.com',
      fetchImpl,
      parseDat: (_resourceName, bytes) => bytes,
    })

    await loader.load('ROM/0/1.DAT')

    expect(fetchImpl).toHaveBeenCalledWith('https://dat.example.com/ROM/0/1.DAT')
  })
})
