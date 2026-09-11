#version 150

// 飞剑流光拖尾：
//   UV0.x = 沿拖尾方向的进度（0 = 剑身处/头，1 = 尾）
//   UV0.y = 横跨条带（0/1 为两侧边缘，纹理本身在两侧做柔和衰减）
//   TrailTime = 客户端秒级时间（由渲染类型 setup 时写入的 uniform）
// 效果 = 底纹 × 顶点色（飞剑自己的随机色相）× 多层正弦流光 × 头亮尾淡

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TrailTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a <= 0.001 || vertexColor.a <= 0.001) {
        discard;
    }

    float u = texCoord0.x;

    // ---- 多层正弦叠加的流光（沿拖尾向尾部流动）----
    float f1 = sin(u * 9.0 - TrailTime * 6.0) * 0.5 + 0.5;
    float f2 = sin(u * 21.0 - TrailTime * 11.0) * 0.5 + 0.5;
    float f3 = sin(u * 44.0 - TrailTime * 19.0) * 0.5 + 0.5;
    float flow = f1 * 0.55 + f2 * 0.30 + f3 * 0.15;
    flow = pow(flow, 2.4);                 // 收窄成"光带"而不是均匀呼吸

    // ---- 头部更亮、尾部收束（u=0 头部）----
    float head = 1.0 - u;
    float intensity = 1.0 + flow * 2.6 * (0.35 + head);

    vec3 rgb = tex.rgb * vertexColor.rgb * intensity;
    float alpha = tex.a * vertexColor.a * (1.0 - u * 0.5);

    fragColor = vec4(rgb, alpha) * ColorModulator;
}
