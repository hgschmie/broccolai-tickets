package broccolai.tickets.api.service.storage;

import broccolai.tickets.api.model.interaction.Interaction;
import broccolai.tickets.api.model.interaction.MessageInteraction;
import broccolai.tickets.api.model.position.Position;
import broccolai.tickets.api.model.service.Disposable;
import broccolai.tickets.api.model.ticket.Ticket;
import broccolai.tickets.api.model.ticket.TicketStatus;
import broccolai.tickets.api.model.user.Soul;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.Map;

public interface StorageService extends Disposable {

    int create(@NonNull Soul soul, @NonNull Position position, @NonNull MessageInteraction messageInteraction);

    @NonNull Map<Integer, Ticket> tickets(@NonNull Collection<@NonNull Integer> ids);

    @NonNull Map<Integer, Ticket> tickets(@NonNull Soul soul, @NonNull Collection<TicketStatus> statuses);

    int countTickets(@NonNull Collection<TicketStatus> statuses);

    void saveInteractions(@NonNull Collection<Interaction> interactions);

    @NonNull Collection<Component> notifications(@NonNull Soul soul);

}
