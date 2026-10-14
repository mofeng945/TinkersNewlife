#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1009 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u597d\u4e11\u554a\uff0c\u4e3a\u4ec0\u4e48\u5185\u5c42\u5149\u6548\u8001\u662f\u6709\u4e00\u4e2a\u767d\u8272\u7684\u5e95" \u2713
//   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u6211\u4e0a\u4e00\u7248\u7684"\u7ec6\u73af\u5e26"\u5b9e\u9645\u662f
//     `smoothstep(0.30,0.52,r) * smoothstep(0.86,0.62,r)` \u2717 \u21d2 \u2605\u5b83\u662f**\u4e00\u4e2a 0.30~0.86 \u7684\u5bbd\u5e26** \u2713
//     \u002b \u2605\u661f\u8292\u7684 `smoothstep(0.90,0.06,r)` \u628a\u534a\u5f84\u5185\u90fd\u4eae\u4e86 \u2717
//     \u21d2 \u2605\u5408\u8d77\u6765\u5c31\u662f**\u4e00\u5f20\u767d\u76d8** \u2713\u2713
//   \u2605\u73b0\u5728 \u2713\uff1a\u2605**\u5b8c\u5168\u6ca1\u6709"\u9762"** \u2713\u2717
//     \u2605\u53ea\u6709 \u2713\uff1a\u2605\u7ec6\u661f\u8292\uff08\u2605\u66f4\u9ad8\u6b21\u5e42 \u21d2 \u2605\u66f4\u7ec6 \u2713\uff09\u002b \u2605\u4e00\u5708\u6781\u7a84\u7684\u8fb9 \u002b \u2605\u9038\u6563\u5149\u70b9 \u2713
//     \u002b \u2605\u6781\u6de1\u7684\u6838\u5fc3\uff08\u2605alpha \u4e0a\u9650 0.10 \u2713\uff09\u002b \u2605\u547c\u5438\u7f29\u653e \u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.78 + 0.22 * wave;
    float zoom = 1.0 + 0.12 * wave;                  // \u2605\u547c\u5438\u7f29\u653e \u2713

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u2460 \u6781\u6de1\u7684\u6838\u5fc3\uff08\u2605\u53ea\u5230 r=0.45\uff0c\u2605\u4e0a\u9650\u5f88\u4f4e \u2713\uff09
    float core = smoothstep(0.45, 0.0, r);
    core *= core * 0.10;

    // \u2605\u2461 \u7ec6\u661f\u8292\uff08\u2605\u6b21\u5e42\u63d0\u5230 14/26 \u21d2 \u2605\u7ebf\u6761\u66f4\u7ec6 \u2713\uff09
    float ang = atan(p.y, p.x) + EffectTime * 0.55;
    float star = pow(abs(cos(ang * 3.0)), 14.0) * smoothstep(0.98, 0.05, r);
    float star2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 26.0) * smoothstep(0.92, 0.04, r);

    // \u2605\u2462 \u53ea\u4fdd\u7559\u4e00\u5708**\u6781\u7a84**\u7684\u5916\u8fb9\uff08\u2605r\u22480.72 \u2713\uff09
    float rim = smoothstep(0.64, 0.72, r) * smoothstep(0.82, 0.73, r);

    // \u2605\u2463 \u968f\u673a\u9038\u6563\u7684\u5c0f\u5149\u70b9 \u2713
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
        float dd = length(f - dir * (phase * 0.85));
        motes += smoothstep(0.15, 0.0, dd) * (1.0 - phase);
    }

    float a = clamp(core * breath + (star * 0.70 + star2 * 0.45 + rim * 0.35) * breath + motes * 0.60, 0.0, 1.0);
    a *= smoothstep(1.0, 0.85, r);
    if (a < 0.08) {
        discard;                                      // \u2605\u2605 \u6de1\u7684\u5730\u65b9**\u6839\u672c\u4e0d\u753b** \u2713
    }

    vec3 col = mix(vec3(0.78, 0.82, 0.90), vec3(1.0), clamp(star + star2, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
