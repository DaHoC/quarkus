package io.quarkus.deployment.ide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.quarkus.dev.console.DevConsoleManager;

public enum Ide {

    // see for cli syntax of idea https://www.jetbrains.com/help/idea/opening-files-from-command-line.html
    IDEA("idea", List.of("--line", "{lineNumber}", "{fileName}"), List.of("--help")),
    ECLIPSE("eclipse", List.of("--launcher.openFile", "{fileName}:{lineNumber}"), Collections.emptyList()),
    VSCODE("code", List.of("--goto", "{fileName}:{lineNumber}"), List.of("--version")),
    NETBEANS("netbeans", Collections.emptyList(), List.of("--help"));

    private final String defaultCommand;
    private final List<String> markerArgs;
    private final List<String> lineNumberArgs;
    private String machineSpecificCommand;

    private String effectiveCommand;

    Ide(String defaultCommand, List<String> lineNumberArgs, List<String> markerArgs) {
        this.defaultCommand = defaultCommand;
        this.lineNumberArgs = lineNumberArgs;
        this.markerArgs = markerArgs;
    }

    /**
     * Attempts to launch the default IDE script. If it succeeds, then that command is used (as the command is on the $PATH),
     * otherwise the full path of the command (determined earlier in the process by looking at the running processes)
     * is used.
     */
    public String getEffectiveCommand() {
        if (effectiveCommand != null) {
            return effectiveCommand;
        }
        effectiveCommand = doGetEffectiveCommand();
        return effectiveCommand;
    }

    private String doGetEffectiveCommand() {
        if (defaultCommand != null) {
            if (markerArgs.isEmpty()) {
                // in this case there is nothing much we can do but hope that the default command will work
                return defaultCommand;
            } else {
                try {
                    List<String> command = new ArrayList<>(1 + markerArgs.size());
                    command.add(defaultCommand);
                    command.addAll(markerArgs);
                    new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD.file())
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD.file()).start()
                            .waitFor(10,
                                    TimeUnit.SECONDS);
                    return defaultCommand;
                } catch (Exception e) {
                    return machineSpecificCommand;
                }
            }
        } else {
            // in this case the IDE does not provide a default command so we need to rely on what was found
            // from inspecting the running processes
            return machineSpecificCommand;
        }
    }

    public List<String> createFileOpeningArgs(String fileName, String line) {
        if (line == null || line.isEmpty()) {
            return Collections.singletonList(fileName);
        }

        if (lineNumberArgs.isEmpty()) {
            return Collections.singletonList(fileName + ":" + line);
        }

        return lineNumberArgs.stream()
                .map(arg -> arg.replace("{fileName}", fileName).replace("{lineNumber}", line))
                .collect(Collectors.toList());
    }

    public void setMachineSpecificCommand(String machineSpecificCommand) {
        this.machineSpecificCommand = machineSpecificCommand;
    }

    /**
     * Finds the location of a source file given the path from the source root
     *
     * @param fileName The file name
     * @return The path or null if it could not be found
     */
    public static Path findSourceFile(String fileName) {
        for (var i : DevConsoleManager.getHotReplacementContext().getSourcesDir()) {
            Path resolved = i.resolve(fileName);
            if (Files.exists(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Ide{" +
                "defaultCommand='" + defaultCommand + '\'' +
                '}';
    }
}
