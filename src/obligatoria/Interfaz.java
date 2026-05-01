package obligatoria;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.List;

import javax.ws.rs.client.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

public class Interfaz extends JFrame {

	//Necesario para la interfaz
	private static final long serialVersionUID = 1L;

	private JPanel contentPane;
	private JTextField valor;
	private JTable table;
	private DefaultTableModel model;

	private static String[] nodos = new String[0];
	private static final int PROCESOS_POR_NODO = 2;

	public static void main(String[] args) {

		String inputIPs = JOptionPane.showInputDialog(null,
				"IP separadas por comas",
				"IP de Nodos",
				JOptionPane.PLAIN_MESSAGE);

		if (inputIPs == null || inputIPs.trim().isEmpty()) {
			System.out.println("Pon IPs cabezon");
			System.exit(0);
		}

		String[] ips = inputIPs.split(",");
		for (int i = 0; i < ips.length; i++) {
			ips[i] = "http://" + ips[i].trim() + "/PBFT/rest";
			ips[i] = ips[i].trim();
		}
		nodos = ips;

		Interfaz frame = new Interfaz();

		// Peticion inicial a cada nodo con la lista de nodos
		for (int i = 0; i < nodos.length; i++) {
			String nodo = nodos[i];
			try {
				Client client = ClientBuilder.newClient();
				URI uri = UriBuilder.fromUri(nodo).build();
				client.target(uri).path("servicio/iniciar").queryParam("nodos", String.join(",", nodos)).queryParam("nodoId", i).request(MediaType.TEXT_PLAIN).get(String.class);
				System.out.println("Nodo " + nodo + " iniciado correctamente");
			} catch (Exception e) {
				System.out.println("Error al iniciar nodo " + nodo + ": " + e.getMessage());
			}
		}

		frame.setVisible(true);
		frame.actualizarTabla();
	}

	public Interfaz() {

		setTitle("Practica Final PBFT");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 600, 300);

		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);

		GridBagLayout gbl_contentPane = new GridBagLayout();
		gbl_contentPane.columnWidths = new int[] { 80, 100, 100, 300 };
		gbl_contentPane.rowHeights = new int[] { 30, 150 };
		contentPane.setLayout(gbl_contentPane);

		JLabel lblNewLabel = new JLabel("Enviar valor:");
		GridBagConstraints gbc_lbl = new GridBagConstraints();
		gbc_lbl.insets = new Insets(0, 0, 5, 5);
		gbc_lbl.gridx = 0;
		gbc_lbl.gridy = 0;
		contentPane.add(lblNewLabel, gbc_lbl);

		valor = new JTextField();
		GridBagConstraints gbc_valor = new GridBagConstraints();
		gbc_valor.insets = new Insets(0, 0, 5, 5);
		gbc_valor.gridx = 1;
		gbc_valor.gridy = 0;
		gbc_valor.fill = GridBagConstraints.HORIZONTAL;
		gbc_valor.weightx = 1.0;
		contentPane.add(valor, gbc_valor);
		valor.setColumns(10);

		JButton btnEnviar = new JButton("Enviar");
		GridBagConstraints gbc_btn = new GridBagConstraints();
		gbc_btn.insets = new Insets(0, 0, 5, 5);
		gbc_btn.gridx = 2;
		gbc_btn.gridy = 0;
		contentPane.add(btnEnviar, gbc_btn);

		JButton btnRefrescar = new JButton("Refrescar");
		GridBagConstraints gbc_btnRefrescar = new GridBagConstraints();
		gbc_btnRefrescar.insets = new Insets(0, 0, 5, 5);
		gbc_btnRefrescar.gridx = 3;
		gbc_btnRefrescar.gridy = 0;
		contentPane.add(btnRefrescar, gbc_btnRefrescar);

		model = new DefaultTableModel(new Object[][] {}, new String[] { "ID", "VAR", "COMPROMISOS", "ERROR" }) {
			Class[] columnTypes = new Class[] { Integer.class, Integer.class, Object.class, Boolean.class };

			@Override
			public Class getColumnClass(int columnIndex) {
				return columnTypes[columnIndex];
			}

			@Override
			public boolean isCellEditable(int row, int column) {
				return column == 3;
			}
		};

		table = new JTable(model);

		JScrollPane scrollPane = new JScrollPane(table);
		GridBagConstraints gbc_scroll = new GridBagConstraints();
		gbc_scroll.gridx = 0;
		gbc_scroll.gridy = 1;
		gbc_scroll.gridwidth = 4;
		gbc_scroll.fill = GridBagConstraints.BOTH;
		contentPane.add(scrollPane, gbc_scroll);

		btnEnviar.addActionListener(e -> {
			String texto = valor.getText().trim();
			if (!texto.isEmpty()) {
				valor.setText("");
				for (String nodo : nodos) {
					new Thread() {
						public void run() {
							enviarPropuesta(nodo, texto);
						}
					}.start();
				}
			}
		});

		btnRefrescar.addActionListener(e -> actualizarTabla());
		
		model.addTableModelListener(e -> {
		      if (e.getColumn() == 3) {
		          int row = e.getFirstRow();
		          int id = (int) model.getValueAt(row, 0);
		          boolean error = (boolean) model.getValueAt(row, 3);
		          String nodo = nodos[id / PROCESOS_POR_NODO];
		          Client client = ClientBuilder.newClient();
		          URI uri = UriBuilder.fromUri(nodo).build();
		          client.target(uri).path("servicio/error").queryParam("procesoId", id).queryParam("error", error).request(MediaType.TEXT_PLAIN).get();
		      }
		 });

	}

	private void enviarPropuesta(String nodo, String valor) {
		try {
			Client client = ClientBuilder.newClient();
			URI uri = UriBuilder.fromUri(nodo).build();
			client.target(uri).path("servicio/propuesta").queryParam("valor", valor).request(MediaType.TEXT_PLAIN).get();
		} catch (Exception ex) {
			System.out.println("Error propuesta " + nodo + ": " + ex.getMessage());
		}
	}

	private void actualizarTabla() {
	      model.setRowCount(0);
	      for (String nodo : nodos) {
	          try {
	              Client client = ClientBuilder.newClient();
	              URI uri = UriBuilder.fromUri(nodo).build();
	              String estado = client.target(uri).path("servicio/estado").request(MediaType.TEXT_PLAIN).get(String.class);
	              for (String linea : estado.split("\n")) {
	                  if (linea.trim().isEmpty()) continue;
	                  String[] partes = linea.split("\t");
	                  model.addRow(new Object[] {
	                      Integer.parseInt(partes[0]),
	                      Integer.parseInt(partes[1]),
	                      partes[2],
	                      Boolean.parseBoolean(partes[3])
	                  });
	              }
	          } catch (Exception e) {
	              System.out.println("Error al obtener estado de " + nodo + ": " + e.getMessage());
	          }
	      }
	  }

}