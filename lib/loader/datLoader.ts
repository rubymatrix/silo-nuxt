export interface DatLoaderOptions<TResource> {
  readonly baseUrl?: string
  readonly concurrency?: number
  readonly fetchImpl?: typeof fetch
  readonly parseDat: (resourceName: string, bytes: Uint8Array) => TResource | Promise<TResource>
}

const defaultBaseUrl = '/api/dat'
const defaultConcurrency = 4

export class DatLoader<TResource> {
  private readonly baseUrl: string
  private readonly concurrency: number
  private readonly fetchImpl: typeof fetch
  private readonly parseDat: DatLoaderOptions<TResource>['parseDat']

  private readonly cache = new Map<string, TResource>()
  private readonly inflight = new Map<string, Promise<TResource>>()
  private readonly waitQueue: Array<() => void> = []
  private activeCount = 0

  constructor(options: DatLoaderOptions<TResource>) {
    this.baseUrl = options.baseUrl ?? defaultBaseUrl
    this.concurrency = Math.max(1, options.concurrency ?? defaultConcurrency)
    this.fetchImpl = options.fetchImpl ?? fetch
    this.parseDat = options.parseDat
  }

  async load(filePath: string): Promise<TResource> {
    const normalizedPath = normalizeFilePath(filePath)
    const cached = this.cache.get(normalizedPath)
    if (cached !== undefined) {
      return cached
    }

    const inflight = this.inflight.get(normalizedPath)
    if (inflight) {
      return inflight
    }

    const request = this.runWithLimit(async () => {
      const url = buildDatUrl(this.baseUrl, normalizedPath)
      const response = await this.fetchImpl.call(globalThis, url)

      if (!response.ok) {
        throw new Error(
          `[${normalizedPath}] Failed to fetch DAT (${response.status} ${response.statusText})`,
        )
      }

      const bytes = new Uint8Array(await response.arrayBuffer())
      const parsed = await this.parseDat(normalizedPath, bytes)
      this.cache.set(normalizedPath, parsed)
      return parsed
    })

    this.inflight.set(normalizedPath, request)

    try {
      return await request
    } finally {
      this.inflight.delete(normalizedPath)
    }
  }

  clearCache(): void {
    this.cache.clear()
    this.inflight.clear()
  }

  private async runWithLimit<T>(task: () => Promise<T>): Promise<T> {
    if (this.activeCount >= this.concurrency) {
      await new Promise<void>((resolve) => {
        this.waitQueue.push(resolve)
      })
    }

    this.activeCount += 1

    try {
      return await task()
    } finally {
      this.activeCount -= 1
      const next = this.waitQueue.shift()
      next?.()
    }
  }
}

function normalizeFilePath(filePath: string): string {
  return filePath.replace(/^\/+/, '')
}

function buildDatUrl(baseUrl: string, filePath: string): string {
  const trimmedBaseUrl = baseUrl.replace(/\/+$/, '')
  return `${trimmedBaseUrl}/${filePath}`
}
