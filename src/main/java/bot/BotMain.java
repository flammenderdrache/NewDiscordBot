package bot;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.lifecycle.ReadyEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.rest.util.Color;
import bot.music.GuildMusicPlayer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

interface Command {
    Mono<Void> execute(MessageCreateEvent event);
}

public class BotMain {

    final static String PREFIX = "-";
    private static final Map<String, Command> commands = new HashMap<>();
    private static final Map<Long, GuildMusicPlayer> players = new ConcurrentHashMap<>();
    private static final BotMain bot = new BotMain();
    private final static long UPSINCE = System.currentTimeMillis();

    static {
        commands.put("ping", event -> event.getMessage().getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());

        commands.put("join", event -> Mono.justOrEmpty(event.getGuildId())
                .map(Snowflake::asLong)
                .flatMap(guildId -> event.getMessage().getChannel()
                        .flatMap(messageChannel -> Mono.justOrEmpty(event.getMember())
                                .flatMap(Member::getVoiceState)
                                .flatMap(VoiceState::getChannel)
                                .doOnNext(ignored -> players.putIfAbsent(guildId, new GuildMusicPlayer(Mono.just(messageChannel))))
                                .flatMap(voiceChannel -> voiceChannel.join(spec -> spec.setProvider(players.get(guildId).getProvider())))
                                .doOnNext(voiceConnection -> players.get(guildId).setVoiceConnection(Mono.just(voiceConnection)))
                        )
                )
                .then());

        commands.put("botinfo", event -> event.getMessage().getChannel()
                .flatMap(channel -> channel.createEmbed(spec -> spec.setColor(Color.ORANGE).setDescription("Uptime: " + TimeFormatter.generalTimeFormatter(System.currentTimeMillis() - UPSINCE))))
                .then());
    }

    public static void main(String[] args) {
        final DiscordClient client = DiscordClient.create(System.getenv("TOKEN"));
        final GatewayDiscordClient gateway = client.login().block();

        gateway.on(ReadyEvent.class)
                .subscribe(ready -> toConsole("Logged in as " + ready.getSelf().getUsername() + "#" + ready.getSelf().getDiscriminator()));

        gateway.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .flatMap(content -> Flux.fromIterable(commands.entrySet())
                                .filter(entry -> content.startsWith(PREFIX + entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(event))
                                .next()))
                .subscribe();


        gateway.onDisconnect().block();
    }

    public static void toConsole(String toConsole) {
        SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date();
        System.out.println(formatter.format(date) + " " + toConsole);
    }
}