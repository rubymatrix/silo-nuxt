import { type DatEntry, type DatId, DirectoryResource, TextureResource } from './datResource'

export class TextureLink {
  readonly name: string
  readonly localDir: DirectoryResource

  private linkedResource: TextureResource | null = null
  private notFound = false

  constructor(name: string, localDir: DirectoryResource) {
    this.name = name
    this.localDir = localDir
  }

  static of(name: string | null | undefined, localDir: DirectoryResource): TextureLink | null {
    if (name == null) {
      return null
    }
    return new TextureLink(name, localDir)
  }

  get(): TextureResource | null {
    return this.linkedResource
  }

  getOrPut(provider?: (name: string) => TextureResource | null): TextureResource | null {
    if (this.linkedResource !== null) {
      return this.linkedResource
    }
    if (this.notFound) {
      return null
    }

    const resolver = provider ?? ((name: string) => this.localDir.searchLocalAndParentsByName(name) ?? DirectoryResource.getGlobalTexture(name))
    this.linkedResource = resolver(this.name)
    this.notFound = this.linkedResource === null
    return this.linkedResource
  }
}

export class DatLink<T extends DatEntry> {
  readonly id: DatId

  private linkedResource: T | null = null
  private notFound = false

  constructor(id: DatId) {
    this.id = id
  }

  static of<T extends DatEntry>(id: DatId | null | undefined): DatLink<T> | null {
    if (!id) {
      return null
    }
    return new DatLink<T>(id)
  }

  getIfPresent(): T | null {
    return this.linkedResource
  }

  getOrPut(provider: (id: DatId) => T | null): T | null {
    if (this.linkedResource !== null) {
      return this.linkedResource
    }
    if (this.notFound) {
      return null
    }

    this.linkedResource = provider(this.id)
    this.notFound = this.linkedResource === null
    return this.linkedResource
  }
}
