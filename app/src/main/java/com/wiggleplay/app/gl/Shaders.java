package com.wiggleplay.app.gl;

/**
 * The one shader program of the 3D view: palette texture (or a flat colour), ambient plus one sun light,
 * and distance fog by the distance from the eye. Light and fog are computed per vertex (cheap on the TV's
 * GPU); the fragment shader only reads the texture and mixes.
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
            + "uniform vec3 uEye;\n"
            + "uniform float uFogNear;\n"
            + "uniform float uFogFar;\n"
            + "attribute vec3 aPos;\n"
            + "attribute vec3 aNormal;\n"
            + "attribute vec2 aUv;\n"
            + "varying vec2 vUv;\n"
            + "varying float vLight;\n"
            + "varying float vFog;\n"
            + "void main() {\n"
            + "  vec4 world = uModel * vec4(aPos, 1.0);\n"
            + "  vec3 n = normalize((uModel * vec4(aNormal, 0.0)).xyz);\n"
            + "  vLight = 0.62 + 0.38 * max(dot(n, -uSunDir), 0.0);\n"
            + "  float d = length(world.xyz - uEye);\n"
            + "  vFog = clamp((d - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);\n"
            + "  vUv = aUv;\n"
            + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
            + "}\n";

    /** Fragment shader (medium precision). */
    static final String FRAGMENT =
            "precision mediump float;\n"
            + "uniform sampler2D uTex;\n"
            + "uniform float uFlat;\n"
            + "uniform vec3 uColor;\n"
            + "uniform vec3 uFogColor;\n"
            + "uniform float uAlpha;\n"
            + "varying vec2 vUv;\n"
            + "varying float vLight;\n"
            + "varying float vFog;\n"
            + "void main() {\n"
            + "  vec3 base = uFlat > 0.5 ? uColor : texture2D(uTex, vUv).rgb;\n"
            + "  vec3 col = mix(base * vLight, uFogColor, vFog);\n"
            + "  gl_FragColor = vec4(col, uAlpha);\n"
            + "}\n";
}
