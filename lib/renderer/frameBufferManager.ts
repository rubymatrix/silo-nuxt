import { WebGLRenderTarget, type WebGLRenderer } from 'three'

export class FrameBufferManager {
  private screenBuffer: WebGLRenderTarget
  private blurBuffer: WebGLRenderTarget
  private hazeBuffer: WebGLRenderTarget

  private width: number
  private height: number

  constructor(width: number, height: number) {
    this.width = Math.max(1, Math.floor(width))
    this.height = Math.max(1, Math.floor(height))

    this.screenBuffer = new WebGLRenderTarget(this.width, this.height)
    this.blurBuffer = new WebGLRenderTarget(this.width, this.height)
    this.hazeBuffer = new WebGLRenderTarget(this.width, this.height)
  }

  resize(width: number, height: number): void {
    const nextWidth = Math.max(1, Math.floor(width))
    const nextHeight = Math.max(1, Math.floor(height))
    if (this.width === nextWidth && this.height === nextHeight) {
      return
    }

    this.width = nextWidth
    this.height = nextHeight

    this.screenBuffer.setSize(nextWidth, nextHeight)
    this.blurBuffer.setSize(nextWidth, nextHeight)
    this.hazeBuffer.setSize(nextWidth, nextHeight)
  }

  getScreenBuffer(): WebGLRenderTarget {
    return this.screenBuffer
  }

  getBlurBuffer(): WebGLRenderTarget {
    return this.blurBuffer
  }

  getHazeBuffer(): WebGLRenderTarget {
    return this.hazeBuffer
  }

  bindAndClearScreenBuffer(renderer: WebGLRenderer): void {
    renderer.setRenderTarget(this.screenBuffer)
    renderer.clear(true, true, true)
  }

  bindAndClearBlurBuffer(renderer: WebGLRenderer): void {
    renderer.setRenderTarget(this.blurBuffer)
    renderer.clear(true, false, false)
  }

  bindAndClearHazeBuffer(renderer: WebGLRenderer): void {
    renderer.setRenderTarget(this.hazeBuffer)
    renderer.clear(true, false, false)
  }

  dispose(): void {
    this.screenBuffer.dispose()
    this.blurBuffer.dispose()
    this.hazeBuffer.dispose()
  }
}
