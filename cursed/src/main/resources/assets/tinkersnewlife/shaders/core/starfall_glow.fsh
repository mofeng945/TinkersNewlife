#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1001 \u2605\u2605 \u661f\u9668\u60b2\u53f9\uff1a\u2605\u7269\u54c1\u680f\u80cc\u666f\u7070\u767d\u5149\u6548 \u2713
//   \u2605\u53c2\u7167\u65e0\u5c3d\u8d2a\u5a6a\uff08\u2605Avaritia \u2713\uff09\uff1a\u2605\u5b83\u7684\u661f\u7a7a\u7269\u54c1\u5728**\u80cc\u540e**\u591a\u753b\u4e00\u5c42
//   \u2605\u4f1a\u547c\u5438\u7684\u5149\u6655 \u2713\uff08\u2605\u800c\u4e0d\u662f\u628a\u7269\u54c1\u672c\u8eab\u63cf\u8fb9 \u2717\uff09\u3002
//   \u2605\u8fd9\u91cc\u7528**\u7a0b\u5e8f\u5316\u5f84\u5411\u6e10\u53d8**\u505a \u2713\uff08\u2605\u4e0d\u9700\u8981\u65b0\u8d34\u56fe \u2713\uff09\u3002
void main() {
    vec2 p = vUv - vec2(0.5);
    float d = length(p) * 2.0;
    // \u2605\u4e2d\u5fc3\u4eae\u3001\u8fb9\u7f18\u6de1\u7684\u7070\u767d\u5149\u6655 \u2713
    float glow = smoothstep(1.0, 0.05, d);
    glow *= glow;
    // \u2605\u547c\u5438\uff08\u2605\u4e0d\u5feb\u4e0d\u6162 \u2713\uff09
    float breath = 0.72 + 0.28 * sin(EffectTime * 1.6);
    // \u2605\u5e26\u4e00\u70b9"\u2605\u51b7\u7070\u767d"\u7684\u5c42\u6b21 \u2713
    vec3 col = mix(vec3(0.86, 0.88, 0.92), vec3(1.0), glow);
    float a = glow * 0.55 * breath;
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
