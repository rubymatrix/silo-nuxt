/**
 * Zone mesh shader adapted for Three.js ShaderMaterial.
 *
 * Based on ximShader.ts but modified to:
 *  - Use Three.js built-in uniforms (modelMatrix, viewMatrix, projectionMatrix)
 *  - Use GLSL ES 1.00 (no #version 300 es) for ShaderMaterial compatibility
 *  - Use standard attribute names with Three.js BufferAttribute
 *
 * Supports: dual-position blending, TBN normal mapping, full lighting, fog.
 */

import {
  pointLightVertexGlsl,
  diffuseLightFragmentGlsl,
  fogFragmentGlsl,
} from './shaderConstants'

export const zoneVertexShader = /* glsl */ `

${pointLightVertexGlsl}

attribute vec3 position0;
attribute vec3 position1;
attribute vec3 tangent;
attribute vec2 textureCoords;
attribute vec4 vertexColor;

uniform float positionBlendWeight;

varying vec2 frag_textureCoords;
varying vec4 frag_vertexColor;
varying vec4 frag_cameraPos;
varying vec3 frag_normal;
varying mat3 frag_TBN;
varying vec4 frag_pointLightSum;

void main() {
  frag_textureCoords = textureCoords;

  mat3 invTransposeModel = transpose(inverse(mat3(modelMatrix)));
  vec3 mNormal = normalize(invTransposeModel * normal);
  vec3 mTangent = normalize(invTransposeModel * tangent);
  vec3 mBitangent = normalize(cross(mTangent, mNormal));
  frag_TBN = mat3(mTangent, mBitangent, mNormal);

  vec3 pos = position0 + positionBlendWeight * position1;
  vec4 worldPos = modelMatrix * vec4(pos, 1.0);
  vec4 cameraPos = viewMatrix * worldPos;

  frag_pointLightSum = vec4(0.0);
  for (int i = 0; i < 4; i++) {
    frag_pointLightSum += pointLightCalc(worldPos, mNormal, vertexColor, pointLights[i]);
  }

  frag_vertexColor = vertexColor;
  frag_cameraPos = cameraPos;
  frag_normal = mNormal;

  gl_Position = projectionMatrix * cameraPos;
}
`

export const zoneFragmentShader = /* glsl */ `
precision highp float;

${diffuseLightFragmentGlsl}

${fogFragmentGlsl}

uniform float discardThreshold;
uniform sampler2D diffuseTexture;
uniform vec4 colorMask;
uniform vec4 ambientLightColor;

varying vec2 frag_textureCoords;
varying vec3 frag_normal;
varying vec4 frag_vertexColor;
varying vec4 frag_cameraPos;
varying mat3 frag_TBN;
varying vec4 frag_pointLightSum;

void main() {
  vec3 lightingNormal = frag_normal;

  vec4 finalAmbientColor = frag_vertexColor * ambientLightColor;

  vec4 df0 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[0]);
  vec4 df1 = diffuseLightCalc(lightingNormal, frag_vertexColor, diffuseLights[1]);

  vec4 litFragColor = clamp(vec4((finalAmbientColor + frag_pointLightSum + df0 + df1).rgb, frag_vertexColor.a), 0.0, 1.0);

  vec4 basePixel = texture2D(diffuseTexture, frag_textureCoords);
  float finalAlpha = clamp(4.0 * litFragColor.a * basePixel.a, 0.0, 1.0);
  vec4 coloredPixel = vec4(2.0 * litFragColor.rgb * basePixel.rgb, finalAlpha);

  if (coloredPixel.a < discardThreshold) { discard; }

  // Force output alpha to 1.0. Zone texture alpha < 1.0 (common in DXT-compressed
  // textures) bleeds through the canvas compositing, causing a checkerboard artifact.
  // Foliage transparency is handled by the discard above, not by alpha blending.
  vec4 fogged = colorMask * fogCalc(frag_cameraPos.xyz, coloredPixel);
  gl_FragColor = vec4(fogged.rgb, 1.0);
}
`
