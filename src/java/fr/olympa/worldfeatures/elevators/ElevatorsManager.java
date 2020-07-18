package fr.olympa.worldfeatures.elevators;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import fr.olympa.api.utils.observable.Observable.Observer;
import fr.olympa.core.spigot.OlympaCore;
import fr.olympa.worldfeatures.elevators.Elevator.Floor;

public class ElevatorsManager implements Listener {
	
	private final Map<Integer, Elevator> elevators = new HashMap<>();
	private int lastID = 0;
	
	private File elevatorsFile;
	private YamlConfiguration elevatorsYaml;
	
	public ElevatorsManager(File elevatorsFile) throws IOException {
		this.elevatorsFile = elevatorsFile;
		
		elevatorsFile.getParentFile().mkdirs();
		elevatorsFile.createNewFile();
		
		Bukkit.getScheduler().runTask(OlympaCore.getInstance(), () -> {
			elevatorsYaml = YamlConfiguration.loadConfiguration(elevatorsFile);
			
			for (String key : elevatorsYaml.getKeys(false)) {
				int id = Integer.parseInt(key);
				lastID = Math.max(id + 1, lastID);
				Elevator elevator = Elevator.deserialize(elevatorsYaml.getConfigurationSection(key).getValues(false));
				addElevator(elevator, id);
			}
		});
		new ElevatorsCommand(this).register();
	}
	
	public Elevator getElevator(int id) {
		return elevators.get(id);
	}
	
	public int addElevator(Elevator elevator) {
		int id = ++lastID;
		addElevator(elevator, id);
		return id;
	}

	private void addElevator(Elevator elevator, int id) {
		elevators.put(id, elevator);
		elevator.observe("maager_save", updateElevator(id, elevator));
	}
	
	private Observer updateElevator(int id, Elevator elevator) {
		return () -> {
			try {
				elevatorsYaml.set(String.valueOf(id), elevator == null ? null : elevator.serialize());
				elevatorsYaml.save(elevatorsFile);
			}catch (Exception e) {
				e.printStackTrace();
			}
		};
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent e) {
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Location click = e.getClickedBlock().getLocation();
			for (Elevator elevator : elevators.values()) {
				for (Floor floor : elevator.getFloors()) {
					if (click.equals(floor.getButtonDown())) {
						elevator.descend();
					}else if (click.equals(floor.getButtonUp())) {
						elevator.ascend();
					}else return;
				}
			}
		}
	}
	
}
