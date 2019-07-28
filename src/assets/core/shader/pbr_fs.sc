$input vWorldPos, vNormal, vTexCoord0, vTexCoord1, vTangent, vBinormal

#include <bgfx_shader.sh>

#define PI 3.14159265359

uniform vec4 uClock;

// Environment
uniform vec4 uFogColor;
uniform vec4 uFogState; // fog_near, 1.0/fog_range

// Lighting environment
uniform vec4 uAmbientColor;

uniform vec4 uLightPos[8]; // pos.xyz, 1.0/radius
uniform vec4 uLightDir[8]; // dir.xyz, inner_rim
uniform vec4 uLightDiffuse[8]; // diffuse.xyz, outer_rim
uniform vec4 uLightSpecular[8]; // specular.xyz, pssm_bias

// Surface attributes
uniform vec4 uBaseOpacityColor;
uniform vec4 uOcclusionRoughnessMetalnessColor;
uniform vec4 uSelfColor;

// Texture slots
SAMPLER2D(uBaseOpacityMap, 0);
SAMPLER2D(uOcclusionRoughnessMetalnessMap, 1);
SAMPLER2D(uNormalMap, 2);
SAMPLER2D(uSelfMap, 4);
SAMPLERCUBE(uIrradianceMap, 8);
SAMPLERCUBE(uRadianceMap, 9);
SAMPLER2D(uBrdfMap, 10);

//
mat3 MakeMat3(vec3 c0, vec3 c1, vec3 c2) {
#ifdef BGFX_SHADER_LANGUAGE_GLSL
	return mat3(c0, c1, c2);
#else
	return transpose(mat3(c0, c1, c2));
#endif
}

vec3 GetT(mat4 m) { return vec3(m[0][3], m[1][3], m[2][3]); }

// Forward PBR GGX
float DistributionGGX(float NdotH, float roughness) {
	const float a = roughness * roughness;
	const float a2 = a * a;

	const float divisor = NdotH * NdotH * (a2 - 1.0) + 1.0;
	return a2 / (PI * divisor * divisor); 
}

float GeometrySchlickGGX(float NdotW, float k) {
	return NdotW / (NdotW * (1.0 - k) + k);
}

float GeometrySmith(float NdotV, float NdotL, float roughness) {
	const float r = roughness + 1.0;
	const float k = (r * r) / 8.0;
	const float ggx2 = GeometrySchlickGGX(NdotV, k);
	const float ggx1 = GeometrySchlickGGX(NdotL, k);
	return ggx1 * ggx2;
}

