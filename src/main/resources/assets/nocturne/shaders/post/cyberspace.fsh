#version 330
uniform sampler2D InSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 source = texture(InSampler, texCoord);
    vec3 rgb = source.rgb * vec3(0.93, 1.025, 1.09);
    float luma = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
    rgb = mix(vec3(luma), rgb, 1.12);
    rgb = (rgb - 0.5) * 1.16 + 0.5;
    fragColor = vec4(clamp(rgb, 0.0, 1.0), source.a);
}
