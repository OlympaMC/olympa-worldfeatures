package fr.olympa.worldfeatures.elevators;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import fr.olympa.api.command.complex.Cmd;
import fr.olympa.api.command.complex.CommandContext;
import fr.olympa.api.command.complex.ComplexCommand;
import fr.olympa.worldfeatures.OlympaWorldFeatures;
import fr.olympa.worldfeatures.WorldFeaturesPermissions;
import fr.olympa.worldfeatures.elevators.Elevator.Floor;

public class ElevatorsCommand extends ComplexCommand {
	
	private ElevatorsManager elevators;
	
	public ElevatorsCommand(ElevatorsManager elevators) {
		super(OlympaWorldFeatures.getInstance(), "elevators", "Permet de gérer les ascenseurs.", WorldFeaturesPermissions.ELEVATORS_COMMAND, "lifts");
		this.elevators = elevators;
	}
	
	@Cmd (player = true, args = { "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER" }, min = 5, syntax = "<floor 0 y> <xMin> <zMin> <xMax> <zMax>")
	public void create(CommandContext cmd) {
		Elevator elevator = new Elevator(getPlayer().getWorld(), cmd.getArgument(0), cmd.getArgument(1), cmd.getArgument(2), cmd.getArgument(3), cmd.getArgument(4));
		sendSuccess("L'ascenseur %d a été créé.", elevators.addElevator(elevator));
	}
	
	@Cmd (args = { "INTEGER", "INTEGER" }, min = 2, syntax = "<elevator id> <floor y>")
	public void addFloor(CommandContext cmd) {
		Elevator elevator = getElevator(cmd.getArgument(0));
		if (elevator == null) return;
		elevator.addFloor(cmd.getArgument(1));
		sendSuccess("Vous avez ajouté un étage à l'ascenseur.");
	}
	
	@Cmd (player = true, args = { "INTEGER", "INTEGER", "UP|DOWN" }, min = 3, syntax = "<elevator id> <floor id> <button type>")
	public void setButton(CommandContext cmd) {
		Elevator elevator = getElevator(cmd.getArgument(0));
		if (elevator == null) return;
		
		Integer floorID = cmd.<Integer>getArgument(1);
		Floor floor = elevator.getFloors().get(floorID);
		if (floor == null) {
			sendError("Il n'y a pas d'étage avec l'ID %d.", floorID);
			return;
		}
		
		Block target = getPlayer().getTargetBlockExact(3);
		if (target == null) {
			sendError("Tu dois regarder un bloc situé à moins de 3 blocs de distance de toi.");
			return;
		}
		String button = cmd.getArgument(2);
		if (button.equalsIgnoreCase("up")) {
			floor.setButtonUp(target.getLocation());
			sendSuccess("Tu as modifié la position du bouton de montée.");
		}else if (button.equalsIgnoreCase("down")) {
			floor.setButtonDown(target.getLocation());
			sendSuccess("Tu as modifié la position du bouton de descente.");
		}else {
			sendError("Argument %s inconnu (requis : UP / DOWN)", button);
		}
	}
	
	@Cmd (player = true, args = { "INTEGER" }, min = 2, syntax = "<elevator id> <block data>")
	public void setBlock(CommandContext cmd) {
		Elevator elevator = getElevator(cmd.getArgument(0));
		if (elevator == null) return;
		
		try {
			BlockData blockData = Bukkit.createBlockData(cmd.<String>getArgument(1));
			elevator.setBlockData(blockData);
			sendSuccess("Tu as modifié la blockdata de l'ascenseur (%s)", blockData.getAsString());
		}catch (IllegalArgumentException ex) {
			sendError("La blockdata spécifiée est invalide.");
		}
	}
	
	@Cmd (player = true)
	public void toggleMoveListen(CommandContext cmd) {
		elevators.listenMoveEvents = !elevators.listenMoveEvents;
	}
	
	private Elevator getElevator(int id) {
		Elevator elevator = elevators.getElevator(id);
		if (elevator == null) sendError("Il n'y a pas d'ascenseur avec l'ID %d.", id);
		return elevator;
	}
	
}
