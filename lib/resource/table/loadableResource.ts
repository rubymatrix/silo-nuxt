export interface LoadableResource {
  preload(): Promise<void>
  isFullyLoaded(): boolean
}