vec3 FresnelSchlick(float cosTheta, vec3 F0) {
	return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

vec3 FresnelSchlickRoughness(float cosTheta, vec3 F0, float roughness) {
	return F0 + (max(vec3(1.0 - roughness, 1.0 - roughness, 1.0 - roughness), F0) - F0) * pow(1.0 - cosTheta, 5.0);
}

vec3 PBRGGX(vec3 P, vec3 V, vec3 N, vec3 R, vec3 albedo, vec3 occlusion, float roughness, float metalness) {
	float NdotV = clamp(dot(N, V), 0.0, 0.99);

	vec3 F0 = vec3(0.04, 0.04, 0.04);
	F0 = mix(F0, albedo, metalness);

	vec3 color = vec3(0.0, 0.0, 0.0);

#if 1
	for (int i = 0; i < 8; ++i) {
		vec3 L = normalize(uLightPos[i].xyz - P);
		vec3 H = normalize(V + L);

		float NdotH = max(dot(N, H), 0.0);
		float NdotL = max(dot(N, L), 0.0);
		float HdotV = max(dot(H, V), 0.0);

		float distance = length(uLightPos[i].xyz - P);
		float attenuation = 1.0; // / (distance * distance);
		vec3 radiance = uLightDiffuse[i].xyz * attenuation;

		float D = DistributionGGX(NdotH, roughness);
		float G = GeometrySmith(NdotV, NdotL, roughness);
		vec3 F = FresnelSchlick(HdotV, F0);

		vec3 specularBRDF = (F * D * G) / max(4.0 * NdotV * NdotL, 0.001);

		vec3 kD = (vec3(1.0, 1.0, 1.0) - F) * (1.0 - metalness); // metallic materials have no diffuse (NOTE: mimics mental ray and 3DX Max ART renderers behavior)
		vec3 diffuseBRDF = kD * albedo;

		color += (diffuseBRDF + specularBRDF) * radiance * NdotL;
	}
#endif

	// IBL ambient
#if 1
	const float MAX_REFLECTION_LOD = 6.0;

#if 0 // LOD selection
	vec3 Ndx = normalize(N + ddx(N));
	float dx = length(Ndx.xy / Ndx.z - N.xy / N.z) * 256.0;
	vec3 Ndy = normalize(N + ddy(N));
	float dy = length(Ndy.xy / Ndy.z - N.xy / N.z) * 256.0;

	float dd = max(dx, dy);
	float lod_level = log2(dd);
#endif

	vec3 irradiance = textureCube(uIrradianceMap, N).xyz;
	vec3 diffuse = irradiance * albedo;
	vec3 radiance = textureCubeLod(uRadianceMap, R, roughness * MAX_REFLECTION_LOD).rgb;

	vec3 F = FresnelSchlickRoughness(NdotV, F0, roughness);
	vec2 brdf = texture2D(uBrdfMap, vec2(NdotV, roughness)).rg;
	vec3 specular = radiance * (F * brdf.x + brdf.y);

	vec3 kS = specular;
	vec3 kD = vec3(1.0, 1.0, 1.0) - kS;
	kD *= 1.0 - metalness;

	color += kD * diffuse + specular;
#endif
	return color * occlusion;
}

//
vec3 DistanceFog(vec3 pos, vec3 color) {
/*
	if (uFogState.y == 0.0)
		return color;

	float k = clamp((pos.z - uFogState.x) * uFogState.y, 0.0, 1.0);
	return mix(color, uFogColor.xyz, k);
*/
	return color;
}

float sRGB2linear(float x) {
	return x <= 0.04045 ? x * 0.0773993808 : // 1.0/12.92
		pow((x + 0.055) / 1.055, 2.4);
}

float linear2sRGB(float x) {
	return x <= 0.0031308 ? 12.92 * x : 1.055 * pow(x, 0.41666) - 0.055;
}

vec3 sRGB2linear(vec3 c) {
	return vec3(sRGB2linear(c.x), sRGB2linear(c.y), sRGB2linear(c.z));
}

vec3 linear2sRGB(vec3 c) {
	return vec3(linear2sRGB(c.x), linear2sRGB(c.y), linear2sRGB(c.z));
}

// Entry point of the forward pipeline default uber shader (Phong and PBR)
void main() {
#if USE_BASE_COLOR_OPACITY_MAP
	vec4 base_opacity = texture2D(uBaseOpacityMap, vTexCoord0);
	base_opacity.xyz = sRGB2linear(base_opacity.xyz);
#else
	vec4 base_opacity = uBaseOpacityColor;
#endif

#if USE_OCCLUSION_ROUGHNESS_METALNESS_MAP
	vec4 occ_rough_metal = texture2D(uOcclusionRoughnessMetalnessMap, vTexCoord0);
#else
	vec4 occ_rough_metal = uOcclusionRoughnessMetalnessColor;
#endif

#if USE_SELF_MAP
	vec4 self = texture2D(uSelfMap, vTexCoord0);
#else
	vec4 self = uSelfColor;
#endif

	//
	vec3 P = vWorldPos; // fragment world pos
	vec3 V = normalize(GetT(u_invView) - P); // view vector
	vec3 N = normalize(vNormal); // geometry normal

#if USE_NORMAL_MAP
	vec3 T = normalize(vTangent);
	vec3 B = normalize(vBinormal);

	mat3 TBN = MakeMat3(T, B, N);

	N.xy = texture2D(uNormalMap, vTexCoord0).xy * 2.0 - 1.0;
	N.z = sqrt(1.0 - dot(N.xy, N.xy));
	N = normalize(mul(N, TBN));
#endif

	vec3 R = reflect(-V, N); // view reflection vector around normal

	vec3 color = PBRGGX(P, V, N, R, base_opacity.xyz, occ_rough_metal.x, occ_rough_metal.g, occ_rough_metal.b);

	color += self.xyz;

	color += uAmbientColor.xyz;

	float opacity = base_opacity.w;

//	color = DistanceFog(vWorldPos, color);

	color = linear2sRGB(color);

	gl_FragColor = vec4(color.xyz, opacity);
}
