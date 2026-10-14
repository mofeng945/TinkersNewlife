#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1013 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6211\u8bf4\u4f60\u53ea\u5728\u65cb\u81c2\u548c\u67d4\u5149\u4e0a\u5e94\u7528\u6e10\u53d8\u4e0d\u884c\u5417" \u2713
//   \u2605\u5173\u952e\u533a\u522b \u2713\uff1a
//     \u2605\u4ee5\u524d\u6211\u628a\u6e10\u53d8\u5f53"\u2605\u989c\u8272\u4e4b\u5916**\u53e6\u52a0\u4e00\u5c42\u53ef\u89c1\u5ea6**" \u2717
//       \u21d2 \u2605\u5b83\u628a\u6574\u4e2a\u56db\u8fb9\u5f62\u94fa\u6210\u4e00\u5757\u76d8\u5b50 \u2713
//     \u2605\u73b0\u5728 \u2713\uff1a\u2605\u6e10\u53d8**\u53ea\u5f53\u989c\u8272** \u2713\uff0c\u2605\u53ef\u89c1\u5ea6**\u53ea\u6765\u81ea\u65cb\u81c2/\u67d4\u5149/\u5149\u70b9** \u2713
//       \u21d2 \u2605\u6548\u679c \u2713\uff1a\u2605**\u81c2\u6839\u504f\u9ed1 \u2192 \u81c2\u5c16\u504f\u767d** \u2713\uff08\u2605\u4f60\u8981\u7684"\u5185\u9ed1\u8fb9\u767d"\u5c31\u5728\u81c2\u4e0a \u2713\uff09
//       \u002b \u2605\u6838\u5fc3\u4fdd\u6301"\u6b63\u5e38\u67d4\u5149"\uff08\u2605\u767d \u2713\uff09\u002b \u2605\u547c\u5438\u7f29\u653e \u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.82 + 0.18 * wave;
    float zoom = 1.0 + 0.10 * wave;

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u53ea\u5f53"\u989c\u8272"\u7528\u7684\u534a\u5f84\u6e10\u53d8\uff1a\u2605\u5185\u9ed1 \u2192 \u4e2d\u7070 \u2192 \u5916\u767d \u2713
    vec3 grad = mix(vec3(0.04, 0.04, 0.06), vec3(0.55, 0.58, 0.64), smoothstep(0.0, 0.55, r));
    grad = mix(grad, vec3(1.0, 1.0, 1.0), smoothstep(0.55, 1.0, r));

    // \u2605\u4e2d\u5fc3\u67d4\u5149\uff08\u2605\u5f88\u5c0f \u2713\uff09
    float core = smoothstep(0.18, 0.0, r);
    core *= core;

    // \u2605\u65cb\u8f6c\u7684\u661f\u8292\u81c2\uff08\u2605\u5ef6\u4f38\u5230\u683c\u5b50\u8fb9\u7f18 \u2713\uff09
    float ang = atan(p.y, p.x) + EffectTime * 0.50;
    float arm1 = pow(abs(cos(ang * 3.0)), 16.0) * smoothstep(1.0, 0.06, r);
    float arm2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 32.0) * smoothstep(0.92, 0.04, r);

    // \u2605\u9038\u6563\u5149\u70b9 \u2713
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
        motes += smoothstep(0.12, 0.0, dd) * (1.0 - phase);
    }

    // \u2605\u53ef\u89c1\u5ea6\uff1a\u2605**\u53ea\u6765\u81ea\u81c2/\u6838\u5fc3/\u5149\u70b9** \u2713\uff08\u2605\u6ca1\u6709\u4efb\u4f55\u586b\u5145 \u2713\uff09
    float a = clamp(core * 0.70 * breath + (arm1 * 0.62 + arm2 * 0.36) * breath + motes * 0.50, 0.0, 1.0);
    if (a < 0.05) {
        discard;
    }

    // \u2605\u989c\u8272 \u2713\uff1a\u2605\u81c2\u7528\u6e10\u53d8\uff08\u2605\u6839\u9ed1\u5c16\u767d \u2713\uff09\uff0c\u2605\u4f46\u6838\u5fc3\u8981"\u6b63\u5e38\u67d4\u5149"\u21d2 \u2605\u5f80\u767d\u63d0 \u2713
    vec3 col = mix(grad, vec3(1.0), clamp(core * 2.2, 0.0, 1.0));
    col += vec3(1.0) * (arm2 * 0.25);                 // \u2605\u7ec6\u81c2\u504f\u767d\u4e00\u70b9 \u2713
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
