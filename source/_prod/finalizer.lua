
hg.LoadPlugins()

function string.starts(String,Start)
   return string.sub(String,1,string.len(Start))==Start
end

function string.ends(String,End)
   return End=='' or string.sub(String,-string.len(End))==End
end

function force_anisotropic(mat, name, geo_name)
	-- force anisotropic
	print("force_anisotropic(" .. name .. ")")
	values = {"diffuse_map", "specular_map", "normal_map", "opacity_map", "self_map",
            "light_map", "ao_map", "ambient_map", "shininess_map"}
	for n=1, #values do
		local ok, value = pcall(mat.GetValue, mat, values[n])
		if ok then
			value.tex_unit_cfg.min_filter = hg.TextureFilterAnisotropic
			value.tex_unit_cfg.mag_filter = hg.TextureFilterAnisotropic
		end
	end	
	print("done!")
end

function CountTextureSlotsFromMaterial(mat)
	map_count = 0

	for i = 0,mat:GetValueCount() - 1 do
		local texture_slot_name = mat:GetValueName(i)
		if string.ends(string.lower(texture_slot_name), '_map') then
			map_count = map_count + 1
		end
	end

	return map_count
 end	

function FinalizeMaterial(mat, name, geo_name)

  print("FinalizeMaterial() mat = " .. name)

  local m = {}
  for i = 0,mat:GetValueCount() - 1 do
    -- print(mat:GetValueName(i))
    table.insert(m, mat:GetValueName(i))
    m[mat:GetValueName(i)] = true
  end

  -- if CountTextureSlotsFromMaterial(mat) == 2 and m["diffuse_map"] and m["opacity_map"] then
  --   print("Updating shader to : rgba_map_opacity_float.isl")
  --   mat.shader = "assets/surfaces/rgba_map_opacity_float.isl"
  -- end
  if name == "edge" then
  	print("Updating shader to : assets/surfaces/edge.isl")
    mat.shader = "assets/surfaces/edge.isl"
  end
end

function FinalizeNode(node)
	-- print("FinalizeNode(" .. node:GetName() .. ")")
end

function FinalizeScene(scene)
	print("FinalizeScene()")

	-- add a default environment
	local _env
	if not scene:HasAspect("Environment") then
		_env = hg.Environment()
		scene:AddComponent(_end)
	end

	scene:UpdateAndCommitWaitAll()

	_env = hg.CastComponentToEnvironment(scene:GetComponents("Environment"):at(0))

	if _env ~= nil then
		_env:SetAmbientColor(hg.Color.White)
		_env:SetAmbientIntensity(0.25)

		scene:UpdateAndCommitWaitAll()
		print('Environment Ambient color set to White.')
	else
		print('Cannot get Environment component.')
	end

	-- look for a set of collision shapes and turn them to physics component
	-- local i
	-- local tank_node
	-- tank_node = scene:GetNode("tank")
	-- local rigid = hg.RigidBody()
	-- rigid:SetType(hg.RigidBodyDynamic)
	-- tank_node:AddComponent(rigid)

	-- local nodes
	-- nodes = scene:GetNodeChildren(tank_node)

	-- for i=0,nodes:size()-1 do
	-- 	local node = nodes:at(i)
	-- 	if node:GetName():lower():starts("tank_colshape_") then
	-- 		print("Found collision shape : " .. node:GetName())
	-- 		local colbox = hg.BoxCollision()
	-- 		local dimensions = node:GetTransform():GetScale()
	-- 		print(dimensions.x .. ', ' .. dimensions.y .. ', ' .. dimensions.z)
	-- 		local pos = node:GetTransform():GetPosition()
	-- 		colbox:SetDimensions(dimensions)
	-- 		colbox:SetMatrix(hg.Matrix4.TranslationMatrix(pos))
	-- 		colbox:SetMass(5.0)
	-- 		tank_node:AddComponent(colbox)
	-- 		node:SetDoNotInstantiate(true)
	-- 	end	
	-- end

	scene:UpdateAndCommitWaitAll()
end
