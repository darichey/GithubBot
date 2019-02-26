package com.darichey.gh;

import discord4j.common.json.MessageResponse;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.ServiceMediator;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Activity;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.util.Snowflake;
import discord4j.rest.json.request.MessageCreateRequest;
import discord4j.rest.util.MultipartRequest;
import discord4j.store.api.noop.NoOpStoreService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GithubBot {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\.gh\\s+(\\d+)");

    Mono<Void> execute(BotConfig config) {
        DiscordClient client = new DiscordClientBuilder(config.getToken())
                .setStoreService(new NoOpStoreService())
                .setInitialPresence(Presence.idle(Activity.playing("Starting up...")))
                .build();

        Mono<Void> login = client.login();
        Mono<Void> handleEvents = handleEvents(client, config);

        return login.and(handleEvents);
    }

    private Mono<Void> handleEvents(DiscordClient client, BotConfig config) {
        ServiceMediator serviceMediator = getServiceMediator(client);

        Mono<Void> handleMessages = client.getEventDispatcher().on(MessageCreateEvent.class)
                .filter(evt -> evt.getGuildId()
                        .map(Snowflake::asLong)
                        .map(config.getAllowedGuilds()::contains)
                        .orElse(true)) // allow DMs
                .flatMap(evt -> {
                    long channel = evt.getMessage().getChannelId().asLong();

                    return Mono.justOrEmpty(evt.getMessage().getContent())
                            .flatMapMany(c -> Flux.fromStream(COMMAND_PATTERN.matcher(c).results()))
                            .map(res -> res.group(1))
                            .distinct()
                            .map(issueNum -> getUrl(config.getRepo(), issueNum))
                            .collect(Collectors.joining("\n"))
                            .filter(responseContent -> !(responseContent.isBlank() || responseContent.length() > 2000))
                            .flatMap(responseContent -> sendMessage(serviceMediator, channel, responseContent));
                })
                .onErrorContinue((t, o) -> {})
                .then();

        Mono<Void> handleReady = client.getEventDispatcher().on(ReadyEvent.class)
                .flatMap(it -> client.updatePresence(Presence.online()))
                .then();

        return handleMessages.and(handleReady);
    }

    private Mono<MessageResponse> sendMessage(ServiceMediator serviceMediator, long channel, String content) {
        MultipartRequest request = new MultipartRequest(new MessageCreateRequest(content, null, false, null));
        return serviceMediator.getRestClient().getChannelService().createMessage(channel, request);
    }

    private static String getUrl(String repo, String issue) {
        return String.format("https://github.com/%s/issues/%s", repo, issue);
    }

    private static ServiceMediator getServiceMediator(DiscordClient client) {
        try {
            Field field = DiscordClient.class.getDeclaredField("serviceMediator");
            field.setAccessible(true);
            return (ServiceMediator) field.get(client);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
