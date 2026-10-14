#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1008 \u2605\u2605 \u5bf9\u7167\u5b9e\u9a8c\u7ed3\u8bba\uff1a\u2605\u7528\u6237\u5173\u6389\u8fd9\u5c42\u540e\u767d\u5e95**\u6d88\u5931** \u2713
//   \u21d2 \u2605\u767d\u5e95**\u5c31\u662f\u6211\u4eec\u81ea\u5df1\u753b\u7684** \u2713\u2713
//   \u2605\u539f\u56e0 \u2717\uff1a\u2605\u539f\u6765\u7684\u201c\u4e2d\u5fc3\u67d4\u5149\u201d\u8870\u51cf\u592a\u5bbd \u2717
//     \uff08\u2605``smoothstep(0.80, 0.0, r)`` \u5728 r\u22480.4 \u5904\u8fd8\u6709\u53ef\u89c1\u5ea6 \u2717\uff09
//     \u21d2 \u2605\u94fa\u5f00\u5c31\u662f**\u4e00\u5c42\u67d4\u548c\u7684\u65b9\u677f** \u2713
//   \u2605\u73b0\u5728 \u2713\uff1a\u2605**\u53ea\u4fdd\u7559\u4e00\u6761\u7ec6\u73af\u5e26** \u2713\uff08\u2605\u5916\u5708\u767d\u8272\u5728\u73af\u5e26\u4e0a \u2713\uff09
//     \u002b \u2605\u65cb\u8f6c\u661f\u8292 \u002b \u2605\u9038\u6563\u5149\u70b9 \u002b \u2605\u547c\u5438\u7f29\u653e \u2713
//     \u002b \u2605**alpha \u4f4e\u4e8e\u9608\u503c\u5c31 discard** \u2713 \u21d2 \u2605\u4e0d\u53ef\u80fd\u518d\u6210\u5757 \u2713\u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.78 + 0.22 * wave;
    float zoom = 1.0 + 0.12 * wave;                 // \u2605\u547c\u5438\u7f29\u653e \u2713

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u53ea\u6709\u4e00\u6761\u7ec6\u73af\u5e26\uff08\u2605\u5185\u5916\u90fd\u5f52\u96f6 \u2713\uff09
    float ring = smoothstep(0.30, 0.52, r) * smoothstep(0.86, 0.62, r);
    ring *= ring;

    // \u2605\u65cb\u8f6c\u661f\u8292\uff08\u2605\u4ece\u4e2d\u5fc3\u5c04\u51fa \u2713\uff09
    float ang = atan(p.y, p.x) + EffectTime * 0.55;
    float star = pow(abs(cos(ang * 3.0)), 9.0) * smoothstep(0.90, 0.06, r) * (1.0 - smoothstep(0.86, 1.0, r));
    float star2 = pow(abs(cos(ang * 3.0 + 1.5707963)), 18.0) * smoothstep(0.86, 0.05, r) * (1.0 - smoothstep(0.82, 1.0, r));

    // \u2605\u968f\u673a\u9038\u6563\u7684\u5c0f\u5149\u70b9 \u2713
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

    float a = clamp(ring * 0.55 * breath + (star * 0.60 + star2 * 0.40) * breath + motes * 0.55, 0.0, 1.0);
    a *= smoothstep(1.0, 0.80, r);
    if (a < 0.06) {
        discard;                                   // \u2605\u2605 \u5173\u952e\uff1a\u4e0d\u753b\u4f4e\u900f\u660e\u5ea6\u7684\u201c\u9762\u201d \u2713
    }

    vec3 col = vec3(0.80, 0.84, 0.90);
    col = mix(col, vec3(1.0), clamp(star + star2, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
