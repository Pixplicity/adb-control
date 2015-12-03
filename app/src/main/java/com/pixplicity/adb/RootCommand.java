package com.pixplicity.adb;

public class RootCommand {

    public final String command;
    public final int maxLines;

    public RootCommand(String command) {
        this(command, 50);
    }

    public RootCommand(String command, int responseLines) {
        this.command = command;
        this.maxLines = responseLines;
    }

}
