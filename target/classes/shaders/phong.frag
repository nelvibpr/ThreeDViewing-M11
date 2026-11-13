#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoords;
in vec4 FragPosLightSpace;

uniform vec3 lightPos;
uniform vec3 viewPos;
uniform vec3 lightColor;
uniform vec3 objectColor;
uniform sampler2D textureSampler;
uniform sampler2D shadowMap;

uniform bool  useTexture;
uniform bool  useBlinnPhong;
uniform bool  useShadow;
uniform bool  useBias;
uniform bool  unlit;        // untuk penanda posisi cahaya
uniform bool  grayscale;    // dipakai pada mode stereo anaglyph
uniform float specularStrength;
uniform float shininess;

// Shadow Mapping: 1.0 = dalam bayangan, 0.0 = terkena cahaya (PCF 3x3)
float calcShadow(vec4 fragPosLight, vec3 N, vec3 L) {
    vec3 proj = fragPosLight.xyz / fragPosLight.w;   // perspective divide
    proj = proj * 0.5 + 0.5;                         // NDC [-1,1] -> [0,1]
    if (proj.z > 1.0) return 0.0;                    // di luar far plane cahaya

    // Slope-scaled bias (skala disesuaikan dengan rentang depth ortografik cahaya)
    float bias = useBias ? max(0.005 * (1.0 - dot(N, L)), 0.0005) : 0.0;

    float shadow = 0.0;
    vec2 texel = 1.0 / vec2(textureSize(shadowMap, 0));
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            float dShadow = texture(shadowMap, proj.xy + vec2(x, y) * texel).r;
            shadow += (proj.z - bias > dShadow) ? 1.0 : 0.0;
        }
    }
    return shadow / 9.0;
}

void main() {
    vec3 baseColor = useTexture ? texture(textureSampler, TexCoords).rgb : objectColor;
    vec3 result;

    if (unlit) {
        result = objectColor;
    } else {
        // 1. Ambient
        vec3 ambient = 0.2 * lightColor;

        // 2. Diffuse (Lambertian)
        vec3 norm = normalize(Normal);
        vec3 lightDir = normalize(lightPos - FragPos);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * lightColor;

        // 3. Specular (Phong vs Blinn-Phong) - hanya pada sisi yang terkena cahaya
        vec3 viewDir = normalize(viewPos - FragPos);
        float spec = 0.0;
        if (diff > 0.0) {
            if (useBlinnPhong) {
                vec3 halfwayDir = normalize(lightDir + viewDir);
                spec = pow(max(dot(norm, halfwayDir), 0.0), shininess);       // Blinn-Phong
            } else {
                vec3 reflectDir = reflect(-lightDir, norm);
                spec = pow(max(dot(viewDir, reflectDir), 0.0), shininess);     // Phong
            }
        }
        vec3 specular = specularStrength * spec * lightColor;

        // 4. Bayangan (Shadow Factor 0.2 saat dalam bayangan; ambient tidak ikut gelap)
        float shadow = useShadow ? calcShadow(FragPosLightSpace, norm, lightDir) : 0.0;
        float shadowTerm = mix(1.0, 0.2, shadow);

        // specular tidak dikali warna dasar, sehingga kilau putih tampak jelas
        result = (ambient + shadowTerm * diffuse) * baseColor + shadowTerm * specular;
    }

    if (grayscale) {
        float g = dot(result, vec3(0.299, 0.587, 0.114));
        result = vec3(g);
    }
    FragColor = vec4(result, 1.0);
}