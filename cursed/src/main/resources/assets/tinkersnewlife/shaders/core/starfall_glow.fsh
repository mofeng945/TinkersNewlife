#version 150

uniform vec4 ColorModulator;
uniform float EffectTime;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL1014 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u51cf\u5c11\u65cb\u81c2\u6570\u91cf\u4f46\u662f\u52a0\u5927\u5bbd\u5ea6\uff0c\u7136\u540e\u8ba9\u65cb\u81c2\u9876\u5230\u683c\u5b50\u8fb9\u7f18
//   \u7136\u540e\u5728\u683c\u5b50\u5916\u6e10\u6e10\u6de1\u5316\uff0c\u505a\u51fa\u53d1\u5149\u65cb\u8f6c\u7684\u6548\u679c" \u2713
//   \u2605\u505a\u6cd5 \u2713\uff1a
//     \u2460 \u2605\u56db\u8fb9\u5f62\u653e\u5927\u5230 **1.35 \u683c** \u2713 \u21d2 \u2605\u683c\u5b50\u8fb9\u7f18\u5bf9\u5e94 r \u2248 1/1.35 \u2248 0.74 \u2713
//     \u2461 \u2605\u81c2\u6570\u51cf\u5c11 \u2713\uff1a\u2605``cos(ang*2.0)`` \u21d2 **4 \u9053\u4e3b\u81c2** \u2713\uff08\u2605\u539f\u6765 6\u002b6 \u2713\uff09
//     \u2462 \u2605\u5bbd\u5ea6\u52a0\u5927 \u2713\uff1a\u2605\u6b21\u5e42\u4ece 16 \u964d\u5230 **4** \u2713\uff08\u2605\u8d8a\u4f4e\u8d8a\u5bbd \u2713\uff09
//     \u2463 \u2605\u683c\u5185\u4e0d\u8870\u51cf \u2713\uff0c\u2605\u8d8a\u8fc7 r=0.74 \u5f00\u59cb\u6de1\u51fa \u2713\uff0c\u2605\u5230 r=1.0 \u5f52\u96f6 \u2713
void main() {
    float wave = sin(EffectTime * 1.6);
    float breath = 0.82 + 0.18 * wave;
    float zoom = 1.0 + 0.10 * wave;

    vec2 p = (vUv - vec2(0.5)) / zoom;
    float r = clamp(length(p) * 2.0, 0.0, 1.0);

    // \u2605\u989c\u8272\u6e10\u53d8\uff08\u2605\u4ec5\u4f5c\u989c\u8272 \u2713\uff09\uff1a\u2605\u5185\u9ed1 \u2192 \u2605\u683c\u8fb9\u5904\u8fd1\u767d \u2192 \u2605\u683c\u5916\u7eaf\u767d \u2713
    vec3 grad = mix(vec3(0.04, 0.04, 0.06), vec3(0.62, 0.65, 0.72), smoothstep(0.0, 0.50, r));
    grad = mix(grad, vec3(1.0), smoothstep(0.50, 0.80, r));

    // \u2605\u4e2d\u5fc3\u67d4\u5149\uff08\u2605\u4fdd\u6301\u5c0f \u2713\uff09
    float core = smoothstep(0.18, 0.0, r);
    core *= core;

    // \u2605\u2461\u2462 4 \u9053**\u5bbd**\u81c2 \u2713
    float ang = atan(p.y, p.x) + EffectTime * 0.50;
    // \u2605\u683c\u5185\u5168\u5f3a \u2713\uff0c\u2605\u8d8a\u8fc7\u683c\u8fb9\u6de1\u51fa \u2713
    float fade = 1.0 - smoothstep(0.74, 1.00, r);
    float arm = pow(abs(cos(ang * 2.0)), 9.0) * fade;           // TNL1015 \u2605 4 \u9053\u4e3b\u81c2\uff08\u2605\u6b21\u5e42 4\u21929 \u21d2 \u2605\u4e0d\u518d\u7c98\u8fde \u2713\uff09
    float arm2 = pow(abs(cos(ang * 2.0 + 1.5707963)), 16.0) * fade * 0.55;   // \u2605\u56db\u9053\u526f\u81c2 \u2713

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

    // \u2605\u53ef\u89c1\u5ea6\uff1a\u2605\u53ea\u6765\u81ea\u81c2\uff08\u2605\u542b\u683c\u5916\u6de1\u51fa \u2713\uff09/\u6838\u5fc3/\u5149\u70b9 \u2713
    float a = clamp(core * 0.70 * breath + (arm * 0.66 + arm2 * 0.34) * breath + motes * 0.45, 0.0, 1.0);
    if (a < 0.05) {
        discard;
    }

    vec3 col = mix(grad, vec3(1.0), clamp(core * 2.2, 0.0, 1.0));
    fragColor = vec4(col * vColor.rgb * ColorModulator.rgb, a * vColor.a * ColorModulator.a);
}
