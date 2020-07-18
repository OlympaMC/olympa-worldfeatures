package fr.olympa.worldfeatures.elevators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.olympa.api.utils.observable.AbstractObservable;
import fr.olympa.api.utils.spigot.SpigotUtils;
import fr.olympa.worldfeatures.OlympaWorldFeatures;

public class Elevator extends AbstractObservable {
	
	private static final PotionEffect INVISIBILITY = new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, false, false);
	
	//private int id;
	
	private List<Floor> floors = new ArrayList<>(5);
	private int floor = 0;
	
	private Set<Chunk> chunks = new HashSet<>();
	private List<ArmorStand> stands = new ArrayList<>();
	private BukkitTask task = null;
	
	private BlockData blockData;
	private final World world;
	private final int xMin, zMin, xMax, zMax;
	
	public Elevator(World world, int floor0y, int x1, int z1, int x2, int z2) {
		this.world = world;
		this.xMin = Math.min(x1, x2);
		this.zMin = Math.min(z1, z2);
		this.xMax = Math.max(x1, x2);
		this.zMax = Math.max(z1, z2);
		
		blockData = Bukkit.createBlockData(Material.SMOOTH_STONE);
		addFloor(new Floor(floor0y));
		spawn();
	}
	
	private Elevator(World world, int xMin, int zMin, int xMax, int zMax, BlockData blockData) {
		this.world = world;
		this.xMin = xMin;
		this.zMin = zMin;
		this.xMax = xMax;
		this.zMax = zMax;
		this.blockData = blockData;
	}
	
	public void addFloor(int y) {
		addFloor(new Floor(y));
		super.update();
	}
	
	private void addFloor(Floor floor) {
		floors.add(floor);
		Collections.sort(floors, (o1, o2) -> Integer.compare(o1.y, o2.y));
	}
	
	public List<Floor> getFloors() {
		return floors;
	}

	public BlockData getBlockData() {
		return blockData;
	}

	public void setBlockData(BlockData blockData) {
		this.blockData = blockData;
		super.update();
		destroy();
		spawn();
	}

	private void spawn() {
		int y = floors.get(floor).y;
		for (int x = xMin; x <= xMax; x++) {
			for (int z = zMin; z <= zMax; z++) {
				Location location = new Location(world, x + 0.5, y - 1, z + 0.5);
				ArmorStand stand = world.spawn(location, ArmorStand.class);
				stand.setPersistent(false);
				stand.setGravity(false);
				//stand.setVisible(false);
				stand.setInvulnerable(true);
				
				Shulker shulker = world.spawn(location, Shulker.class);
				shulker.setPersistent(false);
				shulker.setAI(false);
				shulker.setGravity(false);
				shulker.addPotionEffect(INVISIBILITY);
				shulker.setInvulnerable(true);
				//shulker.setCustomName(Integer.toString(id));
				
				FallingBlock block = world.spawnFallingBlock(location, blockData);
				block.setPersistent(false);
				block.setGravity(false);
				block.setHurtEntities(false);
				block.setInvulnerable(true);
				//block.setTicksLived(-999999999); à faire en NMS
				
				stand.addPassenger(shulker);
				stand.addPassenger(block);
				
				stands.add(stand);
				chunks.add(location.getChunk());
			}
		}
	}
	
	private void destroy() {
		for (Iterator<ArmorStand> iterator = stands.iterator(); iterator.hasNext();) {
			ArmorStand stand = iterator.next();
			stand.getPassengers().forEach(Entity::remove);
			stand.remove();
			iterator.remove();
		}
	}
	
	public void ascend() {
		if (task != null) return;
		if (floor >= floors.size()) return;
		Floor to = floors.get(floor + 1);
		task = new BukkitRunnable() {
			double y = floors.get(floor).y;
			
			@Override
			public void run() {
				for (Chunk chunk : chunks) {
					for (Entity entity : chunk.getEntities()) {
						if (entity instanceof ArmorStand || entity instanceof Shulker || entity instanceof FallingBlock) continue;
						Location location = entity.getLocation();
						int x = location.getBlockX();
						int z = location.getBlockZ();
						if (x >= xMin && x <= xMax && z >= zMin && z <= zMax) {
							if (location.getY() - y < 1) { // l'entité est vraisemblablement sur la plateforme
								entity.teleport(location.add(0, 0.05, 0));
							}
						}
					}
				}
				stands.forEach(stand -> {
					stand.teleport(stand.getLocation().add(0, 1, 0));
				});
				if ((y += 0.05) >= to.y) {
					floor++;
					cancel();
					task = null;
				}
			}
		}.runTaskTimer(OlympaWorldFeatures.getInstance(), 1L, 1L);
	}
	
	public void descend() {
		if (task != null) return;
		if (floor <= 0) return;
		Floor to = floors.get(floor - 1);
		task = new BukkitRunnable() {
			double y = floors.get(floor).y;
			
			@Override
			public void run() {
				stands.forEach(stand -> stand.teleport(stand.getLocation().subtract(0, 0.05, 0)));
				if ((y -= 0.05) <= to.y) {
					floor--;
					cancel();
					task = null;
				}
			}
		}.runTaskTimer(OlympaWorldFeatures.getInstance(), 1L, 1L);
	}
	
	public Map<String, Object> serialize() {
		Map<String, Object> map = new HashMap<>();
		
		map.put("world", world.getName());
		map.put("xMin", xMin);
		map.put("zMin", zMin);
		map.put("xMax", xMax);
		map.put("zMax", zMax);
		map.put("blockdata", blockData.getAsString());
		
		List<Map<String, Object>> floorsMaps = new ArrayList<>(floors.size());
		for (Floor floor : floors) {
			Map<String, Object> floorMap = new HashMap<>();
			floorMap.put("y", floor.y);
			if (floor.getButtonUp() != null) floorMap.put("up", SpigotUtils.convertLocationToString(floor.buttonUp));
			if (floor.getButtonDown() != null) floorMap.put("down", SpigotUtils.convertLocationToString(floor.buttonDown));
			floorsMaps.add(floorMap);
		}
		map.put("floors", floorsMaps);
		
		return map;
	}
	
	public static Elevator deserialize(Map<String, Object> map) {
		Elevator elevator = new Elevator(Bukkit.getWorld((String) map.get("world")), (int) map.get("xMin"), (int) map.get("zMin"), (int) map.get("xMax"), (int) map.get("zMax"), Bukkit.createBlockData((String) map.get("blockdata")));
		
		List<Map<String, Object>> floorsMaps = (List<Map<String, Object>>) map.get("floors");
		for (Map<String, Object> floorMap : floorsMaps) {
			Floor floor = elevator.new Floor((int) floorMap.get("y"));
			if (floorMap.containsKey("up")) floor.setButtonUp(SpigotUtils.convertStringToLocation((String) map.get("up")));
			if (floorMap.containsKey("down")) floor.setButtonDown(SpigotUtils.convertStringToLocation((String) map.get("down")));
			elevator.addFloor(floor);
		}
		
		elevator.spawn(); //TODO gérer chunks etc.
		return elevator;
	}
	
	class Floor {
		
		private final int y;
		private Location buttonUp, buttonDown;
		
		private Floor(int y) {
			this.y = y;
		}
		
		public Location getButtonUp() {
			return buttonUp;
		}
		
		public void setButtonUp(Location buttonUp) {
			this.buttonUp = buttonUp;
			update();
		}
		
		public Location getButtonDown() {
			return buttonDown;
		}
		
		public void setButtonDown(Location buttonDown) {
			this.buttonDown = buttonDown;
			update();
		}
		
	}
	
}
