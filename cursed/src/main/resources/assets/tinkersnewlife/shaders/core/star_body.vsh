#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec2 UV1;
in vec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 vUv;
out vec4 vColor;
out vec2 vLight;
out vec3 vView;
out vec2 vLocalUv;   // TNL968                    // TNL965 \u2605\u89c6\u56fe\u7a7a\u95f4\u4f4d\u7f6e\uff08\u2605\u900f\u955c\u951a\u70b9 \u2713\uff09

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * view;
    vUv = UV0;
    vColor = Color;
    vLight = UV2;
    vView = view.xyz;
    vLocalUv = UV1 / 65535.0;   // TNL968              // \u2605\u2605 \u7528\u6237\u8981\u7684"\u661f\u7a7a\u900f\u955c"\uff1a\u2605\u661f\u7a7a\u8ddf\u7740\u89c6\u89d2\u8d70 \u2713
}
