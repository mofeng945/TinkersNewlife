#version 150

// TNL940 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u7684**\u9ed1\u5f13\u8eab\u661f\u7a7a** \u2713
//   \u2605\u53c2\u8003\uff1a\u2605\u8be1\u5384\u9057\u7269\u7684\u4e24\u9897\u77ff\u77f3\uff08\u2605``nebula_anomaly_item`` / ``mirror_anomaly_item`` \u2713\uff09
//   \u2605\u505a\u6cd5\uff1a\u2605\u7528**fbm \u566a\u58f0**\u7b97\u51fa\u661f\u4e91\u4e0e\u661f\u70b9 \u2713\uff0c
//     \u2605\u5e76\u7528**\u8d34\u56fe\u4eae\u5ea6\u505a\u906e\u7f69** \u2713 \u2014\u2014
//     \u2605\u53ea\u6709"\u9ed1\u8272\u90e8\u5206"\u4f1a\u53d8\u661f\u7a7a \u2713\uff0c\u5176\u4f59\u50cf\u7d20\u539f\u6837\u8f93\u51fa \u2713
uniform sampler2D Sampler0;
uniform float EffectTime;
uniform float MaskThreshold;
uniform vec4 ColorModulator;
uniform vec2 UvOffset;      // TNL960 \u2605\u56fe\u96c6 UV \u2192 \u8d34\u56fe\u672c\u5730 UV \u2713
uniform vec2 UvScale;
uniform vec4 SpriteRect;   // TNL971 \u2605sprite \u5728\u56fe\u96c6\u91cc\u7684 UV \u77e9\u5f62 \u2713
uniform float CamYaw;
uniform float CamPitch;

in vec2 vUv;
in vec4 vColor;
in vec2 vLight;
in vec3 vView;         // TNL965 \u2605\u89c6\u56fe\u7a7a\u95f4\u4f4d\u7f6e \u2713

out vec4 fragColor;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float noise(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float fbm(vec2 x) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise(x);
        x = x * 2.03 + vec2(1.7, 9.2);
        amp *= 0.5;
    }
    return v;
}

