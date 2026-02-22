import {
  AdditiveBlending,
  Mesh,
  NoBlending,
  OrthographicCamera,
  PlaneGeometry,
  Scene,
  ShaderMaterial,
  Vector2,
  Vector4,
  type WebGLRenderTarget,
  type WebGLRenderer,
} from 'three'

export interface BlurOffset {
  readonly x: number
  readonly y: number
}

export interface BlurCompositePass {
  readonly offset: BlurOffset
  readonly blendMode: 'additive'
}

export interface BlurCompositePlan {
  readonly requiresBlur: boolean
  readonly passes: readonly BlurCompositePass[]
}

export function buildBlurCompositePlan(blurOffsets: readonly BlurOffset[]): BlurCompositePlan {
  const passes = blurOffsets
    .filter((offset) => Number.isFinite(offset.x) && Number.isFinite(offset.y))
    .map((offset) => ({
      offset,
      blendMode: 'additive' as const,
    }))

  return {
    requiresBlur: passes.length > 0,
    passes,
  }
}

export interface BlitColor {
  readonly r: number
  readonly g: number
  readonly b: number
  readonly a: number
}

export interface BlitOptions {
  readonly offset?: BlurOffset
  readonly colorMask?: BlitColor
  readonly blendMode?: 'copy' | 'additive'
  readonly clear?: boolean
}

export class PostProcessBlitter {
  private readonly scene = new Scene()
  private readonly camera = new OrthographicCamera(-1, 1, 1, -1, 0, 1)
  private readonly material = new ShaderMaterial({
    uniforms: {
      uTexture: { value: null as WebGLRenderTarget['texture'] | null },
      uOffset: { value: new Vector2(0, 0) },
      uColor: { value: new Vector4(1, 1, 1, 1) },
    },
    vertexShader: `
      varying vec2 vUv;

      void main() {
        vUv = uv;
        gl_Position = vec4(position.xy, 0.0, 1.0);
      }
    `,
    fragmentShader: `
      uniform sampler2D uTexture;
      uniform vec2 uOffset;
      uniform vec4 uColor;

      varying vec2 vUv;

      void main() {
        gl_FragColor = texture2D(uTexture, vUv + uOffset) * uColor;
      }
    `,
    depthTest: false,
    depthWrite: false,
    transparent: true,
  })
  private readonly quad = new Mesh(new PlaneGeometry(2, 2), this.material)

  constructor() {
    this.scene.add(this.quad)
  }

  blit(
    renderer: WebGLRenderer,
    source: WebGLRenderTarget,
    destination: WebGLRenderTarget | null,
    options: BlitOptions = {},
  ): void {
    const color = options.colorMask ?? { r: 1, g: 1, b: 1, a: 1 }
    const offset = options.offset ?? { x: 0, y: 0 }
    const blendMode = options.blendMode ?? 'copy'

    this.material.uniforms.uTexture.value = source.texture
    this.material.uniforms.uOffset.value.set(offset.x, offset.y)
    this.material.uniforms.uColor.value.set(color.r, color.g, color.b, color.a)
    this.material.blending = blendMode === 'additive' ? AdditiveBlending : NoBlending

    const previousTarget = renderer.getRenderTarget()
    const previousAutoClear = renderer.autoClear
    renderer.autoClear = false

    renderer.setRenderTarget(destination)
    if (options.clear) {
      renderer.clear(true, false, false)
    }
    renderer.render(this.scene, this.camera)

    renderer.setRenderTarget(previousTarget)
    renderer.autoClear = previousAutoClear
  }

  dispose(): void {
    this.quad.geometry.dispose()
    this.material.dispose()
  }
}
