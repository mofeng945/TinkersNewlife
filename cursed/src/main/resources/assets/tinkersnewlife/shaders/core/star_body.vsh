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

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
    vColor = Color;
    vLight = UV1;
}
