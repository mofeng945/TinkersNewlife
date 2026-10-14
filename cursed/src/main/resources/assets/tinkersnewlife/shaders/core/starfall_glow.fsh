#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1002 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6211\u60f3\u8981\u65cb\u8f6c\u7684\u661f\u578b\u5149\u6548\uff0c\u4f34\u6709\u547c\u5438\u611f\uff0c\u5e76\u4e14\u6709\u5c0f\u578b\u5149\u70b9\u968f\u673a\u9038\u6563" \u2713
//   \u2605\u4e09\u5c42\u53e0\u52a0 \u2713\uff1a
//     \u2460 \u2605\u4e2d\u5fc3\u5149\u6655\uff08\u2605\u5e95\u5b50 \u2713\uff09
//     \u2461 \u2605**\u65cb\u8f6c\u7684\u661f\u8292**\uff08\u2605\u6781\u5750\u6807\u91cc\u7684 cos \u5c16\u89d2 \u002b \u65f6\u95f4\u8f6c\u89d2 \u2713\uff09
//     \u2462 \u2605**\u968f\u673a\u9038\u6563\u7684\u5c0f\u5149\u70b9**\uff08\u2605\u683c\u5b50\u54c8\u5e0c \u002b \u5411\u5916\u6f02 \u002b \u5230\u8fb9\u6de1\u51fa \u2713\uff09
//   \u2605\u5168\u90e8\u7a0b\u5e8f\u5316 \u2713\uff08\u2605\u4e0d\u9700\u8981\u65b0\u8d34\u56fe \u2713\uff09
void main() {
    vec2 p = vUv - vec2(0.5);
    float r = length(p) * 2.0;

    // \u2605\u547c\u5438\uff08\u2605\u4e0d\u5feb\u4e0d\u6162 \u2713\uff09
    float breath = 0.75 + 0.25 * sin(EffectTime * 1.6);

    // \u2460 \u4e2d\u5fc3\u5149\u6655 \u2713
    float glow = smoothstep(1.0, 0.05, r);
    glow *= glow;

    // \u2461 \u65cb\u8f6c\u7684\u516d\u89d2\u661f\u8292 \u2713
    float ang = atan(p.y, p.x) + EffectTime * 0.55;          // \u2605\u8f6c\u901f \u2713
    float spokes = pow(abs(cos(ang * 3.0)), 9.0);            // \u26056 \u9053\u5149\u8292 \u2713
    float star = spokes * smoothstep(1.0, 0.08, r);
    // \u2605\u518d\u52a0\u4e00\u5c42\u7ec6\u957f\u661f\u8292\uff08\u2605\u66f4\u50cf"\u661f\u578b" \u2713\uff09
    float spokes2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 18.0);
    float star2 = spokes2 * smoothstep(0.95, 0.05, r);

    // \u2462 \u968f\u673a\u9038\u6563\u7684\u5c0f\u5149\u70b9\uff08\u2605\u4e09\u5c42 \u2713\uff09
    float motes = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float spd = 0.10 + 0.06 * fi;
        vec2 g = p * (4.0 + fi * 2.0) + vec2(EffectTime * spd, -EffectTime * spd * 0.6);
        vec2 cell = floor(g);
        vec2 f = fract(g) - vec2(0.5);
        float h1 = fract(sin(dot(cell, vec2(127.1, 311.7)) + fi * 7.3) * 43758.5453);
        float h2 = fract(sin(dot(cell, vec2(269.5, 183.3)) + fi * 3.1) * 43758.5453);
        // \u2605\u6bcf\u4e2a\u683c\u5b50\u4e00\u4e2a"\u5411\u5916\u6f02"\u7684\u5149\u70b9 \u2713
        vec2 dir = normalize(cell + vec2(0.5) + vec2(1.0e-4));
        float phase = fract(h1 + EffectTime * (0.12 + 0.20 * h2));
        vec2 target = dir * (phase * 0.85);
        float dd = length(f - target);
        motes += smoothstep(0.16, 0.0, dd) * (1.0 - phase);
    }

    float a = clamp(glow * 0.40 * breath + (star * 0.55 + star2 * 0.35) * breath + motes * 0.55, 0.0, 1.0);
    vec3 col = mix(vec3(0.84, 0.87, 0.93), vec3(1.0), clamp(star + glow, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
