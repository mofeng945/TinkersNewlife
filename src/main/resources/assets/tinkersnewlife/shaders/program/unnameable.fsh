#version 150

// 「不可名状」后处理：锐化（非锐化掩模）+ 拉高饱和度 + 拉高对比度 + 反转颜色。
// 采样器/属性名沿用原版 blit 顶点着色器（blit.vsh 里 Sampler0 就绑定到 DiffuseSampler）。

uniform sampler2D DiffuseSampler;

uniform float Saturation;
uniform float Contrast;
uniform float Sharpen;

in vec2 texCoord;

out vec4 fragColor;

void main(){
    ivec2 size = textureSize(DiffuseSampler, 0);
    vec2 texel = vec2(1.0 / max(float(size.x), 1.0), 1.0 / max(float(size.y), 1.0));

    // 3x3 的十字邻居 = 模糊；中心减模糊再补回去 = 锐化
    vec4 center = texture(DiffuseSampler, texCoord);
    vec4 up     = texture(DiffuseSampler, texCoord + vec2(0.0, texel.y));
    vec4 down   = texture(DiffuseSampler, texCoord - vec2(0.0, texel.y));
    vec4 left   = texture(DiffuseSampler, texCoord - vec2(texel.x, 0.0));
    vec4 right  = texture(DiffuseSampler, texCoord + vec2(texel.x, 0.0));

    vec3 blur = (up.rgb + down.rgb + left.rgb + right.rgb) * 0.25;
    vec3 color = center.rgb + (center.rgb - blur) * Sharpen;

    // 饱和度：向灰度插值（>1 更艳，<1 更灰）
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(vec3(luma), color, Saturation);

    // 对比度：围绕 0.5 拉伸
    color = (color - 0.5) * Contrast + 0.5;

    // 反转颜色
    color = 1.0 - color;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
