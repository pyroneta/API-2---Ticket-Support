package Service;

import Client.UsuarioClient;
import Model.Ticket;
import Proxy.TicketProxy;

import java.util.Date;

public class TicketService {

    private final UsuarioClient usuarioClient;
    private final TicketProxy ticketProxy;

    public TicketService() {
        this.usuarioClient = new UsuarioClient();
        this.ticketProxy = new TicketProxy();
    }

    public Ticket crearTicket(Ticket ticket) throws Exception {



        return ticketProxy.enviarTicket(ticket);
    }


    public String listarTickets() throws Exception {
        return ticketProxy.obtenerTickets();
    }

}
