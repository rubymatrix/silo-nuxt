/**
 * XimSkinnedShader -- skeletal character mesh shader.
 *
 * Ported from xim/poc/gl/XimSkinnedShader.kt, adapted for GPU skinning
 * via per-joint mat4 uniforms (matching the existing Three.js approach).
 *
 * Handles:
 *  - GPU skeletal animation via joint matrix uniforms
 *  - Dual-position / dual-normal blending per bone pair
 *  - Full lighting: ambient + 2 directional + 4 point lights
 *  - Distance fog in camera space
 *  - Diffuse texture sampling
 *  - Color mask tinting
 *  - Alpha discard
 *
 * Future additions (stubs present):
 *  - Wrap texture (uWrapTexture) for spherical environment mapping
 *  - Specular cubemap (samplerCube) for specular highlights
 *
 * Vertex attribute layout:
 *  position0   (vec3)  -- bone-0 local position
 *  position1   (vec3)  -- bone-1 local position
 *  normal0     (vec3)  -- bone-0 local normal
 *  normal1     (vec3)  -- bone-1 local normal
 *  uv          (vec2)  -- texture coordinates
 *  color       (vec4)  -- vertex color RGBA (Three.js built-in)
 *  jointWeight (float) -- blend weight for bone-0 (1 - w = bone-1)
 *  joint0      (float) -- bone-0 index into joint[] array
 *  joint1      (float) -- bone-1 index into joint[] array
 */

import {
  pointLightVertexGlsl,
  diffuseLightFragmentGlsl,
  fogFragmentGlsl,
} from './shaderConstants'

/**
 * Builds the vertex shader with the joint array sized to the GPU's capacity.
 *
 * The joint array size is determined at runtime based on
 * `gl.getParameter(gl.MAX_VERTEX_UNIFORM_VECTORS)`.
 */
export function buildXimSkinnedVertexShader(maxJoints: number): string {
  // Uses Three.js built-in uniforms: modelMatrix, viewMatrix, projectionMatrix
  // These are automatically set by Three.js before each draw call.
  return /* glsl */ `
#define MAX_JOINTS ${maxJoints}

${pointLightVertexGlsl}

uniform mat4 joints[MAX_JOINTS];

attribute vec3 position0;
attribute vec3 position1;
attribute vec3 normal0;
attribute vec3 normal1;
attribute float jointWeight;
attribute float joint0;
attribute float joint1;

varying vec2 vUv;
varying vec4 vColor;
varying vec3 vWorldNormal;
varying vec3 vCameraPos;
varying vec4 vPointLightSum;

void main() {
  float w = clamp(jointWeight, 0.0, 1.0);
  int i0 = int(clamp(joint0, 0.0, float(MAX_JOINTS - 1)));
  int i1 = int(clamp(joint1, 0.0, float(MAX_JOINTS - 1)));

  vec4 skinnedNormal = w * (joints[i0] * vec4(normal0, 0.0)) + (1.0 - w) * (joints[i1] * vec4(normal1, 0.0));
  vec4 modelSpacePos = (joints[i0] * vec4(position0, w)) + (joints[i1] * vec4(position1, 1.0 - w));

  // Use Three.js built-in modelMatrix/viewMatrix for correct camera transforms
  vec4 worldPos = modelMatrix * modelSpacePos;
  vec3 worldNormal = normalize((modelMatrix * vec4(skinnedNormal.xyz, 0.0)).xyz);

  vPointLightSum = vec4(0.0);
  for (int i = 0; i < 4; i++) {
    vPointLightSum += pointLightCalc(worldPos, worldNormal, color, pointLights[i]);
  }

  vec4 cameraPos = viewMatrix * worldPos;

  vUv = uv;
  vColor = color;
  vWorldNormal = worldNormal;
  vCameraPos = cameraPos.xyz;

  gl_Position = projectionMatrix * cameraPos;
}
`
}

export const ximSkinnedFragmentShader = /* glsl */ `
${diffuseLightFragmentGlsl}

${fogFragmentGlsl}

uniform sampler2D uDiffuseTexture;
uniform bool uHasDiffuseTexture;
uniform float uAlpha;
uniform float uDiscardThreshold;
uniform vec4 uColorMask;

uniform vec4 ambientLightColor;

varying vec2 vUv;
varying vec4 vColor;
varying vec3 vWorldNormal;
varying vec3 vCameraPos;
varying vec4 vPointLightSum;

void main() {
  vec4 diffusePixel = uHasDiffuseTexture ? texture2D(uDiffuseTexture, vUv) : vec4(1.0);

  // Lighting accumulation: ambient + directional + point lights
  vec4 finalAmbientColor = vColor * ambientLightColor;

  vec4 df0 = diffuseLightCalc(vWorldNormal, vColor, diffuseLights[0]);
  vec4 df1 = diffuseLightCalc(vWorldNormal, vColor, diffuseLights[1]);

  vec4 litFragColor = clamp(vec4((finalAmbientColor + vPointLightSum + df0 + df1).rgb, vColor.a), 0.0, 1.0);

  // Matches Kotlin XimSkinnedShader: 2x RGB, 4x alpha (FFXI stores vertex color
  // at half intensity, so 4.0 * 0.5 * texAlpha = 2.0 * texAlpha effective).
  vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * diffusePixel.rgb, 4.0 * litFragColor.a * diffusePixel.a);
  coloredPixel.a *= uAlpha;

  if (coloredPixel.a < uDiscardThreshold) { discard; }

  gl_FragColor = uColorMask * fogCalc(vCameraPos, clamp(coloredPixel, 0.0, 1.0));
}
`
