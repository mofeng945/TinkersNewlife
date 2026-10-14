#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1012 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u4f60\u5c31\u4e0d\u80fd\u5355\u72ec\u753b\u65cb\u81c2\u5417\uff0c\u975e\u8981\u53ea\u8981\u753b\u661f\u8292\u5c31\u628a\u6574\u4e2a\u76d8\u5b50\u753b\u51fa\u6765" \u2713
//   \u2605\u76d8\u5b50\u5230\u5e95\u4ece\u54ea\u6765 \u2717\uff1a\u2605\u5c31\u662f\u90a3\u5c42"\u5185\u9ed1\u2192\u8fb9\u767d"\u7684**\u6e10\u53d8\u586b\u5145** \u2713
//     \uff08\u2605``grad`` \u002b ``rampA`` \u2713\uff09\u2014\u2014 \u2605\u5b83\u628a\u6574\u4e2a\u56db\u8fb9\u5f62\u94fa\u4e0a\u4e86\u4e00\u5c42\u53ef\u89c1\u5ea6 \u2717
//   \u2605\u73b0\u5728 \u2713\uff1a\u2605**\u5f7b\u5e95\u5220\u6389\u6e10\u53d8\u5c42** \u2713\uff0c\u2605\u53ea\u753b\uff1a
//     \u2460 \u2605\u65cb\u8f6c\u7684\u661f\u8292\u81c2\uff08\u2605\u4e00\u76f4\u5ef6\u5230\u683c\u5b50\u8fb9\u7f18 \u2713\uff09
//     \u2461 \u2605**\u5f88\u5c0f**\u7684\u6838\u5fc3\u67d4\u5149\uff08\u2605\u534a\u5f84\u4e0d\u5230\u56db\u5206\u4e4b\u4e00 \u2713\uff09
//     \u2462 \u2605\u9038\u6563\u5149\u70b9 \u2713
//     \u2605\u5176\u4f59\u5730\u65b9**\u4e00\u5f8b discard** \u2713 \u21d2 \u2605\u4e0d\u53ef\u80fd\u518d\u6709\u76d8\u5b50 \u2713\u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.82 + 0.18 * wave;
    float zoom = 1.0 + 0.10 * wave;                 // \u2605\u547c\u5438\u7f29\u653e \u2713

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u2461 \u5f88\u5c0f\u7684\u6838\u5fc3\u67d4\u5149\uff08\u2605r \u2264 0.16 \u2713\uff09
    float core = smoothstep(0.16, 0.0, r);
    core *= core;

    // \u2605\u2460 \u65cb\u8f6c\u7684\u661f\u8292\u81c2\uff08\u2605\u5ef6\u4f38\u5230\u683c\u5b50\u8fb9\u7f18 \u2713\uff09
    float ang = atan(p.y, p.x) + EffectTime * 0.50;
    float arm1 = pow(abs(cos(ang * 3.0)), 16.0) * smoothstep(1.0, 0.06, r);          // \u26056 \u9053\u4e3b\u81c2 \u2713
    float arm2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 32.0) * smoothstep(0.92, 0.04, r);  // \u26056 \u9053\u7ec6\u81c2 \u2713

    // \u2605\u2462 \u968f\u673a\u9038\u6563\u7684\u5c0f\u5149\u70b9 \u2713
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

    // \u2605\u5408\u6210\uff1a\u2605\u53ea\u6709"\u7ebf"\u4e0e"\u5c0f\u70b9" \u2713\uff0c\u2605\u6ca1\u6709\u4efb\u4f55"\u9762" \u2713
    float a = clamp(core * 0.65 * breath + (arm1 * 0.60 + arm2 * 0.35) * breath + motes * 0.50, 0.0, 1.0);
    if (a < 0.05) {
        discard;
    }

    // \u2605\u7070\u767d\uff08\u2605\u81c2\u4e0e\u6838\u5fc3\u504f\u767d \u2713\uff09
    vec3 col = mix(vec3(0.76, 0.80, 0.90), vec3(1.0), clamp(core * 2.0 + arm1 + arm2, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
