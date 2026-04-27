package obligatoria;

import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import javax.inject.Singleton;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Path("/servicio")
public class Servicio {

    private static final int NUM_PROCESOS = 4;
    private int nodoId = 0;
    private int procesosPorNodo = 2;
    private String[] nodos;
    private String clienteUrl;
    private Proceso[] procesos;
    private final Map<Integer, Integer> confirmaciones = new HashMap<>();

    public Servicio() {
        nodos = new String[] {
            "http://localhost:8080/PBFT/rest",
            "http://172.28.142.255:8080/PBFT/rest"
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
        synchronized (this) { confirmaciones.clear(); }
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
    	int offset = nodoId * procesosPorNodo;
    	procesos[procesoId - offset].setError(error);
        return "ok";
    }

    @GET
    @Path("confirmacion")
    @Produces(MediaType.TEXT_PLAIN)
    public String confirmacion(@QueryParam("valor") int valor) {
        synchronized (this) {
            confirmaciones.merge(valor, 1, Integer::sum);
            if (confirmaciones.get(valor) >= (NUM_PROCESOS / 2 + 1)) {
                System.out.println("Consenso confirmado: valor " + valor);
            }
        }
        return "ok";
    }

}

