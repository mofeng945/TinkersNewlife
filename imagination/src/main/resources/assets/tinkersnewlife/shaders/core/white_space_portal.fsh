#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 vUv;
in vec4 vColor;

out vec4 fragColor;

// TNL999 \u2605\u2605 \u767d\u8272\u7a7a\u95f4\u4f20\u9001\u95e8\uff1a\u2605\u628a"\u5bf9\u9762\u4e16\u754c\u7684\u753b\u9762"\u8d34\u5728\u95e8\u9762\u4e0a \u2713
//   \u2605Sampler0 = \u6211\u4eec\u81ea\u5df1\u7ed1\u7684\u526f\u5e27\u7f13\u51b2\u989c\u8272\u9644\u4ef6 \u2713\uff08\u2605\u4e0d\u662f\u56fe\u96c6 \u2717\uff09
//   \u2605UV \u8d85\u51fa 0~1 \u5c31 discard \u2713 \u21d2 \u2605\u753b\u4e0d\u51fa\u95e8\u5916 \u2713\uff08\u2605\u6700\u7b80\u7248\u88c1\u526a \u2713\uff09
void main() {
    if (vUv.x < 0.0 || vUv.x > 1.0 || vUv.y < 0.0 || vUv.y > 1.0) {
        discard;
    }
    vec4 tex = texture(Sampler0, vUv);
    fragColor = vec4(tex.rgb, 1.0) * vColor * ColorModulator;
}
