package com.darichey.gh;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

final class BotConfig {

    static Optional<BotConfig> fromFile(Path path) {
        try {
            return Optional.of(new ObjectMapper().readValue(path.toFile(), BotConfig.class));
        } catch (IOException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private final String token;
    private final String repo;
    private final Set<Long> allowedGuilds;

    public BotConfig(@JsonProperty("token") String token, @JsonProperty("repo") String repo, @JsonProperty("allowedGuilds") Set<Long> allowedGuilds) {
        this.token = token;
        this.repo = repo;
        this.allowedGuilds = allowedGuilds;
    }

    String getToken() {
        return token;
    }

    String getRepo() {
        return repo;
    }

    Set<Long> getAllowedGuilds() {
        return allowedGuilds;
    }
}
