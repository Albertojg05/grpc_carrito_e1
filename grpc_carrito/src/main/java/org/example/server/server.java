package org.example.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.example.grpc.Empty;
import org.example.grpc.ProductItem;
import org.example.grpc.CatalogResponse;
import org.example.grpc.CartItem;
import org.example.grpc.OrderRequest;
import org.example.grpc.OrderResponse;
import org.example.grpc.ShoppingCartServiceGrpc;


import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class server extends JFrame {
    private final DefaultTableModel tableModel;
    private final JTextArea logArea;

    public static class InventoryProduct {
        public String id, name;
        public double price;
        public int stock;

        public InventoryProduct(String id, String name, double price, int stock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.stock = stock;
        }
    }

    private final Map<String, InventoryProduct> inventory = new ConcurrentHashMap<>();

    public server() {
        setTitle("Servidor - Almacén");
        setSize(520, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(6, 6));

        inventory.put("P1", new InventoryProduct("P1", "Laptop Gamer", 15000.0, 5));
        inventory.put("P2", new InventoryProduct("P2", "Mouse Inalámbrico", 450.0, 10));
        inventory.put("P3", new InventoryProduct("P3", "Teclado Mecánico", 1200.0, 8));
        inventory.put("P4", new InventoryProduct("P4", "Monitor 144Hz", 4200.0, 4));

        String[] cols = {"ID", "Producto", "Precio", "Stock"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        updateTable();

        JTable table = new JTable(tableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(500, 160));
        add(tableScroll, BorderLayout.CENTER);

        logArea = new JTextArea(8, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Eventos y peticiones"));
        add(logScroll, BorderLayout.SOUTH);

        startGrpcServer(9090);
    }

    private void updateTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            for (InventoryProduct p : inventory.values()) {
                tableModel.addRow(new Object[]{p.id, p.name, "$" + p.price, p.stock});
            }
        });
    }

    private void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void startGrpcServer(int port) {
        new Thread(() -> {
            try {
                Server server = ServerBuilder.forPort(port)
                        .addService(new ShoppingCartServiceImpl())
                        .build()
                        .start();
                log("Servidor iniciado en el puerto " + port);
                server.awaitTermination();
            } catch (Exception e) {
                log("Error al iniciar el servidor: " + e.getMessage());
            }
        }).start();
    }

    private class ShoppingCartServiceImpl extends ShoppingCartServiceGrpc.ShoppingCartServiceImplBase {

        @Override
        public void getCatalog(Empty request, StreamObserver<CatalogResponse> responseObserver) {
            CatalogResponse.Builder catalog = CatalogResponse.newBuilder();
            for (InventoryProduct p : inventory.values()) {
                catalog.addProducts(ProductItem.newBuilder()
                        .setId(p.id)
                        .setName(p.name)
                        .setPrice(p.price)
                        .build());
            }
            log("Catálogo consultado remotamente.");
            responseObserver.onNext(catalog.build());
            responseObserver.onCompleted();
        }

        @Override
        public void processOrder(OrderRequest request, StreamObserver<OrderResponse> responseObserver) {
            OrderResponse.Builder res = OrderResponse.newBuilder();
            String client = request.getClientId();

            if (request.getItemsCount() == 0) {
                log(client + ": RECHAZADO -> Carrito vacío recibido.");
                res.setSuccess(false).setMessage("El carrito está vacío.");
                responseObserver.onNext(res.build());
                responseObserver.onCompleted();
                return;
            }

            for (CartItem item : request.getItemsList()) {
                if (item.getQuantity() <= 0 || item.getPrice() <= 0) {
                    log(client + ": RECHAZADO -> Parámetros inválidos en " + item.getProductName());
                    res.setSuccess(false).setMessage("Cantidad o precio inválido en: " + item.getProductName());
                    responseObserver.onNext(res.build());
                    responseObserver.onCompleted();
                    return;
                }
            }

            synchronized (inventory) {
                for (CartItem item : request.getItemsList()) {
                    InventoryProduct p = inventory.get(item.getProductId());
                    if (p == null || p.stock < item.getQuantity()) {
                        String prodName = (p != null ? p.name : item.getProductName());
                        int stockDisp = (p != null ? p.stock : 0);
                        String errMsg = "Stock insuficiente para '" + prodName +
                                "' (Disponible: " + stockDisp + ", Solicitado: " + item.getQuantity() + ")";

                        log(client + ": ORDEN CANCELADA -> " + errMsg);
                        res.setSuccess(false).setMessage(errMsg);
                        responseObserver.onNext(res.build());
                        responseObserver.onCompleted();
                        return;
                    }
                }

                for (CartItem item : request.getItemsList()) {
                    inventory.get(item.getProductId()).stock -= item.getQuantity();
                }

                updateTable();
            }

            log(client + ": ÉXITO -> Compra procesada (" + request.getItemsCount() + " ítems). Inventario actualizado.");
            res.setSuccess(true).setMessage("¡Compra procesada con éxito!");
            responseObserver.onNext(res.build());
            responseObserver.onCompleted();
        }
    }
}
