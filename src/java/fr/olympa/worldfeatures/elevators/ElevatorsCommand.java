package fr.olympa.worldfeatures.elevators;

import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import fr.olympa.api.command.complex.Cmd;
import fr.olympa.api.command.complex.CommandContext;
import fr.olympa.api.command.complex.ComplexCommand;
import fr.olympa.api.utils.Prefix;
import fr.olympa.api.utils.spigot.SpigotUtils;
import fr.olympa.worldfeatures.OlympaWorldFeatures;
import fr.olympa.worldfeatures.WorldFeaturesPermissions;
import fr.olympa.worldfeatures.elevators.Elevator.Floor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

public class ElevatorsCommand extends ComplexCommand {
	
	private static final HoverEvent HOVER_EVENT = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText("§eClique pour suggérer la commande !")));
	
	private ElevatorsManager elevators;
	
	public ElevatorsCommand(ElevatorsManager elevators) {
		super(OlympaWorldFeatures.getInstance(), "elevators", "Permet de gérer les ascenseurs.", WorldFeaturesPermissions.ELEVATORS_COMMAND, "lifts");
		this.elevators = elevators;
		super.addArgumentParser("ELEVATOR", sender -> elevators.getElevators().keySet().stream().map(String::valueOf).collect(Collectors.toList()), arg -> {
			try {
				Elevator elevator = elevators.getElevator(Integer.parseInt(arg));
				if (elevator != null) return elevator;
			}catch (NumberFormatException ex) {}
			return null;
		}, x -> String.format("Il n'y a pas d'ascenseur avec l'ID %d.", x));
	}
	
	@Cmd (player = true, args = { "INTEGER", "INTEGER", "INTEGER", "INTEGER", "INTEGER" }, min = 5, syntax = "<floor 0 y> <xMin> <zMin> <xMax> <zMax>")
	public void create(CommandContext cmd) {
		Elevator elevator = new Elevator(getPlayer().getWorld(), cmd.getArgument(0), cmd.getArgument(1), cmd.getArgument(2), cmd.getArgument(3), cmd.getArgument(4));
		sendSuccess("L'ascenseur %d a été créé.", elevators.addElevator(elevator));
	}
	
	private TextComponent createCommandComponent(String legacyText, String command, String after, Elevator elevator) {
		TextComponent compo = new TextComponent();
		compo.setHoverEvent(HOVER_EVENT);
		compo.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/elevators " + command + " " + elevator.getID() + after));
		for (BaseComponent baseComponent : TextComponent.fromLegacyText(legacyText)) compo.addExtra(baseComponent);
		return compo;
	}
	
	private TextComponent createButtonComponent(Elevator elevator, int floorID, Location button, String name, String type) {
		TextComponent compo = new TextComponent();
		compo.setHoverEvent(HOVER_EVENT);
		compo.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/elevators setButton " + elevator.getID() + " " + floorID + " " + type));
		
		TextComponent header = new TextComponent(name + " : ");
		header.setColor(ChatColor.YELLOW);
		compo.addExtra(header);
		
		TextComponent info = new TextComponent(button == null ? "non défini" : SpigotUtils.convertBlockLocationToString(button));
		info.setColor(button == null ? ChatColor.RED : ChatColor.GREEN);
		compo.addExtra(info);
		
		return compo;
	}
	
	@Cmd (args = "ELEVATOR")
	public void info(CommandContext cmd) {
		Elevator elevator = cmd.getArgument(0);
		
		sender.spigot().sendMessage(getElevatorInfo(elevator));
		
		sender.spigot().sendMessage(createCommandComponent(Prefix.DEFAULT.formatMessage("§eVitesse : §6§l%d§e (§6%s§e)", elevator.getSpeed(), elevator.getSpeed() * 0.9 + " blocks/seconde"), "setSpeed", " ", elevator));
		sender.spigot().sendMessage(createCommandComponent(Prefix.DEFAULT.formatMessage("§eBlockdata : §6%s", elevator.getBlockData().getAsString()), "setBlock", " ", elevator));
		
		sendSuccess("Étages : (%d)", elevator.getFloors().size());
		
		int id = 0;
		for (Floor floor : elevator.getFloors()) {
			TextComponent floorCompo = new TextComponent();
			floorCompo.setColor(ChatColor.YELLOW);
			floorCompo.addExtra(createCommandComponent(Prefix.DEFAULT.formatMessage("§eY : §6§l%d§e | ", floor.getY()), "setY", " " + id + " ", elevator));
			floorCompo.addExtra(createButtonComponent(elevator, id, floor.getButtonUp(), "Montée", "UP"));
			floorCompo.addExtra(new TextComponent(" | "));
			floorCompo.addExtra(createButtonComponent(elevator, id, floor.getButtonDown(), "Descente", "DOWN"));
			
			sender.spigot().sendMessage(floorCompo);
			id++;
		}
		
		sender.spigot().sendMessage(createCommandComponent(Prefix.DEFAULT_GOOD.formatMessage("Créer un nouvel étage..."), "addFloor", " ", elevator));
	}
	
	@Cmd
	public void list(CommandContext cmd) {
		for (Elevator elevator : elevators.getElevators().values()) {
			sender.spigot().sendMessage(getElevatorInfo(elevator));
		}
	}

	private TextComponent getElevatorInfo(Elevator elevator) {
		return createCommandComponent(Prefix.DEFAULT_GOOD.formatMessage("Ascenseur §l#%d§a au monde §l%s§a, s'étend de §l%d %d§a à §l%d %d §a(§l%d étages§a)%s", elevator.getID(), elevator.world.getName(), elevator.xMin, elevator.zMin, elevator.xMax, elevator.zMax, elevator.getFloors().size(), elevator.isSpawned ? "" : "§c | Déchargé"), "info", "", elevator);
	}
	
	@Cmd (args = { "ELEVATOR", "INTEGER" }, min = 2, syntax = "<elevator id> <floor y>")
	public void addFloor(CommandContext cmd) {
		cmd.<Elevator>getArgument(0).addFloor(cmd.getArgument(1));
		sendSuccess("Vous avez ajouté un étage à l'ascenseur.");
	}
	
	@Cmd (player = true, args = { "ELEVATOR", "INTEGER", "UP|DOWN" }, min = 3, syntax = "<elevator id> <floor id> <button type>")
	public void setButton(CommandContext cmd) {
		Integer floorID = cmd.<Integer>getArgument(1);
		Floor floor = cmd.<Elevator>getArgument(0).getFloors().get(floorID);
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
	
	@Cmd (player = true, args = { "ELEVATOR", "INTEGER", "INTEGER" }, min = 3, syntax = "<elevator id> <floor id> <floor y>")
	public void setY(CommandContext cmd) {
		Integer floorID = cmd.<Integer>getArgument(1);
		Floor floor = cmd.<Elevator>getArgument(0).getFloors().get(floorID);
		if (floor == null) {
			sendError("Il n'y a pas d'étage avec l'ID %d.", floorID);
			return;
		}
		floor.setY(cmd.getArgument(2));
		sendSuccess("Tu as modifié la hauteur de l'étage.");
	}
	
	@Cmd (player = true, args = { "ELEVATOR" }, min = 2, syntax = "<elevator id> <block data>")
	public void setBlock(CommandContext cmd) {
		try {
			BlockData blockData = Bukkit.createBlockData(cmd.<String>getArgument(1));
			cmd.<Elevator>getArgument(0).setBlockData(blockData);
			sendSuccess("Tu as modifié la blockdata de l'ascenseur (%s)", blockData.getAsString());
		}catch (IllegalArgumentException ex) {
			sendError("La blockdata spécifiée est invalide.");
		}
	}
	
	@Cmd (player = true, args = { "ELEVATOR", "INTEGER" }, min = 2, syntax = "<elevator id> <speed>")
	public void setSpeed(CommandContext cmd) {
		Elevator elevator = cmd.getArgument(0);
		try {
			elevator.setSpeed(cmd.getArgument(1));
			sendSuccess("Tu as modifié la vitesse de l'ascenseur (%d).", elevator.getSpeed());
		}catch (IllegalArgumentException ex) {
			sendError("La vitesse spécifiée doit être supérieure ou égale à 1.");
		}
	}
	
}
