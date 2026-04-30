package obligatoria;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Proceso extends Thread {

    //totales
    private static final int NUM_PROCESOS = 4;

    private int id;
    private int valor;
    private boolean error;
    private int[] compromisos;
    private int[] comisiones;
    private String[] nodos;
    private String clienteUrl;

    public Proceso(int id, int valor, boolean error, String[] nodos, String clienteUrl) {
        this.id = id;
        this.valor = valor;
        this.error = error;
        this.clienteUrl = clienteUrl;
        this.nodos = nodos;
        this.compromisos = new int[NUM_PROCESOS];
        this.comisiones = new int[NUM_PROCESOS];
        for (int i = 0; i < NUM_PROCESOS; i++) {
            compromisos[i] = -1;
            comisiones[i] = -1;
        }
    }

    public void setError(boolean error) {
        this.error = error;
        System.out.println("Proceso " + id + ": error = " + error);
    }

    public boolean getError() {
        return error;
    }

    public synchronized void resetear() {
        for (int i = 0; i < NUM_PROCESOS; i++) {
            compromisos[i] = -1;
            comisiones[i] = -1;
        }
    }

    // broadcast compromiso a todos los servicios
    public void propuesta(int v) {
        System.out.println("Proceso " + id + " propuesta: " + v);
        int error = new Random().nextInt(100);
        for (String nodo : nodos) {
            if (error) {
                Client client = ClientBuilder.newClient();
                client.target(nodo).path("servicio/compromiso").queryParam("valor", error).queryParam("procesoId", id).request(MediaType.TEXT_PLAIN).get();
                continue;
            }
            try {
                Client client = ClientBuilder.newClient();
                client.target(nodo).path("servicio/compromiso").queryParam("valor", v).queryParam("procesoId", id).request(MediaType.TEXT_PLAIN).get();
            } catch (Exception e) {
                System.out.println("Proceso " + id + ": error enviando compromiso a " + nodo);
            }
        }
    }

    // guarda y comprueba quorum
    public void compromiso(int procesoId, int v) {
        int valorQuorum = -1;

        synchronized (this) {
            compromisos[procesoId] = v;
            System.out.println("Proceso " + id + " recibio compromiso de " + procesoId + ": " + v);

            Map<Integer, Integer> contador = new HashMap<>();
            for (int i = 0; i < NUM_PROCESOS; i++) {
                if (compromisos[i] != -1) {
                    if (contador.containsKey(compromisos[i])) {
                        contador.put(compromisos[i], contador.get(compromisos[i]) + 1);
                    } else {
                        contador.put(compromisos[i], 1);
                    }
                }
            }

            for (Map.Entry<Integer, Integer> entry : contador.entrySet()) {
                if (entry.getValue() >= (NUM_PROCESOS / 2 + 1)) {
                    System.out.println("Proceso " + id + " detecta quorum en compromiso: " + entry.getKey());
                    valorQuorum = entry.getKey();
                    break;
                }
            }
        }

        // Enviar fuera del synchronized para evitar deadlock
        if (valorQuorum != -1) {
            for (String nodo : nodos) {
                try {
                    Client client = ClientBuilder.newClient();
                    client.target(nodo).path("servicio/comision").queryParam("procesoId", id).queryParam("valor", valorQuorum).request(MediaType.TEXT_PLAIN).get();
                } catch (Exception e) {
                    System.out.println("Error enviando comision desde proceso " + id);
                }
            }
        }
    }

    // guarda y comprueba quorum
    public void comision(int procesoId, int v) {
        int valorDecidido = -1;

        synchronized (this) {
            comisiones[procesoId] = v;
            System.out.println("Proceso " + id + " recibio comision de " + procesoId + ": " + v);

            Map<Integer, Integer> contador = new HashMap<>();
            for (int i = 0; i < NUM_PROCESOS; i++) {
                if (comisiones[i] != -1) {
                    if (contador.containsKey(comisiones[i])) {
                        contador.put(comisiones[i], contador.get(comisiones[i]) + 1);
                    } else {
                        contador.put(comisiones[i], 1);
                    }
                }
            }

            for (Map.Entry<Integer, Integer> entry : contador.entrySet()) {
                if (entry.getValue() >= (NUM_PROCESOS / 2 + 1)) {
                    System.out.println("Proceso " + id + " DECIDE valor: " + entry.getKey());
                    this.valor = entry.getKey(); //AQUI SE DECIDE EL VALOR FINAL
                    valorDecidido = entry.getKey();
                    break;
                }
            }
        }

        // Enviar fuera del synchronized para evitar deadlock
        if (valorDecidido != -1) {
            try {
                Client client = ClientBuilder.newClient();
                client.target(clienteUrl).path("servicio/confirmacion").queryParam("valor", valorDecidido).request(MediaType.TEXT_PLAIN).get();
            } catch (Exception e) {
                System.out.println("Error enviando confirmacion al cliente");
            }
        }
    }

    public void run() {}


    public synchronized String estado() {
        return id + "\t" + valor + "\t" + java.util.Arrays.toString(compromisos) + "\t" + error;
    }

}
