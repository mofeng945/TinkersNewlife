#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1005 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5176\u5b9e\u53ef\u4ee5\u4e0d\u8981\u5916\u5708\u767d\u8272\u586b\u6ee1\uff0c\u53ea\u8981\u5185\u90e8\u90a3\u4e2a\u65cb\u8f6c\u5149\u6655
//   \u548c\u547c\u5438\u7684\u653e\u5927\u7f29\u5c0f\u6548\u679c\u4fdd\u7559\u5c31\u884c" \u2713
//   \u2605\u53bb\u6389 \u2717\uff1a\u2605"\u5185\u9ed1\u2192\u4e2d\u7070\u2192\u5916\u767d"\u90a3\u5c42**\u94fa\u6ee1\u683c\u5b50\u7684\u586b\u5145** \u2713
//   \u2605\u4fdd\u7559 \u2713\uff1a\u2605\u65cb\u8f6c\u661f\u8292 \u002b \u2605\u67d4\u5149\u6838\u5fc3 \u002b \u2605\u9038\u6563\u5149\u70b9 \u002b \u2605**\u547c\u5438\u7684\u653e\u5927\u7f29\u5c0f** \u2713
//   \u2605\u73b0\u5728\uff1a\u2605\u6574\u4f53\u5750\u6807\u5148\u6309\u547c\u5438**\u7f29\u653e** \u2713 \u21d2 \u2605\u770b\u8d77\u6765\u5c31\u662f"\u4e00\u6536\u4e00\u653e" \u2713
void main() {
    // \u2605\u547c\u5438\uff1a\u2605\u660e\u6697 \u002b \u2605\u5c3a\u5bf8 \u4e00\u8d77\u547c\u5438 \u2713
    float breathWave = sin(EffectTime * 1.6);
    float breath = 0.78 + 0.22 * breathWave;
    float zoom = 1.0 + 0.12 * breathWave;              // \u2605\u2605 \u653e\u5927\u7f29\u5c0f \u2713

    vec2 p = (vUv - vec2(0.5)) / zoom;                 // \u2605\u5148\u7f29\u653e\u5750\u6807 \u2713
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u2460 \u67d4\u5149\u6838\u5fc3\uff08\u2605\u53ea\u5728\u4e2d\u95f4 \u2713\uff0c\u2605\u4e0d\u94fa\u6ee1 \u2717\uff09
    float core = smoothstep(0.80, 0.0, r);
    core *= core;

    // \u2605\u2461 \u65cb\u8f6c\u7684\u516d\u89d2\u661f\u8292 \u2713
    float ang = atan(p.y, p.x) + EffectTime * 0.55;
    float star = pow(abs(cos(ang * 3.0)), 9.0) * smoothstep(0.95, 0.05, r);
    float star2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 18.0) * smoothstep(0.90, 0.04, r);

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
        float dd = length(f - dir * (phase * 0.85));
        motes += smoothstep(0.15, 0.0, dd) * (1.0 - phase);
    }

    // \u2605\u5408\u6210\uff08\u2605\u53ea\u6709\u4e2d\u5fc3\u90a3\u56e2 \u2713\uff0c\u2605\u56db\u8fb9\u5f52\u96f6 \u2713\uff09
    float a = clamp(core * 0.30 * breath + (star * 0.55 + star2 * 0.35) * breath + motes * 0.50, 0.0, 1.0);
    a *= smoothstep(1.0, 0.62, r);

    // \u2605\u7070\u767d\u8272\u8c03\uff08\u2605\u661f\u8292/\u5149\u70b9\u504f\u767d \u2713\uff09
    vec3 col = vec3(0.82, 0.85, 0.91);
    col += vec3(1.0) * (star * 0.35 + star2 * 0.25 + motes * 0.30);

    // TNL1006 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5916\u9762\u80cc\u666f\u8fd9\u4e2a\u65b9\u5f62\u767d\u5e95\u7ed9\u6211\u5f04\u6389\u554a\uff0c\u4e00\u76f4\u5728\u5c31\u6ca1\u6d88\u5931\u8fc7" \u2713
    //   \u2605\u65b0\u624b\u6bb5 \u2713\uff1a\u2605\u4e0d\u53ea\u628a alpha \u538b\u4f4e \u2717 \u2014\u2014 \u2605**\u4f4e\u4e8e\u9608\u503c\u76f4\u63a5 discard** \u2713
    //     \u21d2 \u2605\u54ea\u6015\u56db\u8fb9\u5f62\u94fa\u6ee1\u683c\u5b50\uff0c\u2605\u4e5f**\u4e0d\u53ef\u80fd\u518d\u51fa\u73b0\u4e00\u5757\u767d\u677f** \u2713\u2713
    float alpha = a * vColor.a * ColorModulator.a;
    if (alpha < 0.035) {
        discard;
    }
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, alpha);
}
