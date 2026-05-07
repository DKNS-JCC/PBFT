package obligatoria;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
@Path("/servicio")
public class Servicio {

    private int nodoId;
    private int procesosPorNodo = 2;
    private String[] nodos;
    private Proceso[] procesos = new Proceso[0];
    private final Map<Integer, Integer> confirmaciones = new HashMap<>();

    public Servicio() {
    }

    @GET
    @Path("iniciar")
    @Produces(MediaType.TEXT_PLAIN)
    public String iniciar(@QueryParam("nodos") String nodosStr, @QueryParam("nodoId") int nodoIdParam) {
        this.nodoId = nodoIdParam;
        this.nodos = nodosStr.split(",");
        System.out.println("Servicio iniciado con nodoId " + this.nodoId + " y nodos: " + nodosStr);
        // Reinicializar los procesos con los nodos recibidos
        int offset = this.nodoId * procesosPorNodo;
        procesos = new Proceso[] {
                new Proceso(offset + 0, 0, false, nodos),
                new Proceso(offset + 1, 0, false, nodos)
        };
        return "ok";
    }

    @GET
    @Path("propuesta")
    @Produces(MediaType.TEXT_PLAIN)
    public String propuesta(@QueryParam("valor") int valor) {
        synchronized (this) {
            confirmaciones.clear();
        }
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
    public void confirmacion(@QueryParam("valor") int valor) {
        synchronized (confirmaciones) {
            confirmaciones.put(valor, confirmaciones.getOrDefault(valor, 0) + 1);
        }
        int totalConfirmaciones = nodos.length * procesosPorNodo;
        if (confirmaciones.getOrDefault(valor, 0) >= totalConfirmaciones) {
            return;
        } else {
            return;
        }
    }

    @GET
    @Path("estadoConfirmaciones")
    @Produces(MediaType.TEXT_PLAIN)
    public String estadoConfirmaciones() {
        if (confirmaciones.isEmpty()) {
            return "No hay confirmaciones";
        } else {
            if (confirmaciones.size() >= nodos.length * procesosPorNodo) {
                return "Todos los procesos han acabado ok";
            }
        }
        return "Aún no";
    }

}
