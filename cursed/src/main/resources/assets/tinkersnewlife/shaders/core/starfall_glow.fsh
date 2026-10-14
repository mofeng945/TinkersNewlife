#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1011 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u661f\u8292\u624b\u81c2\u53ef\u4ee5\u4fdd\u7559\u5ef6\u4f38\u5230\u7269\u54c1\u680f\u8fb9\u6846\uff0c\u4f46\u662f\u5185\u5c42\u53d1\u5149
//   \u5c31\u6b63\u5e38\u6838\u5fc3\u67d4\u5149\uff0c\u5e76\u4e14\u628a\u6211\u5185\u9ed1\u8fb9\u767d\u7684\u6e10\u53d8\u5f04\u56de\u6765" \u2713
//   \u2605\u4e09\u5c42 \u2713\uff1a
//     \u2460 \u2605\u6b63\u5e38\u6838\u5fc3\u67d4\u5149\uff08\u2605\u4e2d\u5fc3\u6700\u4eae\uff0c\u2605\u5c0f\u8303\u56f4 \u2713\uff09
//     \u2461 \u2605**\u5185\u9ed1 \u2192 \u4e2d\u7070 \u2192 \u8fb9\u767d**\u7684\u534a\u5f84\u6e10\u53d8 \u2713
//          \uff08\u2605\u53ef\u89c1\u5ea6\u9760\u5916\uff1a\u2605\u4e2d\u5fc3\u51e0\u4e4e\u4e0d\u53ef\u89c1 \u21d2 \u2605\u4e0d\u4f1a\u53d8\u6210\u9ed1\u76d8 \u2713\uff09
//     \u2462 \u2605\u661f\u8292\u624b\u81c2\uff08\u2605\u4e00\u76f4\u5ef6\u5230\u683c\u5b50\u8fb9\u7f18 \u2713\uff09\u002b \u2605\u9038\u6563\u5149\u70b9 \u2713
//     \u2605\u5168\u90e8\u968f\u547c\u5438\u7f29\u653e \u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.80 + 0.20 * wave;
    float zoom = 1.0 + 0.10 * wave;                 // \u2605\u547c\u5438\u7f29\u653e \u2713

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u2460 \u6b63\u5e38\u6838\u5fc3\u67d4\u5149\uff08\u2605\u53ea\u5230 r=0.40 \u2713\uff09
    float core = smoothstep(0.40, 0.0, r);
    core *= core;

    // \u2605\u2461 \u5185\u9ed1 \u2192 \u4e2d\u7070 \u2192 \u8fb9\u767d \u2713
    vec3 grad = mix(vec3(0.03, 0.03, 0.05), vec3(0.52, 0.55, 0.61), smoothstep(0.0, 0.55, r));
    grad = mix(grad, vec3(1.0), smoothstep(0.55, 1.0, r));
    // \u2605\u53ef\u89c1\u5ea6\uff1a\u2605\u4e2d\u5fc3\u51e0\u4e4e 0 \u2713\uff0c\u2605\u5230\u8fb9\u7f18\u524d\u53c8\u6de1\u51fa \u2713
    float rampA = smoothstep(0.12, 0.80, r) * smoothstep(1.0, 0.88, r);

    // \u2605\u2462 \u661f\u8292\u624b\u81c2\uff08\u2605\u5ef6\u4f38\u5230\u683c\u5b50\u8fb9\u7f18 \u2713\uff09
    float ang = atan(p.y, p.x) + EffectTime * 0.50;
    float arms = pow(abs(cos(ang * 3.0)), 10.0) * smoothstep(1.0, 0.10, r);
    float arms2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 20.0) * smoothstep(0.95, 0.06, r);

    // \u2605\u2463 \u9038\u6563\u5149\u70b9 \u2713
    float motes = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float spd = 0.10 + 0.06 * fi;
        vec2 g = p * (4.0 + fi * 2.0) + vec2(EffectTime * spd, -EffectTime * spd * 0.6);
        vec2 cell = floor(g);
        vec2 f = fract(g) - vec2(0.5);
        float h1 = fract(sin(dot(cell, vec2(127.1, 311.7)) + fi * 7.3) * 43758.5453);
        float h2 = fract(sin(dot(cell, vec2(269.5, 183.3)) + fi * 3.1) * 43758.5453);
        vec2 dir = normalize(cell + vec2(0.5) + vec2(1.0e-4));
        float phase = fract(h1 + EffectTime * (0.12 + 0.20 * h2));
        float dd = length(f - dir * (phase * 0.8));
        motes += smoothstep(0.13, 0.0, dd) * (1.0 - phase);
    }

    float a = clamp(core * 0.60 * breath + rampA * 0.30 * breath
            + (arms * 0.55 + arms2 * 0.32) * breath + motes * 0.45, 0.0, 1.0);
    if (a < 0.06) {
        discard;                                    // \u2605\u2605 \u6de1\u7684\u5730\u65b9\u4e0d\u753b \u2713
    }

    vec3 col = mix(grad, vec3(1.0), clamp(core * 0.85 + arms + arms2, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
