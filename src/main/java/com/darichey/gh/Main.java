package com.darichey.gh;

import java.nio.file.Paths;
import java.util.Optional;

public final class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Must provide a config path.");
            System.exit(-1);
        }

        Optional<BotConfig> cfg = BotConfig.fromFile(Paths.get(args[0]));
        if (cfg.isEmpty()) {
            System.err.println("Could not find valid config at " + args[0]);
            System.exit(-1);
        } else {
            new GithubBot().execute(cfg.get()).block();
        }
    }
}
