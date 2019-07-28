import harfang as hg

# Game Constants
screen_width = 1280
screen_height = 720
font_size = screen_width / 40
screen_font = "assets/fonts/aerial.ttf"
game_hilite_color = hg.Color.Red + hg.Color.Yellow * 0.5

max_player_life = 5
max_enemy_spawn_interval = 5.0 # in seconds

max_torque_speed = 100.0
tank_rotation_speed = 25.0
aim_angle_range = {'min': -60, 'max': 60}
tank_cool_down_duration = 1.5

enemy_mass = 10.0

bullet_velocity = 15.0

max_debris = 256