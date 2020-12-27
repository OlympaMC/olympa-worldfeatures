package fr.olympa.worldfeatures.elevators;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import fr.olympa.api.utils.Point2D;
import fr.olympa.api.utils.observable.AbstractObservable;
import fr.olympa.api.utils.spigot.SpigotUtils;
import fr.olympa.worldfeatures.OlympaWorldFeatures;

public class Elevator extends AbstractObservable {
	
	private static final int PAUSE_TIME = 10;
	private static final PotionEffect INVISIBILITY = new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, false, false);
	
	private List<Floor> floors = new ArrayList<>(5);
	private int floor = 0;
	
	protected int id;
	protected boolean isSpawned = false;
	
	private List<ArmorStand> stands = new ArrayList<>();
	private List<FallingBlock> blocks = new ArrayList<>();
	private BukkitTask blocksTask = null;
	
	private BukkitTask task = null;
	private boolean goingUp = false;
	
	private int speed;
	private double tickYAddition;
	private PotionEffect levitationEffect;
	private double aboveY;
	
	private BlockData blockData;
	public final World world;
	public final int xMin, zMin, xMax, zMax;
	public final double xMinPlayer, zMinPlayer, xMaxPlayer, zMaxPlayer;
	protected final Map<Point2D, Chunk> chunks = new HashMap<>();
	
	public Elevator(World world, int floor0y, int x1, int z1, int x2, int z2) {
		this(world, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2), Bukkit.createBlockData(Material.SMOOTH_STONE), 1);
		
		addFloor(new Floor(floor0y));
	}
	
	private Elevator(World world, int xMin, int zMin, int xMax, int zMax, BlockData blockData, int speed) {
		this.world = world;
		this.xMin = xMin;
		this.xMinPlayer = xMin - 0.3;
		this.zMin = zMin;
		this.zMinPlayer = zMin - 0.3;
		this.xMax = xMax;
		this.xMaxPlayer = xMax + 1.3;
		this.zMax = zMax;
		this.zMaxPlayer = zMax + 1.3;
		this.blockData = blockData;
		
		setSpeed(speed);
		
		for (int x = xMin; x <= xMax; x++) {
			for (int z = zMin; z <= zMax; z++) {
				Point2D chunkPoint = new Point2D(x >> 4, z >> 4);
				Chunk chunk = null;
				if (world.isChunkLoaded(chunkPoint.x, chunkPoint.z)) chunk = chunkPoint.asChunk(world);
				chunks.put(chunkPoint, chunk);
			}
		}
	}
	
	public int getID() {
		return id;
	}
	
	public void addFloor(int y) {
		addFloor(new Floor(y));
		super.update();
	}
	
	private void addFloor(Floor floor) {
		floors.add(floor);
		sortFloors();
	}
	
	protected void sortFloors() {
		Collections.sort(floors, (o1, o2) -> Integer.compare(o1.getY(), o2.getY()));
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
		if (isSpawned) {
			destroy();
			spawn();
		}
	}

	public int getSpeed() {
		return speed;
	}
	
	public void setSpeed(int speed) {
		Validate.isTrue(speed > 0);
		this.speed = speed;
		this.tickYAddition = (speed * 0.9D) / 20D;
		this.levitationEffect = new PotionEffect(PotionEffectType.LEVITATION, 25, speed - 1, false, false, false);
		this.aboveY = 1.7D + speed * 0.1D;
		super.update();
	}
	
	public void updateChunks() {
		boolean loaded = chunks.values().stream().allMatch(x -> x != null && x.isLoaded());
		if (isSpawned) {
			if (!loaded) destroy();
		}else {
			if (loaded) spawn();
		}
	}
	
	private void spawn() {
		if (isSpawned) return;
		isSpawned = true;
		
		int y = floors.get(floor).getY();
		for (int x = xMin; x <= xMax; x++) {
			for (int z = zMin; z <= zMax; z++) {
				Location location = new Location(world, x + 0.5, y - 1, z + 0.5);
				ArmorStand stand = world.spawn(location, ArmorStand.class);
				stand.setPersistent(false);
				stand.setGravity(false);
				stand.setVisible(false);
				stand.setInvulnerable(true);
				stands.add(stand);
				
				Shulker shulker = world.spawn(location, Shulker.class);
				shulker.setPersistent(false);
				shulker.setAI(false);
				shulker.setGravity(false);
				shulker.addPotionEffect(INVISIBILITY);
				shulker.setInvulnerable(true);
				
				FallingBlock block = world.spawnFallingBlock(location, blockData);
				block.setPersistent(false);
				block.setGravity(false);
				block.setHurtEntities(false);
				block.setInvulnerable(true);
				blocks.add(block);
				//block.setTicksLived(-999999999); à faire en NMS
				
				stand.addPassenger(shulker);
				stand.addPassenger(block);
			}
		}
		
		blocksTask = Bukkit.getScheduler().runTaskTimerAsynchronously(OlympaWorldFeatures.getInstance(), () -> blocks.forEach(block -> block.setTicksLived(1)), 100, 500);
	}
	
	protected void destroy() {
		if (!isSpawned) return;
		isSpawned = false;
		
		for (Iterator<ArmorStand> iterator = stands.iterator(); iterator.hasNext();) {
			ArmorStand stand = iterator.next();
			stand.getPassengers().forEach(Entity::remove);
			stand.remove();
			iterator.remove();
		}
		blocksTask.cancel();
		blocksTask = null;
	}
	
	public void ascend(int toID) {
		if (!isSpawned) return;
		if (task != null) return;
		if (toID >= floors.size()) return;
		goingUp = true;
		Floor to = floors.get(toID);
		task = new BukkitRunnable() {
			double y = floors.get(floor).getY();
			
			int pause = -1;
			int potion = 0;
			List<LivingEntity> riding = new ArrayList<>(5);
			
			@Override
			public void run() {
				if (pause != -1) {
					if (--pause == 0) {
						cancel();
						task = null;
					}
					return;
				}
				
				y += tickYAddition;
				List<LivingEntity> on = new ArrayList<>(5);
				for (Chunk chunk : chunks.values()) {
					for (Entity entity : chunk.getEntities()) {
						if (entity instanceof ArmorStand || entity instanceof Shulker || entity instanceof FallingBlock) continue;
						Location location = entity.getLocation();
						double x = location.getX();
						double z = location.getZ();
						if (x > xMinPlayer && x < xMaxPlayer && z > zMinPlayer && z < zMaxPlayer) {
							double diff = location.getY() - y;
							if (diff > 0 && diff < aboveY + 0.2) { // l'entité est vraisemblablement sur la plateforme
								if (entity instanceof LivingEntity) {
									LivingEntity le = (LivingEntity) entity;
									on.add(le);
									boolean joins = !riding.remove(le);
									if (joins) {
										location.setY(y + aboveY);
										entity.teleport(location);
									}
									if (joins || potion == 0) le.addPotionEffect(levitationEffect);
								}
							}
						}
					}
				}
				riding.forEach(le -> le.removePotionEffect(PotionEffectType.LEVITATION));
				riding = on;
				stands.forEach(entity -> {
					Location location = entity.getLocation();
					location.setY(Math.min(to.getY() - 1, y - 1));
					setNMSPosition(entity, location);
				});
				if (potion-- == 0) potion = 20;
				
				if (y >= to.getY()) {
					floor = toID;
					playSound();
					riding.forEach(le -> le.removePotionEffect(PotionEffectType.LEVITATION));
					riding.clear();
					pause = PAUSE_TIME;
				}
			}
		}.runTaskTimer(OlympaWorldFeatures.getInstance(), 1L, 1L);
	}
	
	public void descend(int toID) {
		if (!isSpawned) return;
		if (task != null) return;
		if (toID < 0) return;
		goingUp = false;
		Floor to = floors.get(toID);
		task = new BukkitRunnable() {
			double y = floors.get(floor).getY();
			int pause = -1;
			
			@Override
			public void run() {
				if (pause != -1) {
					if (--pause == 0) {
						cancel();
						task = null;
					}
					return;
				}
				
				y -= tickYAddition;
				stands.forEach(entity -> {
					Location location = entity.getLocation();
					location.setY(Math.max(to.getY() - 1, y - 1));
					setNMSPosition(entity, location);
				});
				if (y <= to.getY()) {
					floor = toID;
					playSound();
					pause = PAUSE_TIME;
				}
			}
		}.runTaskTimer(OlympaWorldFeatures.getInstance(), 1L, 1L);
	}
	
	private void playSound() {
		world.playSound(new Location(world, xMin, floors.get(floor).getY(), zMin), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);
	}
	
	private void setNMSPosition(Entity entity, Location newLocation) {
		((CraftEntity) entity).getHandle().setPosition(newLocation.getX(), newLocation.getY(), newLocation.getZ());
	}
	
	public Map<String, Object> serialize() {
		Map<String, Object> map = new HashMap<>();
		
		map.put("world", world.getName());
		map.put("xMin", xMin);
		map.put("zMin", zMin);
		map.put("xMax", xMax);
		map.put("zMax", zMax);
		map.put("blockdata", blockData.getAsString());
		map.put("speed", speed);
		
		List<Map<String, Object>> floorsMaps = new ArrayList<>(floors.size());
		for (Floor floor : floors) {
			Map<String, Object> floorMap = new HashMap<>();
			floorMap.put("y", floor.getY());
			if (floor.buttonUp != null) floorMap.put("up", SpigotUtils.convertLocationToString(floor.buttonUp));
			if (floor.buttonDown != null) floorMap.put("down", SpigotUtils.convertLocationToString(floor.buttonDown));
			floorsMaps.add(floorMap);
		}
		map.put("floors", floorsMaps);
		
		return map;
	}
	
	public static Elevator deserialize(Map<String, Object> map) {
		Elevator elevator = new Elevator(Bukkit.getWorld((String) map.get("world")), (int) map.get("xMin"), (int) map.get("zMin"), (int) map.get("xMax"), (int) map.get("zMax"), Bukkit.createBlockData((String) map.get("blockdata")), (int) map.get("speed"));
		
		List<Map<String, Object>> floorsMaps = (List<Map<String, Object>>) map.get("floors");
		for (Map<String, Object> floorMap : floorsMaps) {
			Floor floor = elevator.new Floor((int) floorMap.get("y"));
			if (floorMap.containsKey("up")) floor.buttonUp = SpigotUtils.convertStringToLocation((String) floorMap.get("up"));
			if (floorMap.containsKey("down")) floor.buttonDown = SpigotUtils.convertStringToLocation((String) floorMap.get("down"));
			elevator.addFloor(floor);
		}
		
		return elevator;
	}
	
	class Floor {
		
		private int y;
		private Location buttonUp, buttonDown;
		
		private Floor(int y) {
			this.setY(y);
		}
		
		public int getY() {
			return y;
		}
		
		public void setY(int y) {
			this.y = y;
			sortFloors();
		}

		public boolean click(Location location) {
			boolean isUp = location.equals(buttonUp);
			boolean isDown = isUp ? false : location.equals(buttonDown);
			
			if (!isUp && !isDown) return false;
			
			if (task != null) return true;
			
			int id = floors.indexOf(this);
			if (id == floor) {
				if (isUp) {
					ascend(id + 1);
				}else descend(id - 1);
			}else {
				if (id > floor) {
					ascend(id);
				}else descend(id);
			}
			return true;
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
