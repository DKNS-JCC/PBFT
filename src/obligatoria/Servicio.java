package obligatoria;

import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.inject.Singleton;
import java.net.URI;

@Singleton
@Path("/servicio")
public class Servicio {

    private int nodoId = 0;
    private int procesosPorNodo = 2;
    private String[] nodos;
    private String clienteUrl;
    private Proceso[] procesos;

    public Servicio() {
        nodos = new String[] {
            "http://localhost:8080/PBFT/rest"
        };
        clienteUrl = "http://localhost:8080/PBFT/rest";
        int offset = nodoId * procesosPorNodo;
        procesos = new Proceso[] {
            new Proceso(offset + 0, 0, false, nodos, clienteUrl),
            new Proceso(offset + 1, 0, false, nodos, clienteUrl)
        };
    }

    @GET
    @Path("propuesta")
    @Produces(MediaType.TEXT_PLAIN)
    public String propuesta(@QueryParam("valor") int valor) {
        // Primero resetear todos, luego enviar compromisos
        for (int i = 0; i < procesos.length; i++) {
            procesos[i].resetear();
        }
        for (int i = 0; i < procesos.length; i++) {
            procesos[i].propuesta(valor);
        }
        return "ok";
    }

    @GET
    @Path("compromiso")
    @Produces(MediaType.TEXT_PLAIN)
    public String compromiso(@QueryParam("procesoId") int procesoId, @QueryParam("valor") int valor) {
        for (int i = 0; i < procesos.length; i++) {
            procesos[i].compromiso(procesoId, valor);
        }
        return "ok";
    }

    @GET
    @Path("comision")
    @Produces(MediaType.TEXT_PLAIN)
    public String comision(@QueryParam("procesoId") int procesoId, @QueryParam("valor") int valor) {
        for (int i = 0; i < procesos.length; i++) {
            procesos[i].comision(procesoId, valor);
        }
        return "ok";
    }

    @GET
    @Path("estado")
    @Produces(MediaType.TEXT_PLAIN)
    public String estado() {
        String resultado = "";
        for (int i = 0; i < procesos.length; i++) {
            resultado += procesos[i].estado() + "\n";
        }
        return resultado;
    }

    @GET
    @Path("error")
    @Produces(MediaType.TEXT_PLAIN)
    public String error(@QueryParam("procesoId") int procesoId, @QueryParam("error") boolean error) {
        procesos[procesoId].setError(error);
        return "ok";
    }

}

