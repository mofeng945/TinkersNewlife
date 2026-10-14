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
    vec2 localUv = (SpriteRect.z > SpriteRect.x && SpriteRect.w > SpriteRect.y)
            ? (uv - SpriteRect.xy) / (SpriteRect.zw - SpriteRect.xy)
            : uv;
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
        // \u2605\u9ed1\u8272\u90e8\u5206 \u21d2 \u2605\u661f\u7a7a \u2713
        float neb = fbm(suv * 22.0 + vec2(EffectTime * 0.035, -EffectTime * 0.022));
        float neb2 = fbm(suv * 7.0 - vec2(EffectTime * 0.012, EffectTime * 0.02));
        // TNL954 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u661f\u661f\u5bc6\u5ea6\u4e0d\u591f" \u2713
        //   \u2605\u53c2\u8003\u65e0\u5c3d\u8d2a\u5a6a\uff1a\u2605\u5b83\u7528 ``cosmicoutof = 101``\uff08\u2605\u7ea6 1% \u7684\u50cf\u7d20\u624d\u662f\u661f \u2713\uff09
        //     \u002b \u2605\u4ece 10 \u5e27\u661f\u7a7a\u8d34\u56fe\u91cc\u53d6\u6837 \u2713\u3002
        //   \u2605\u6211\u4eec\u6ca1\u6709\u56fe\u96c6\u5e27 \u2717 \u21d2 \u2605\u628a\u9608\u503c\u538b\u4f4e\u002b\u9891\u7387\u63d0\u9ad8\u6765\u5bf9\u7b49 \u2713
        //     \uff08\u2605\u4ece\u201c\u53ea\u6709\u6700\u4eae\u7684 10%\u201d\u6539\u6210\u201c\u7ea6 38%\u201d\uff0c\u2605\u5c3a\u5ea6\u4e5f\u52a0\u5bc6 \u2713\uff09
        float stars = smoothstep(0.62, 1.0, noise(suv * 150.0 + vec2(EffectTime * 0.55, 0.0)));
        float stars2 = smoothstep(0.74, 1.0, noise(suv.yx * 210.0 + vec2(0.0, EffectTime * 0.42)));
        float bright = smoothstep(0.86, 1.0, noise(suv.yx * 70.0 - vec2(0.0, EffectTime * 0.3)));
        vec3 deep = vec3(0.02, 0.025, 0.06);
        vec3 cloud = vec3(0.35, 0.42, 0.80) * neb * 0.55 + vec3(0.55, 0.35, 0.75) * neb2 * 0.35;
        col = deep + cloud + vec3(1.0) * (stars * 0.95 + stars2 * 0.7) + vec3(0.9, 0.94, 1.0) * bright;   // TNL954 \u2605\u661f\u70b9\u52a0\u5bc6 \u2713
    } else {
        col = tex.rgb;
    }
    float light = max(max(vLight.x, vLight.y), 0.55);
    fragColor = vec4(col * light, tex.a) * vColor * ColorModulator;
}
