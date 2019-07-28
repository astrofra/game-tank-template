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
uniform vec4 uDiffuseColor;
uniform vec4 uSpecularColor;
uniform vec4 uSelfColor;

// Texture slots
SAMPLER2D(uDiffuseMap, 0); // PBR metalness in alpha
SAMPLER2D(uSpecularMap, 1); // PBR roughness in alpha
SAMPLER2D(uNormalMap, 2); // Parallax mapping elevation in alpha
SAMPLER2D(uLightMap, 3);
SAMPLER2D(uSelfMap, 4);
SAMPLER2D(uOpacityMap, 5);
SAMPLER2D(uAmbientMap, 6);
SAMPLER2D(uReflectionMap, 7);

//
mat3 MakeMat3(vec3 c0, vec3 c1, vec3 c2) {
#ifdef BGFX_SHADER_LANGUAGE_GLSL
	return mat3(c0, c1, c2);
#else
	return transpose(mat3(c0, c1, c2));
#endif
}

vec3 GetT(mat4 m) { return vec3(m[0][3], m[1][3], m[2][3]); }

//
struct LightModelOut {
	float i_diff;
	float i_spec;
};

struct LightContributionOut {
	vec3 diff;
	vec3 spec;
};

// Forward Phong
LightModelOut PhongLightModel(vec3 V, vec3 N, vec3 R, vec3 L, float gloss) {
	LightModelOut m;
	m.i_diff = max(-dot(L, N), 0.0);
	m.i_spec = pow(max(-dot(L, R), 0.0), gloss);
	return m;
}

float LightAttenuation(vec3 L, vec3 D, float dist, float attn, float inner_rim, float outer_rim) {
	float k = 1.0;
	if (attn > 0.0)
		k = max(1.0 - dist * attn, 0.0); // distance attenuation

	if (outer_rim > 0.0) {
		float c = dot(L, D);
		k *= clamp(1.0 - (c - inner_rim) / (outer_rim - inner_rim), 0.0, 1.0); // spot attenuation
	}
	return k;
}

LightContributionOut PhongLightModelContribution(vec3 P, vec3 V, vec3 N, vec3 R, float gloss, vec3 ao) {
	LightContributionOut c;

	// SLOT 0: linear light
	LightModelOut m = PhongLightModel(V, N, R, uLightDir[0].xyz, gloss);

	c.diff = uLightDiffuse[0].xyz * m.i_diff;
	c.spec = uLightSpecular[0].xyz * m.i_spec;

	// SLOT 1-N: point/spot light
	for (int i = 1; i < 8; ++i) {
		vec3 L = P - uLightPos[i].xyz; // incident

		float D = length(L);
		L /= D; // normalize

		m = PhongLightModel(V, N, R, L, gloss);

		float k = LightAttenuation(L, uLightDir[i].xyz, D, uLightPos[i].w, uLightDir[i].w, uLightDiffuse[i].w);

		c.diff += uLightDiffuse[i].xyz * m.i_diff * k;
		c.spec += uLightSpecular[i].xyz * m.i_spec * k;
	}

	c.diff += uAmbientColor.xyz * ao;
	return c;
}

vec3 Phong(vec3 P, vec3 V, vec3 N, vec3 R, vec3 diff, vec3 spec, vec3 self, vec3 light, vec3 ao) {
	float gloss = 1.0 / uSpecularColor.w;
	const LightContributionOut c = PhongLightModelContribution(P, V, N, R, gloss, ao);

	vec3 color = diff * (c.diff + light) + spec * c.spec + self;

#if USE_REFLECTION_MAP
	vec4 reflection = texture2D(uReflectionMap, R.xy);
	color += reflection.xyz;
#endif
	return color;
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

// Entry point of the forward pipeline default uber shader (Phong and PBR)
void main() {
#if USE_DIFFUSE_MAP
	vec4 diff = texture2D(uDiffuseMap, vTexCoord0);
#else
	vec4 diff = uDiffuseColor;
#endif

#if USE_SPECULAR_MAP
	vec4 spec = texture2D(uSpecularMap, vTexCoord0);
#else
	vec4 spec = uSpecularColor;
#endif

#if USE_SELF_MAP
	vec4 self = texture2D(uSelfMap, vTexCoord0);
#else
	vec4 self = uSelfColor;
#endif

#if USE_AMBIENT_MAP
	vec4 ao = texture2D(uAmbientMap, vTexCoord1);
#else
	vec4 ao = vec4(1.0, 1.0, 1.0, 1.0);
#endif

#if USE_LIGHT_MAP
	vec4 light = texture2D(uLightMap, vTexCoord1);
#else
	vec4 light = vec4(0.0, 0.0, 0.0, 0.0);
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

	vec3 color = Phong(P, V, N, R, diff.xyz, spec.xyz, self.xyz, light.xyz, ao.xyz);

	color = DistanceFog(vWorldPos, color);

#if USE_OPACITY_MAP
	float opacity = texture2D(uOpacityMap, vTexCoord0).x;
#else
	float opacity = 1.0;
#endif

	gl_FragColor = vec4(color, opacity);
}
