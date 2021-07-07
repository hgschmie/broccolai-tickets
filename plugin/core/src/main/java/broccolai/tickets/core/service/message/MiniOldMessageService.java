package broccolai.tickets.core.service.message;

import broccolai.corn.core.Lists;
import broccolai.tickets.api.model.interaction.Interaction;
import broccolai.tickets.api.model.interaction.MessageInteraction;
import broccolai.tickets.api.model.ticket.Ticket;
import broccolai.tickets.api.model.user.Soul;
import broccolai.tickets.api.service.message.OldMessageService;
import broccolai.tickets.api.service.template.TemplateService;
import broccolai.tickets.api.service.user.UserService;
import broccolai.tickets.core.configuration.LocaleConfiguration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.Template;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jetbrains.annotations.NotNull;

@Singleton
public final class MiniOldMessageService implements OldMessageService {

    private final LocaleConfiguration locale;
    private final UserService userService;
    private final TemplateService templateService;

    private final Template prefix;

    @Inject
    public MiniOldMessageService(
            final @NonNull LocaleConfiguration locale,
            final @NonNull UserService userService,
            final @NonNull TemplateService templateService
    ) {
        this.locale = locale;
        this.userService = userService;
        this.templateService = templateService;
        this.prefix = Template.of("prefix", this.locale.prefix.use());
    }

    @Override
    public @NotNull Component commandsTicketList(final @NonNull Collection<@NonNull Ticket> tickets) {
        Template wrapper = Template.of("wrapper", this.locale.title.wrapper.use());

        TextComponent.Builder builder = Component.text()
                .append(this.locale.title.yourTickets.use(Collections.singletonList(wrapper)));

        List<Ticket> sortedTickets = new ArrayList<>(tickets);
        sortedTickets.sort(Comparator.comparingInt(Ticket::id));

        sortedTickets.forEach(ticket -> {
            List<Template> templates = new ArrayList<>(this.templateService.ticket(ticket));
            templates.add(this.prefix);

            Component list = this.locale.format.list.use(templates);
            builder.append(Component.newline(), list);
        });

        return this.padComponent(builder.build());
    }

    @Override
    public @NotNull Component commandsTicketsList(final @NonNull Map<@NonNull UUID, @NonNull Collection<@NonNull Ticket>> map) {
        Template wrapper = Template.of("wrapper", this.locale.title.wrapper.use());

        TextComponent.Builder builder = Component.text()
                .append(this.locale.title.allTickets.use(Collections.singletonList(wrapper)));

        map.forEach((uuid, tickets) -> {
            Soul soul = this.userService.wrap(uuid);

            builder.append(Component.newline());
            builder.append(this.locale.format.listHeader.use(this.templateService.player("player", soul)));

            List<Ticket> sortedTickets = new ArrayList<>(tickets);
            sortedTickets.sort(Comparator.comparingInt(Ticket::id));

            for (final Ticket ticket : sortedTickets) {
                List<Template> templates = new ArrayList<>(this.templateService.ticket(ticket));
                templates.add(this.prefix);

                Component list = this.locale.format.list.use(templates);
                builder.append(Component.newline(), list);
            }
        });

        return this.padComponent(builder.build());
    }

    @Override
    public @NotNull Component commandsHighscore(final @NonNull Map<UUID, Integer> ranks) {
        Template wrapper = Template.of("wrapper", this.locale.title.wrapper.use());

        TextComponent.Builder builder = Component.text()
                .append(this.locale.title.highscores.use(Collections.singletonList(wrapper)), Component.newline());

        List<Component> entries = Lists.map(ranks.entrySet(), entry -> {
            Soul soul = this.userService.wrap(entry.getKey());
            List<Template> templates = new ArrayList<>(this.templateService.player("player", soul));
            templates.add(Template.of("amount", entry.getValue().toString()));

            return this.locale.format.hs.use(templates);
        });

        return builder.append(Component.join(
                Component.newline(),
                entries
        )).build();
    }

    @Override
    public @NotNull Component commandsLog(final @NonNull Collection<Interaction> interactions) {
        DateTimeFormatter formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
        Template wrapper = Template.of("wrapper", this.locale.title.wrapper.use());

        TextComponent.Builder builder = Component.text()
                .append(this.locale.title.log.use(Collections.singletonList(wrapper)), Component.newline());

        List<Component> entries = Lists.map(interactions, interaction -> {
            Soul soul = this.userService.wrap(interaction.sender());
            List<Template> templates = new ArrayList<>(this.templateService.player("player", soul));
            templates.add(Template.of("action", interaction.action().name()));

            Component hoverComponent = Component.text("Time: " + formatter.format(interaction.time()));
            hoverComponent = hoverComponent.append(Component.newline());

            if (interaction instanceof MessageInteraction messageInteraction) {
                hoverComponent = hoverComponent.append(Component.text("Message: " + messageInteraction.message()));
            }

            return this.locale.format.log.use(templates).hoverEvent(HoverEvent.showText(hoverComponent));
        });

        return builder.append(Component.join(
                Component.newline(),
                entries
        )).build();
    }

    @Override
    public @NotNull Component showTicket(final @NonNull Ticket ticket) {
        List<Template> templates = new ArrayList<>();
        templates.add(this.prefix);
        templates.add(Template.of("wrapper", this.locale.title.wrapper.use()));
        templates.addAll(this.templateService.ticket(ticket));

        Optional<UUID> claimer = ticket.claimer();
        Component component;

        if (claimer.isPresent()) {
            Soul soul = this.userService.wrap(claimer.get());
            templates.addAll(this.templateService.player("claimer", soul));

            component = this.locale.show.claimed.use(templates);
        } else {
            component = this.locale.show.unclaimed.use(templates);
        }

        return this.padComponent(Component.join(
                Component.newline(),
                this.locale.title.showTicket.use(templates),
                this.locale.show.status.use(templates),
                this.locale.show.player.use(templates),
                this.locale.show.position.use(templates),
                component,
                this.locale.show.message.use(templates)
        ));
    }

    private Component padComponent(final @NonNull Component component) {
        return TextComponent.ofChildren(
                Component.newline(),
                component,
                Component.newline()
        );
    }

}