void main() {
    // TNL960 \u2605\u2605 \u5173\u952e\uff1a\u2605\u7269\u54c1\u56db\u8fb9\u5f62\u7684 UV \u662f**\u56fe\u96c6\u7a7a\u95f4** \u2713
    //   \u2605\u76f4\u63a5\u62ff\u5b83\u53bb\u91c7\u6211\u4eec\u81ea\u5df1\u7684 16\u00d716 \u8d34\u56fe \u21d2 \u2605\u91c7\u5230\u4e71\u7801\u7eb9\u7d20 \u2717
    //   \u21d2 \u2605\u6574\u5757\u90fd\u7b97"\u9ed1" \u21d2 \u2605\u6574\u5757\u661f\u7a7a \u2713\uff08\u2605\u7528\u6237\u5b9e\u6d4b\uff1a\u53c8\u53d8\u6210\u5757 \u2713\uff09
    //   \u2605\u73b0\u5728\uff1a\u2605\u5148\u6362\u7b97\u6210\u8d34\u56fe\u672c\u5730 0~1 \u2713\uff08\u2605\u548c\u539f\u7248 sprite \u4e00\u6837 \u2713\uff09
    // TNL963 \u2605\u2605 \u6539\u6210\u2605**Java \u4fa7\u5c31\u628a UV \u6362\u7b97\u597d** \u2713
    //   \u2605\u539f\u56e0 \u2717\uff1a\u2605uniform \u65b9\u6848\u4e00\u76f4\u6ca1\u5582\u8fdb\u53bb \u2717
    //   \uff08\u2605\u9ed8\u8ba4 0 \u21d2 \u5168\u900f\u660e \u2713\uff1b\u2605\u9ed8\u8ba4 1 \u21d2 \u56fe\u96c6 UV \u21d2 \u6574\u5757 \u2713\uff09
    //   \u2605\u73b0\u5728\uff1a\u2605\u9876\u70b9\u91cc\u5199\u7684\u5c31\u662f\u672c\u5730 UV \u2713 \u21d2 \u2605\u76f4\u63a5\u7528 vUv \u2713
    // TNL965 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u60f3\u5b9e\u73b0\u539f\u7248\u661f\u7a7a\u900f\u955c\u7684\u6548\u679c\uff0c\u800c\u4e0d\u662f\u76ee\u524d\u56fa\u5b9a\u7684\u661f\u7a7a" \u2713
    //   \u2605\u539f\u7248\u672b\u5730\u4f20\u9001\u95e8\uff08\u2605rendertype_end_portal \u2713\uff09\u7684\u661f\u7a7a**\u951a\u5728\u4e16\u754c/\u89c6\u56fe\u7a7a\u95f4** \u2713
    //     \u21d2 \u2605\u8f6c\u5934\u3001\u8d70\u52a8\u90fd\u4f1a\u6709**\u89c6\u5dee** \u2713 \u2014\u2014 \u2605\u770b\u8d77\u6765\u5c31\u50cf"\u900f\u955c\u91cc\u770b\u5230\u53e6\u4e00\u7247\u661f\u7a7a" \u2713
    //   \u2605\u73b0\u5728\uff1a\u2605\u7528\u9876\u70b9\u7684\u89c6\u56fe\u7a7a\u95f4\u65b9\u5411\u7b97\u661f\u661f \u2713\uff08\u2605\u4e0d\u9700\u8981\u9006\u77e9\u9635 \u2713\uff09
    // TNL971 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u76f4\u63a5\u5f80\u4e0b\u505a" \u2713
    //   \u2605\u5f62\u72b6\u9760**\u5c40\u90e8 UV**\uff08\u2605\u4ece SpriteRect \u7b97\uff09\u2713
    //   \u2605\u955c\u5934\u611f\u9760**\u76f8\u673a\u671d\u5411\u5e73\u79fb** \u2713 \u2014\u2014 \u2605\u4e24\u8005\u5206\u79bb \u2713
    vec2 uv = vUv;
    // TNL974 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u8fd8\u662f\u6ca1\u6709" \u2713
    //   \u2605\u65e5\u5fd7\u8bc1\u636e\uff1a\u2605``TNL971 sprite rect`` **\u4e00\u884c\u90fd\u6ca1\u6709** \u2717
    //     \u21d2 \u2605SpriteRect \u4e00\u76f4\u662f\u9ed8\u8ba4 0 \u2717 \u21d2 \u2605\u8d70\u515c\u5e95 ``= uv``\uff08\u2605\u56fe\u96c6 UV \u2713\uff09
    //     \u21d2 \u2605\u8303\u56f4\u53ea\u6709 16/2048 \u2248 0.008 \u2717 \u21d2 \u2605\u6574\u4e2a\u9762\u677f**\u53ea\u843d\u5728\u4e00\u4e2a\u683c\u5b50** \u2717
    //     \u21d2 \u2605\u90a3\u4e00\u683c\u6ca1\u547d\u4e2d\u5c31\u662f"\u4e00\u9897\u661f\u90fd\u6ca1\u6709" \u2713\u2713
    //   \u2605\u73b0\u5728\uff1a\u2605\u515c\u5e95\u6539\u6210**\u628a\u56fe\u96c6 UV \u653e\u5927 128 \u500d** \u2713
    //     \uff08\u2605 16px/2048 \u00d7 128 \u2248 1.0 \u2713 \u21d2 \u2605\u4e00\u5f20\u8d34\u56fe\u6b63\u597d\u94fa\u6ee1 0~1 \u2713\uff09
    //     \u21d2 \u2605\u4e0d\u4f9d\u8d56 uniform \u4e5f\u80fd\u51fa\u661f \u2713\uff08\u2605uniform \u6709\u5c31\u7528\u66f4\u51c6 \u2713\uff09
    vec2 localUv = (SpriteRect.z > SpriteRect.x && SpriteRect.w > SpriteRect.y)
            ? (uv - SpriteRect.xy) / (SpriteRect.zw - SpriteRect.xy)
            : fract(uv * 128.0);
    float cy = cos(radians(CamYaw));
    float sy = sin(radians(CamYaw));
    float cp = cos(radians(CamPitch));
    float sp = sin(radians(CamPitch));                       // \u2605\u4ecd\u7528\u4e8e\u53d6\u906e\u7f69\uff08\u2605\u8d34\u56fe\u4eae\u5ea6\uff09\u2713
    vec2 suv = localUv + vec2(sy * cp, -sp) * 0.45;   // TNL971 \u2605\u5c40\u90e8 UV \u002b \u76f8\u673a\u5e73\u79fb \u2713
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < 0.08) {
        discard;
    }
    float luma = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    vec3 col;
    if (luma < MaskThreshold) {
        // TNL1034 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u5f13\u90fd\u5df2\u7ecf\u5b8c\u7f8e\u5b9e\u73b0\u8fc7\u4e00\u7248\u4e86\u4f60\u600e\u4e48\u8fd8\u53cd\u590d\u8d70\u4e4b\u524d\u8bd5\u9519\u8fc7\u7684\u8def" \u2713
        //   \u2605\u505a\u6cd5 \u2713\uff1a\u2605**\u628a\u5f13\u7684 fsh \u539f\u6587\u7167\u642c** \u2713\uff0c\u2605\u53ea\u6362\u6389"\u906e\u7f69\u91cc\u753b\u4ec0\u4e48"\u8fd9\u4e00\u6bb5 \u2713
        //     \u2605\u906e\u7f69\u5224\u5b9a/\u2605\u8f93\u51fa/\u2605alpha/\u2605\u5750\u6807\u6765\u6e90**\u5168\u90e8\u4e0e\u5f13\u4e00\u81f4** \u2713\u2713
        vec2 uvp = suv * 16.0;
        vec3 dir = normalize(vView + vec3(1.0e-4));
        vec2 flow = uvp + vec2(EffectTime * 0.35, -EffectTime * 0.22) + dir.xy * 2.0;
        float m1 = fbm(flow * 0.55);
        float m2 = fbm(flow * 1.10 + vec2(-EffectTime * 0.25, EffectTime * 0.18));
        float mist = clamp(m1 * 0.8 + m2 * 0.4, 0.0, 1.0);
        vec3 col = mix(vec3(0.01, 0.01, 0.02), vec3(0.11, 0.10, 0.14), mist);
        vec2 g = flow * 1.6;
        vec2 cell = floor(g);
        vec2 fc = fract(g) - vec2(0.5);
        float h1 = hash(cell);
        if (h1 > 0.62) {
            vec2 eye = vec2((hash(cell + vec2(3.1, 7.7)) - 0.5) * 0.22,
                            (hash(cell + vec2(9.3, 1.7)) - 0.5) * 0.22);
            float blink = fract(EffectTime * 0.28 + hash(cell + vec2(1.1, 4.4)));
            float open = clamp(smoothstep(0.0, 0.12, blink) * smoothstep(0.30, 0.18, blink) + 0.15, 0.12, 1.0);
            vec2 sq = (fc - eye) * vec2(1.0, 1.0 / open);
            float iris = smoothstep(0.20, 0.02, length(sq));
            float pupil = smoothstep(0.09, 0.0, length(sq));
            col += (vec3(0.95, 0.93, 0.80) * iris - vec3(0.85) * pupil) * 1.25;
        }
        col += vec3(0.06, 0.02, 0.10) * mist;
    }
    float light = max(max(vLight.x, vLight.y), 0.55);
    fragColor = vec4(col * light, tex.a) * vColor * ColorModulator;
}
