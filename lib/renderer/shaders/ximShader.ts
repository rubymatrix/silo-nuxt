/**
 * XimShader -- static/zone mesh shader.
 *
 * Ported from xim/poc/gl/XimShader.kt.
 *
 * Handles:
 *  - Dual-position blending via `positionBlendWeight` (for zone mesh morphing)
 *  - TBN matrix for normal mapping (bump maps)
 *  - Full lighting: ambient + 2 directional + 4 point lights
 *  - Distance fog in camera space
 *  - Diffuse texture + optional normal map
 *  - Color mask tinting
 *  - Alpha discard
 *
 * Vertex attribute layout (matches Kotlin GlBufferBuilder stride 0x44):
 *  location 0: position0     (vec3)  -- base position
 *  location 1: position1     (vec3)  -- blend target (zero if unused)
 *  location 2: normal        (vec3)
 *  location 3: tangent       (vec3)
 *  location 4: textureCoords (vec2)
 *  location 8: vertexColor   (vec4)  -- RGBA normalized
 */

import {
  pointLightVertexGlsl,
  diffuseLightFragmentGlsl,
  fogFragmentGlsl,
} from './shaderConstants'

export const ximVertexShader = /* glsl */ `
#version 300 es

${pointLightVertexGlsl}

uniform sampler2D diffuseTexture;

uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;

uniform float positionBlendWeight;

layout(location=0) in vec3 position0;
layout(location=1) in vec3 position1;

layout(location=2) in vec3 normal;
layout(location=3) in vec3 tangent;

layout(location=4) in vec2 textureCoords;
layout(location=8) in vec4 vertexColor;

out vec2 frag_textureCoords;
out vec4 frag_vertexColor;
out vec4 frag_cameraPos;
out vec4 frag_worldPos;
out vec3 frag_normal;
out mat3 frag_TBN;
out vec4 frag_pointLightSum;

void main() {
  frag_textureCoords = textureCoords;

  mat3 invTransposeModel = transpose(inverse(mat3(uModelMatrix)));
  vec3 mNormal = normalize(invTransposeModel * normal);
  vec3 mTangent = normalize(invTransposeModel * tangent);
  vec3 mBitangent = normalize(cross(mTangent, mNormal));
  frag_TBN = mat3(mTangent, mBitangent, mNormal);

  vec3 position = position0 + positionBlendWeight * position1;
  vec4 worldPos = uModelMatrix * vec4(position, 1.0);
  vec4 cameraPos = uViewMatrix * worldPos;

  frag_pointLightSum = vec4(0.0);
  for (int i = 0; i < 4; i++) {
    frag_pointLightSum += pointLightCalc(worldPos, mNormal, vertexColor, pointLights[i]);
  }

  frag_vertexColor = vertexColor;
  frag_cameraPos = cameraPos;
  frag_worldPos = worldPos;
  frag_normal = mNormal;

  gl_Position = uProjMatrix * cameraPos;
}
`

export const ximFragmentShader = /* glsl */ `
#version 300 es
precision highp float;

${diffuseLightFragmentGlsl}

${fogFragmentGlsl}

uniform float discardThreshold;
uniform sampler2D diffuseTexture;
uniform vec4 colorMask;

uniform vec4 ambientLightColor;

uniform sampler2D normalTexture;
uniform float enableNormalTexture;

in vec2 frag_textureCoords;
in vec3 frag_normal;
in vec4 frag_vertexColor;
in vec4 frag_worldPos;
in vec4 frag_cameraPos;
in mat3 frag_TBN;
in vec4 frag_pointLightSum;

out vec4 outColor;

void main() {
  vec3 lightingNormal;
  if (enableNormalTexture > 0.0) {
    vec3 sampledNormal = texture(normalTexture, frag_textureCoords).rgb;
    sampledNormal = sampledNormal * 2.0 - 1.0;
    lightingNormal = normalize(frag_TBN * sampledNormal);
  } else {
    lightingNormal = frag_normal;
  }

  // Lighting accumulation
  vec4 finalAmbientColor = frag_vertexColor * ambientLightColor;

  vec4 df0 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[0]);
  vec4 df1 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[1]);

  vec4 litFragColor = clamp(vec4((finalAmbientColor + frag_pointLightSum + df0 + df1).rgb, frag_vertexColor.a), 0.0, 1.0);

  vec4 basePixel = texture(diffuseTexture, frag_textureCoords);
  vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * basePixel.rgb, 4.0 * litFragColor.a * basePixel.a);

  if (coloredPixel.a < discardThreshold) { discard; }

  outColor = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);
}
`

/**
 * Uniform location names for the XimShader.
 * Used when setting uniforms from the TypeScript renderer.
 */
export interface XimShaderUniforms {
  readonly uProjMatrix: string
  readonly uModelMatrix: string
  readonly uViewMatrix: string
  readonly positionBlendWeight: string
  readonly diffuseTexture: string
  readonly normalTexture: string
  readonly enableNormalTexture: string
  readonly discardThreshold: string
  readonly colorMask: string
  readonly ambientLightColor: string
}
