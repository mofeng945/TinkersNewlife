#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1003 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u4f60\u6ca1\u53bb\u6389\u4e4b\u524d\u7684\u767d\u8272\u80cc\u666f\uff0c\u65b0\u52a0\u7684\u5149\u6548\u770b\u4e0d\u89c1\u4e86\uff0c
//   \u800c\u4e14\u6211\u8981\u5185\u9ed1\u4e2d\u7070\u5916\u767d\u7684\u6e10\u53d8\uff0c\u800c\u4e14\u4f34\u968f\u547c\u5438\u611f" \u2713
//   \u2605\u65e7\u7248\u7684\u6bdb\u75c5 \u2717\uff1a\u2605``glow *= glow`` \u540e\u6574\u7247\u90fd\u662f\u4eae\u7684 \u2717
//     \u002b \u2605\u4e0d\u900f\u660e\u5ea6\u4e0a\u9650 0.55 \u21d2 \u2605**\u770b\u8d77\u6765\u5c31\u662f\u4e00\u4e2a\u767d\u65b9\u5757\u80cc\u666f** \u2713\u2713
//   \u2605\u73b0\u5728 \u2713\uff1a\u2605\u5185\u9ed1 \u2192 \u2605\u4e2d\u7070 \u2192 \u2605\u5916\u767d\u7684\u534a\u5f84\u6e10\u53d8 \u2713
//     \u002b \u2605\u4e2d\u5fc3**\u51e0\u4e4e\u900f\u660e**\uff08\u2605\u4e0d\u906e\u7269\u54c1 \u2713\uff09\u002b \u2605\u4e2d\u73af\u6700\u660e\u663e \u2713
//     \u002b \u2605\u661f\u8292 \u002b \u2605\u9038\u6563\u5149\u70b9 \u002b \u2605\u547c\u5438 \u2713
void main() {
    vec2 p = vUv - vec2(0.5);
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u547c\u5438\uff08\u2605\u6574\u4f53\u660e\u6697 \u002b \u2605\u6700\u4eae\u5904\u4e5f\u5728\u547c\u5438 \u2713\uff09
    float breath = 0.70 + 0.30 * sin(EffectTime * 1.6);

    // \u2605\u2460 \u5185\u9ed1 \u2192 \u4e2d\u7070 \u2192 \u5916\u767d \u2713
    vec3 grad = mix(vec3(0.02, 0.02, 0.03), vec3(0.58, 0.60, 0.64), smoothstep(0.0, 0.55, r));
    grad = mix(grad, vec3(1.0, 1.0, 1.0), smoothstep(0.55, 1.0, r));

    // \u2605\u4e0d\u900f\u660e\u5ea6\uff1a\u2605\u4e2d\u5fc3\u51e0\u4e4e\u900f\u660e \u2713\uff0c\u2605\u4e2d\u5916\u73af\u624d\u663e\u73b0 \u2713
    float ring = smoothstep(0.20, 0.62, r) * smoothstep(1.0, 0.72, r);
    float haze = 0.06 + 0.34 * ring;

    // \u2605\u2461 \u65cb\u8f6c\u7684\u516d\u89d2\u661f\u8292 \u2713
    float ang = atan(p.y, p.x) + EffectTime * 0.55;
    float star = pow(abs(cos(ang * 3.0)), 9.0) * smoothstep(1.0, 0.08, r);
    float star2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 18.0) * smoothstep(0.95, 0.05, r);

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

    float a = clamp(haze * breath + (star * 0.62 + star2 * 0.40) * breath + motes * 0.55, 0.0, 1.0);
    // TNL1004 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6548\u679c\u53ef\u4ee5\u4e86\u4f46\u662f\u4e3a\u4ec0\u4e48\u767d\u8272\u65b9\u5757\u80cc\u666f\u6ca1\u53bb\u6389" \u2713
    //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u6211\u7684\u56db\u8fb9\u5f62\u94fa\u6ee1\u6574\u4e2a\u683c\u5b50 \u2717
    //     \u002b \u2605"\u5916\u767d"\u90a3\u4e00\u6bb5**\u5230\u4e86\u56db\u8fb9\u5f62\u8fb9\u7f18\u8fd8\u662f\u767d\u7684** \u2717 \u21d2 \u2605\u770b\u8d77\u6765\u5c31\u662f\u4e00\u5757\u767d\u677f \u2713\u2713
    //   \u2605\u73b0\u5728\uff1a\u2605\u8ba9\u4e0d\u900f\u660e\u5ea6\u5728\u63a5\u8fd1\u8fb9\u7f18\u65f6**\u5f52\u96f6** \u2713\uff08\u2605\u5916\u5708\u767d\u8272\u4f9d\u7136\u5728 \u2713\uff0c\u2605\u4f46\u5230\u8fb9\u5c31\u6de1\u51fa \u2713\uff09
    a *= smoothstep(1.0, 0.72, r);
    vec3 col = grad;
    col += vec3(1.0) * (star * 0.35 + motes * 0.30);      // \u2605\u661f\u8292/\u5149\u70b9\u504f\u767d \u2713
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
