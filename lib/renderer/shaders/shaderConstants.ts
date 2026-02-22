/**
 * Shared GLSL shader snippets for the XIM rendering pipeline.
 *
 * These match the Kotlin reference in xim/poc/gl/ShaderConstants.kt.
 * All lighting is computed in world space for point lights (vertex shader)
 * and in world/camera space for directional/fog (fragment shader).
 */

/**
 * Point light struct and per-vertex calculation function.
 * Used in vertex shaders to accumulate point light contributions.
 *
 * The attenuation model is: 1 / (constant + linear*d + quadratic*d^2)
 * with a hard range cutoff.
 */
export const pointLightVertexGlsl = /* glsl */ `
struct PointLight {
  vec3 position;
  vec4 color;
  float range;
  vec3 attenuation;
};

uniform PointLight pointLights[4];

vec4 pointLightCalc(in vec4 worldPos, in vec3 transformedNormal, in vec4 vertexColor, in PointLight pl) {
  float plDistance = distance(worldPos.xyz, pl.position);
  float constAttenuation = pl.attenuation.x;
  float linearAttenuation = plDistance * pl.attenuation.y;
  float quadraticAttenuation = plDistance * plDistance * pl.attenuation.z;
  float plDistanceFactor = (plDistance > pl.range) ? 0.0 : 1.0 / (constAttenuation + linearAttenuation + quadraticAttenuation);
  vec3 plDirection = normalize(pl.position - worldPos.xyz);
  float plNormalFactor = dot(transformedNormal, plDirection);
  return clamp(vertexColor * plNormalFactor * plDistanceFactor * pl.color, 0.0, 1.0);
}
`

/**
 * Diffuse (directional) light struct and per-fragment calculation function.
 * Used in fragment shaders. Two directional lights are supported.
 */
export const diffuseLightFragmentGlsl = /* glsl */ `
struct DiffuseLight {
  vec3 diffuseLightDir;
  vec4 diffuseLightColor;
};

uniform DiffuseLight diffuseLights[2];

vec4 diffuseLightCalc(in vec3 transformedNormal, in vec4 vertexColor, in DiffuseLight diffuseLight) {
  float diffuseFactor = clamp(dot(transformedNormal, diffuseLight.diffuseLightDir), 0.0, 1.0);
  return vertexColor * diffuseFactor * diffuseLight.diffuseLightColor;
}
`

/**
 * Distance-based fog calculation.
 * Linear fog between near and far distances in camera space.
 * Fog affects RGB but preserves the original alpha.
 *
 * When fog is disabled (near >= far), returns baseColor unmodified.
 * This avoids NaN from Infinity/Infinity division when noOpFog is used.
 */
/**
 * NOTE: `near` and `far` are reserved words in GLSL ES 1.00 (WebGL 1).
 * Three.js ShaderMaterial defaults to GLSL ES 1.00, so we use
 * `fogNearDist` / `fogFarDist` to avoid compilation failure.
 */
export const fogFragmentGlsl = /* glsl */ `
struct FogParams {
  float fogNearDist;
  float fogFarDist;
  vec4 fogColor;
};

uniform FogParams uFog;

vec4 fogCalc(in vec3 cameraSpacePosition, in vec4 baseColor) {
  float range = uFog.fogFarDist - uFog.fogNearDist;
  if (range <= 0.0) {
    return baseColor;
  }
  float dist = length(cameraSpacePosition);
  float fogFactor = clamp((uFog.fogFarDist - dist) / range, 0.0, 1.0);
  vec4 mixed = baseColor * fogFactor + uFog.fogColor * (1.0 - fogFactor);
  return vec4(mixed.rgb, baseColor.a);
}
`

/**
 * Common uniform declarations shared by all XIM shaders.
 */
export const commonUniformsGlsl = /* glsl */ `
uniform mat4 uProjMatrix;
uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
`

/**
 * Names of all lighting-related uniforms for programmatic access when
 * setting uniform values from the TypeScript side.
 */
export const POINT_LIGHT_COUNT = 4
export const DIFFUSE_LIGHT_COUNT = 2
