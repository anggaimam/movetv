package com.wiggleplay.app.gl;

/**
 * The one shader program of the 3D view: palette texture (or a flat colour), a hemisphere light plus one coloured sun,
 * a procedural detail texture on the static world, and distance fog. Light and fog are computed per vertex; the
 * fragment shader reads the palette, one detail sample, and mixes.
 *
 * <p>Detail: the world position (scaled) is a varying; the vertex shader picks the quadrant of the detail atlas, the
 * strength and the scale from the palette block the vertex's uv lies in (one block per triangle, so they do not change inside
 * a triangle). {@code uDetailOn}: 0 = none, 1 = from the uv, 2 = force the ground sand (flat items without uvs).
 */
final class Shaders {

    private Shaders() { }

    /** Sun direction (the way the light travels), normalised in code before it is sent to the shader. */
    static final float SUN_X = -0.4f, SUN_Y = -0.8f, SUN_Z = -0.45f;

    /** Vertex shader. */
    static final String VERTEX =
            "uniform mat4 uMvp;\n"
            + "uniform mat4 uModel;\n"
            + "uniform vec3 uSunDir;\n"
            + "uniform vec3 uSkyAmb;\n"
            + "uniform vec3 uGroundAmb;\n"
            + "uniform vec3 uSunColor;\n"
            + "uniform float uUnlit;\n"
            + "uniform float uDetailOn;\n"
            + "uniform vec3 uEye;\n"
            + "uniform float uFogNear;\n"
            + "uniform float uFogFar;\n"
            + "attribute vec3 aPos;\n"
            + "attribute vec3 aNormal;\n"
            + "attribute vec2 aUv;\n"
            + "varying vec2 vUv;\n"
            + "varying vec3 vLight;\n"
            + "varying float vFog;\n"
            + "varying vec2 vDetUv;\n"
            + "varying vec3 vDet;\n"
            + "void main() {\n"
            + "  vec4 world = uModel * vec4(aPos, 1.0);\n"
            + "  vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);\n"
            + "  vec3 hemi = mix(uGroundAmb, uSkyAmb, 0.5 + 0.5 * n.y) + uSunColor * max(dot(n, -uSunDir), 0.0);\n"
            + "  vLight = uUnlit > 0.5 ? vec3(1.0) : hemi;\n"
            + "  float d = length(world.xyz - uEye);\n"
            + "  vFog = clamp((d - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);\n"
            + "  vUv = aUv;\n"
            // palette block index of the vertex: 16x16 blocks, 32 per row, the first row is not a block row
            + "  float blk = floor(aUv.x * 32.0) + 32.0 * (floor(aUv.y * 32.0) - 1.0);\n"
            + "  float q = 3.0;\n"
            + "  float strength = 0.5;\n"
            + "  float scale = 0.5;\n"
            + "  if (blk < 10.0) { q = 1.0; strength = 1.0; scale = 0.5; }\n"
            + "  else if (blk > 15.5 && blk < 18.5) { q = 0.0; strength = 1.2; scale = 0.25; }\n"
            + "  else if (blk > 20.5 && blk < 30.5) { q = 2.0; strength = 0.8; scale = 0.25; }\n"
            + "  if (uDetailOn > 1.5) { q = 0.0; strength = 1.2; scale = 0.25; }\n"
            + "  vDet = vec3(mod(q, 2.0) * 0.5, floor(q * 0.5) * 0.5, uDetailOn > 0.5 ? strength : 0.0);\n"
            + "  vDetUv = world.xz * scale;\n"
            + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
            + "}\n";

    /** Fragment shader (medium precision, with a high precision varying for the detail uv where the GPU has it). */
    static final String FRAGMENT =
            "precision mediump float;\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
            + "varying highp vec2 vDetUv;\n"
            + "#else\n"
            + "varying mediump vec2 vDetUv;\n"
            + "#endif\n"
            + "uniform sampler2D uTex;\n"
            + "uniform sampler2D uDetail;\n"
            + "uniform float uFlat;\n"
            + "uniform vec3 uColor;\n"
            + "uniform vec3 uFogColor;\n"
            + "uniform float uAlpha;\n"
            + "varying vec2 vUv;\n"
            + "varying vec3 vLight;\n"
            + "varying float vFog;\n"
            + "varying vec3 vDet;\n"
            + "void main() {\n"
            + "  vec3 base = uFlat > 0.5 ? uColor : texture2D(uTex, vUv).rgb;\n"
            + "  if (vDet.z > 0.001) {\n"
            + "    float d = texture2D(uDetail, vDet.xy + fract(vDetUv) * 0.496 + 0.002).r;\n"
            + "    base *= 1.0 + (d - 0.5) * vDet.z * (1.0 - vFog);\n"
            + "  }\n"
            + "  vec3 col = mix(base * vLight, uFogColor, vFog);\n"
            + "  gl_FragColor = vec4(col, uAlpha);\n"
            + "}\n";
}
