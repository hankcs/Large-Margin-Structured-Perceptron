package br.pucrio.inf.learn.structlearning.discriminative.driver;

import java.util.LinkedList;
import java.util.List;

/**
 * Command-line driver for the structural learning project.
 * 
 * This program (driver) expects as a first argument the name of a command. The
 * list of commands is filled in the main method. If the program is invoked
 * without any argument, it shows the list of available commands.
 * 
 * This driver also accepts JVM arguments from the command line. These arguments
 * are parsed and removed from the argument list before calling the command
 * class.
 * 
 * @author eraldof
 * 
 */
public class Driver {

	private static List<CommandDescription> descriptions;

	public static void main(String[] args) {
		descriptions = new LinkedList<CommandDescription>();

		// Trainer for the Structural Perceptron.
		descriptions.add(new CommandDescription(new TrainHmmMain(), "TrainHmm",
				"Train an HMM using the Structural Perceptron algorithm."));

		// Test JNI.
		descriptions.add(new CommandDescription(new TrainNativeSvm(), "JNITest",
				"Test JNI."));

		if (args.length < 1) {
			usage();
			System.exit(1);
		}

		run(args);
	}

	private static void run(String[] args) {
		String commandName = args[0];
		args = removeFirstArg(args);
		for (CommandDescription descr : descriptions) {
			if (descr.name.equals(commandName)) {
				descr.command.run(args);
				return;
			}
		}

		// Invalid command.
		usage();
		System.exit(1);
	}

	private static String[] removeFirstArg(String[] args) {
		String[] newArgs = new String[args.length - 1];
		for (int idx = 0; idx < args.length - 1; ++idx)
			newArgs[idx] = args[idx + 1];
		return newArgs;
	}

	private static void usage() {
		System.out.println();
		System.out.println("Valid commands:");
		System.out.println();
		for (CommandDescription descr : descriptions) {
			System.out.println(" * " + descr.name);
			System.out.println("\t" + descr.description);
			System.out.println();
		}
	}

	public static interface Command {

		public void run(String[] args);

	}

	private static class CommandDescription {
		public Command command;
		public String name;
		public String description;

		public CommandDescription(Command command, String name,
				String description) {
			this.command = command;
			this.name = name;
			this.description = description;
		}
	}
}