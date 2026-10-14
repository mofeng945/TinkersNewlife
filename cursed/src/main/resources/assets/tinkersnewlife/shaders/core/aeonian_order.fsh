#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float MaskThreshold;

in vec2 vUv;
in vec4 vColor;
in vec2 vLight;
in vec3 vView;

out vec4 fragColor;

// TNL1024 \u2605\u2605 \u6052\u6b63\u79e9\u5668\uff1a\u2605\u7eaf\u9ed1\u90e8\u5206\u5f53\u906e\u7f69 \u2713
//   \u2605\u906e\u7f69\u91cc\u753b \u2713\uff1a\u2605**\u7ffb\u6eda\u7684\u9ed1\u96fe** \u002b \u2605**\u4e0d\u65ad\u7728\u773c\u7684\u773c\u775b** \u2713\uff08\u2605\u900f\u955c\u611f \u2713\uff09
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float n00 = hash(i);
    float n10 = hash(i + vec2(1.0, 0.0));
    float n01 = hash(i + vec2(0.0, 1.0));
    float n11 = hash(i + vec2(1.0, 1.0));
    return mix(mix(n00, n10, f.x), mix(n01, n11, f.x), f.y);
}

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise(p);
        p *= 2.02;
        amp *= 0.5;
    }
    return sum;
}

void main() {
    vec4 tex = texture(Sampler0, vUv);
    if (tex.a < 0.08) {
        discard;
    }
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float light = max(max(vLight.x, vLight.y), 0.55);
    if (luma >= MaskThreshold) {
        fragColor = tex * vColor * ColorModulator * light;
        return;
    }

    // \u2605\u8fd9\u91cc\u5c31\u662f"\u900f\u955c"\uff1a\u2605\u7528\u89c6\u7a7a\u95f4\u65b9\u5411\u505a\u5750\u6807 \u2713
    vec2 uvp = vUv * 16.0;
    vec3 dir = normalize(vView + vec3(1.0e-4));
    vec2 flow = uvp + vec2(EffectTime * 0.35, -EffectTime * 0.22) + dir.xy * 2.0;

    // \u2605\u2460 \u7ffb\u6eda\u7684\u9ed1\u96fe\uff08\u2605\u4e24\u5c42\u4e0d\u540c\u65cb\u5411 \u2713\uff09
    float m1 = fbm(flow * 0.55);
    float m2 = fbm(flow * 1.10 + vec2(-EffectTime * 0.25, EffectTime * 0.18));
    float mist = clamp(m1 * 0.8 + m2 * 0.4, 0.0, 1.0);
    vec3 col = mix(vec3(0.01, 0.01, 0.02), vec3(0.10, 0.09, 0.13), mist);

    // \u2605\u2461 \u4e0d\u65ad\u7728\u773c\u7684\u773c\u775b\uff08\u2605\u683c\u70b9 \u002b \u2605\u968f\u673a\u7728\u773c\u5468\u671f \u2713\uff09
    vec2 g = flow * 1.6;
    vec2 cell = floor(g);
    vec2 fc = fract(g) - vec2(0.5);
    float h1 = hash(cell);
    if (h1 > 0.62) {                          // \u2605\u53ea\u6709\u4e00\u90e8\u5206\u683c\u5b50\u6709\u773c\u775b \u2713
        vec2 eye = vec2(0.0, 0.0);
        eye.x = (hash(cell + vec2(3.1, 7.7)) - 0.5) * 0.22;
        eye.y = (hash(cell + vec2(9.3, 1.7)) - 0.5) * 0.22;
        float d = length(fc - eye);
        // \u2605\u7728\u773c\uff1a\u2605\u6bcf\u4e2a\u773c\u775b\u81ea\u5df1\u7684\u5468\u671f \u2713
        float phase = hash(cell + vec2(5.5, 2.2)) * 6.2831;
        float blink = fract(EffectTime * 0.28 + hash(cell + vec2(1.1, 4.4)));
        float open = smoothstep(0.0, 0.12, blink) * smoothstep(0.30, 0.18, blink) + 0.15;
        open = clamp(open, 0.12, 1.0);
        float iris = smoothstep(0.20, 0.02, length((fc - eye) * vec2(1.0, 1.0 / max(open, 0.12))));
        float pupil = smoothstep(0.09, 0.0, length((fc - eye) * vec2(1.0, 1.0 / max(open, 0.12))));
        vec3 eyeCol = vec3(0.95, 0.93, 0.80) * iris - vec3(0.85, 0.85, 0.85) * pupil;
        col += eyeCol * step(d, 0.28) * 1.25;
    }

    // \u2605\u52a0\u4e00\u70b9\u6de1\u7d2b\u6c14\u6c1b \u2713
    col += vec3(0.06, 0.02, 0.10) * mist;
    // TNL1027 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6211\u8981\u8ba9\u624b\u6301\u9570\u5200\u6a21\u578b\u6709\u900f\u955c\u6548\u679c" \u2713
    //   \u2605\u4e0a\u6b21"\u624b\u6301\u53d8\u5757"\u7684\u771f\u56e0 \u2717\uff1a\u2605\u6211\u628a\u4e0d\u900f\u660e\u5ea6\u5199\u6210
    //     ``max(tex.a, 0.92)`` \u2717 \u21d2 \u2605\u8fde**\u900f\u660e\u533a\u4e5f\u88ab\u6d82\u6210\u4e0d\u900f\u660e** \u2717
    //     \u21d2 \u2605\u6574\u5757\u56db\u8fb9\u5f62\u90fd\u53d8\u6210\u4e00\u5757\u9ed1\u677f \u2713\u2713\uff08\u2605\u548c\u59ff\u6001\u65e0\u5173 \u2717\uff09
    //   \u2605\u73b0\u5728 \u2713\uff1a\u2605alpha \u53ea\u7528\u8d34\u56fe\u81ea\u5df1\u7684 \u2713 \u21d2 \u2605\u8be5\u900f\u7684\u5730\u65b9\u4fdd\u6301\u900f\u660e \u2713
    fragColor = vec4(col * light, tex.a) * vColor * ColorModulator;
}
