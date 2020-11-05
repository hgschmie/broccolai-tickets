package broccolai.tickets.exceptions;

import broccolai.tickets.locale.Messages;

public final class TicketClosed extends PureException {

    /**
     * Exception for when a ticket is already closed
     */
    public TicketClosed() {
        super(Messages.EXCEPTIONS__TICKET_CLOSED);
    }

}
