package com.darichey.gh;

import discord4j.common.json.MessageResponse;
import discord4j.core.CoreResources;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.presence.Presence;
import discord4j.core.object.util.Snowflake;
import discord4j.rest.json.request.MessageCreateRequest;
import discord4j.rest.util.MultipartRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GithubBot {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("\\.gh\\s+(\\d+)");

    Mono<Void> execute(BotConfig config) {
        return DiscordClient.create(config.getToken())
                .withGateway(gatewayClient ->
                        handleEvents(gatewayClient, config));
    }

    private Mono<Void> handleEvents(GatewayDiscordClient client, BotConfig config) {
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
                            .flatMap(responseContent -> sendMessage(client.getCoreResources(), channel, responseContent));
                })
                .onErrorContinue((t, o) -> {
                })
                .then();

        Mono<Void> handleReady = client.getEventDispatcher().on(ReadyEvent.class)
                .flatMap(it -> client.updatePresence(0, Presence.online()))
                .then();

        return handleMessages.and(handleReady);
    }

    private Mono<MessageResponse> sendMessage(CoreResources serviceMediator, long channel, String content) {
        MultipartRequest request = new MultipartRequest(new MessageCreateRequest(content, null, false, null));
        return serviceMediator.getRestClient().getChannelService().createMessage(channel, request);
    }

    private static String getUrl(String repo, String issue) {
        return String.format("https://github.com/%s/issues/%s", repo, issue);
    }
}
