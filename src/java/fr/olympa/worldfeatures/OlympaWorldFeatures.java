package fr.olympa.worldfeatures;

import java.io.File;
import java.io.IOException;

import fr.olympa.api.plugin.OlympaAPIPlugin;
import fr.olympa.worldfeatures.elevators.ElevatorsManager;

public class OlympaWorldFeatures extends OlympaAPIPlugin {
	
	private static OlympaWorldFeatures instance;
	
	public static OlympaWorldFeatures getInstance() {
		return instance;
	}
	
	public ElevatorsManager elevators;
	
	@Override
	public void onEnable() {
		instance = this;
		super.onEnable();
		
		try {
			getServer().getPluginManager().registerEvents(elevators = new ElevatorsManager(new File(getDataFolder(), "elevators.yml")), this);
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void onDisable() {
		super.onDisable();
		
		if (elevators != null) elevators.unload();
	}
	
}
