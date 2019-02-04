in {
	tex2D diffuse_map;
	tex2D specular_map;
	float glossiness = 0.5;
}

variant {
	vertex {
		out {
			vec2 v_uv;
		}

		source %{
			v_uv = vUV0;
		%}
	}

	pixel {
		source %{
			vec4 diffuse_color = (texture2D(diffuse_map, v_uv * 2.5) + texture2D(diffuse_map, v_uv * 55.0)) * 0.5;
			vec4 specular_color = (texture2D(specular_map, v_uv * 2.5) + texture2D(specular_map, v_uv * 55.0)) * 0.5;

			%diffuse% = diffuse_color.xyz;
			%specular% = specular_color.xyz;
			%glossiness% = glossiness;
		%}
	}
}
