package com.redislabs.riot.gen;

import com.github.javafaker.Faker;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.IHelpCommandInitializable2;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "faker-help", header = "Displays help information about Faker", synopsisHeading = "%nUsage: ", helpCommand = true)
public class FakerHelpCommand implements IHelpCommandInitializable2, Runnable {

    private final static List<String> EXCLUDES = Arrays.asList("instance", "options");

    private PrintWriter outWriter;

    public void run() {
        for (Method method : Faker.class.getDeclaredMethods()) {
            if (EXCLUDES.contains(method.getName())) {
                continue;
            }
            if (!method.getReturnType().getPackage().equals(Faker.class.getPackage())) {
                continue;
            }
            List<String> names = Arrays.stream(method.getReturnType().getDeclaredMethods()).filter(m -> m.getParameters().length == 0).map(Method::getName).collect(Collectors.toList());
            outWriter.println(String.format("%-30.30s: %s", method.getName(), String.join(" ", names)));
        }
    }

    public void init(CommandLine helpCommandLine, Help.ColorScheme colorScheme, PrintWriter out, PrintWriter err) {
        this.outWriter = notNull(out, "outWriter");
    }

    static <T> T notNull(T object, String description) {
        if (object == null) {
            throw new NullPointerException(description);
        }
        return object;
    }

}
