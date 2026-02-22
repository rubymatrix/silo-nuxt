import { describe, expect, it, vi } from 'vitest'

import { createResourceTableRuntime } from '~/lib/resource/table/runtime'

describe('resource table runtime wiring', () => {
  it('loads FFXI table resources from localhost server paths', async () => {
    const fetchImpl = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)

      if (url.endsWith('/FFXiMain.dll')) {
        const bytes = new Uint8Array(0x40000)
        const view = new DataView(bytes.buffer)

        view.setUint32(0x30020, 0xc825c825, false)
        view.setUint32(0x30030, 0x6f9f6f9f, false)
        view.setUint32(0x30040, 0xef9def9d, false)
        view.setUint32(0x30050, 0x4826c826, false)
        view.setUint32(0x30060, 0xef9f6fa0, false)
        view.setUint32(0x30070, 0x48274827, false)
        view.setUint32(0x30080, 0xa81b0000, false)
        view.setUint32(0x30090, 0xa01ba01b, false)
        view.setUint32(0x300a0, 0xcb81cb81, false)
        view.setUint32(0x300b0, 0xb9e2b9e2, false)
        view.setUint32(0x300c0, 0xcb96cb96, false)
        view.setUint32(0x300d0, 0x8b998b99, false)
        view.setUint32(0x300e0, 0xe2e506a9, false)
        view.setUint32(0x300f0, 0xb8c5f784, false)
        view.setBigUint64(0x30100, 0x6400000100010100n, false)

        return new Response(bytes.buffer, { status: 200 })
      }

      if (url.endsWith('/VTABLE.DAT') || url.endsWith('/FTABLE.DAT')) {
        return new Response(new Uint8Array(32).buffer, { status: 200 })
      }

      if (url.endsWith('/landsandboat/ItemModelTable.DAT')) {
        return new Response(new Uint8Array(32).buffer, { status: 200 })
      }

      return new Response('missing', { status: 404, statusText: 'Not Found' })
    })

    const runtime = createResourceTableRuntime({
      baseUrl: 'http://localhost:3005',
      fetchImpl,
      fileTableCount: 1,
    })

    await runtime.preloadAll()

    const requestedUrls = fetchImpl.mock.calls.map(([input]) => String(input))
    expect(requestedUrls).toContain('http://localhost:3005/FFXiMain.dll')
    expect(requestedUrls).toContain('http://localhost:3005/VTABLE.DAT')
    expect(requestedUrls).toContain('http://localhost:3005/FTABLE.DAT')
    expect(requestedUrls).toContain('http://localhost:3005/landsandboat/ItemModelTable.DAT')
  })
})
