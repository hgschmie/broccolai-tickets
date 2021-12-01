package broccolai.tickets.core.commands.command;

import broccolai.tickets.api.model.interaction.MessageInteraction;
import broccolai.tickets.api.model.ticket.Ticket;
import broccolai.tickets.api.model.ticket.TicketStatus;
import broccolai.tickets.api.model.user.OnlineUser;
import broccolai.tickets.api.model.user.PlayerUser;
import broccolai.tickets.api.service.modification.ModificationService;
import broccolai.tickets.api.service.message.MessageService;
import broccolai.tickets.api.service.storage.StorageService;
import broccolai.tickets.api.service.ticket.TicketService;
import broccolai.tickets.api.service.user.UserService;
import broccolai.tickets.core.commands.arguments.MessageArgument;
import broccolai.tickets.core.commands.arguments.TicketParserMode;
import broccolai.tickets.core.configuration.CommandsConfiguration;
import broccolai.tickets.core.configuration.MainConfiguration;
import broccolai.tickets.core.factory.CloudArgumentFactory;
import broccolai.tickets.core.utilities.Constants;
import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.EnumArgument;
import cloud.commandframework.context.CommandContext;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;

public final class TicketCommand extends CommonCommands {

    private final CommandsConfiguration.TicketConfiguration config;
    private final CloudArgumentFactory argumentFactory;
    private final MessageService messageService;
    private final TicketService ticketService;
    private final ModificationService modificationService;

    @Inject
    public TicketCommand(
            final @NonNull MainConfiguration config,
            final @NonNull CloudArgumentFactory argumentFactory,
            final @NonNull MessageService messageService,
            final @NonNull StorageService storageService,
            final @NonNull TicketService ticketService,
            final @NonNull ModificationService modificationService,
            final @NonNull UserService userService
    ) {
        super(messageService, storageService, userService);
        this.config = config.commandsConfiguration.ticket;
        this.argumentFactory = argumentFactory;
        this.messageService = messageService;
        this.ticketService = ticketService;
        this.modificationService = modificationService;
    }

    @Override
    public void register(
            final @NonNull CommandManager<@NonNull OnlineUser> manager
    ) {
        final Command.Builder<OnlineUser> builder = manager.commandBuilder("ticket", "ti")
                .senderType(PlayerUser.class);

        manager.command(builder.literal(
                                this.config.create.main,
                                ArgumentDescription.of("Create a ticket"),
                                this.config.create.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".create")
                        .argument(MessageArgument.of("message"))
                        .handler(this::processCreate)
        );

        manager.command(builder.literal(
                                this.config.update.main,
                                ArgumentDescription.of("Update a ticket"),
                                this.config.update.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".update")
                        .argument(this.argumentFactory.ticket(
                                "ticket", TicketParserMode.SENDERS,
                                EnumSet.of(TicketStatus.OPEN, TicketStatus.CLAIMED),
                                0
                        ))
                        .argument(MessageArgument.of("message"))
                        .handler(this::processUpdate)
        );

        manager.command(builder.literal(
                                this.config.close.main,
                                ArgumentDescription.of("Close a ticket"),
                                this.config.close.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".close")
                        .argument(this.argumentFactory.ticket(
                                "ticket",
                                TicketParserMode.SENDERS,
                                EnumSet.of(TicketStatus.OPEN, TicketStatus.CLAIMED),
                                0
                        ))
                        .handler(this::processClose)
        );

        manager.command(builder.literal(
                                this.config.list.main,
                                ArgumentDescription.of("List tickets"),
                                this.config.list.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".list")
                        .flag(manager.flagBuilder("status")
                                .withArgument(EnumArgument.of(TicketStatus.class, "status")))
                        .handler(this::processList)
        );

        manager.command(builder.literal(
                                this.config.show.main,
                                ArgumentDescription.of("Show a ticket"),
                                this.config.show.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".show")
                        .argument(this.argumentFactory.ticket("ticket", TicketParserMode.SENDERS, EnumSet.allOf(TicketStatus.class), 0))
                        .handler(c -> processShow(c.getSender(), c.get("ticket")))
        );

        manager.command(builder.literal(
                                this.config.log.main,
                                ArgumentDescription.of("View a tickets log"),
                                this.config.log.aliases
                        )
                        .permission(Constants.USER_PERMISSION + ".log")
                        .argument(this.argumentFactory.ticket("ticket", TicketParserMode.SENDERS, EnumSet.allOf(TicketStatus.class), 0))
                        .handler(c -> processLog(c.getSender(), c.get("ticket")))
        );
    }

    private void processCreate(final @NonNull CommandContext<OnlineUser> c) {
        PlayerUser soul = (PlayerUser) c.getSender();

        this.modificationService.create(soul, c.get("message"));
    }

    private void processUpdate(final @NonNull CommandContext<OnlineUser> c) {
        PlayerUser soul = (PlayerUser) c.getSender();
        Ticket ticket = c.get("ticket");
        MessageInteraction message = c.get("message");

        this.modificationService.update(soul, ticket, message);
    }

    private void processClose(final @NonNull CommandContext<OnlineUser> c) {
        PlayerUser soul = (PlayerUser) c.getSender();
        Ticket ticket = c.get("ticket");

        this.modificationService.close(soul, ticket);
    }

    private void processList(final @NonNull CommandContext<OnlineUser> c) {
        OnlineUser soul = c.getSender();
        Collection<Ticket> tickets = this.ticketService.get(
                soul,
                TicketStatus.from(c.flags())
        );

        List<Component> results = new ArrayList<>();
        results.add(Component.empty());
        results.add(this.messageService.listTitlePersonal());

        for (final Ticket ticket : tickets) {
            results.add(this.messageService.listTitleEntry(ticket));
        }

        soul.sendMessage(Component.join(
                Component.newline(),
                results
        ));
    }

}
