package org.example.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class client extends JFrame {
    private final String clientId = "Cli-" + UUID.randomUUID().toString().substring(0, 4);
    private ShoppingCartServiceGrpc.ShoppingCartServiceBlockingStub stub;

    private JComboBox<String> comboProducts;
    private JSpinner spinQty;
    private DefaultTableModel cartModel;
    private JLabel lblTotal;

    private final List<ProductItem> catalog = new ArrayList<>();
    private final List<CartItem> cart = new ArrayList<>();

    public client() {
        setTitle("Carrito - " + clientId);
        setSize(480, 360);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        initGrpc();

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        comboProducts = new JComboBox<>();
        spinQty = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        JButton btnAdd = new JButton("Agregar");

        topPanel.add(comboProducts);
        topPanel.add(new JLabel("Cant:"));
        topPanel.add(spinQty);
        topPanel.add(btnAdd);
        add(topPanel, BorderLayout.NORTH);

        // Centro: Tabla del carrito
        String[] cols = {"Producto", "Cant.", "Subtotal"};
        cartModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        add(new JScrollPane(new JTable(cartModel)), BorderLayout.CENTER);

        // Panel Inferior: Acciones y total
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        lblTotal = new JLabel("Total: $0.00");
        lblTotal.setFont(new Font("Arial", Font.BOLD, 13));
        JButton btnClear = new JButton("Vaciar");
        JButton btnBuy = new JButton("Comprar");

        bottomPanel.add(lblTotal);
        bottomPanel.add(btnClear);
        bottomPanel.add(btnBuy);
        add(bottomPanel, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> addItem());
        btnClear.addActionListener(e -> clearCart());
        btnBuy.addActionListener(e -> buyOrder());

        loadCatalog();
    }

    private void initGrpc() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        stub = ShoppingCartServiceGrpc.newBlockingStub(channel);
    }

    private void loadCatalog() {
        try {
            CatalogResponse res = stub.getCatalog(Empty.newBuilder().build());
            catalog.clear();
            comboProducts.removeAllItems();
            for (ProductItem p : res.getProductsList()) {
                catalog.add(p);
                comboProducts.addItem(p.getName() + " ($" + p.getPrice() + ")");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Sin conexión al servidor: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addItem() {
        int idx = comboProducts.getSelectedIndex();
        if (idx < 0) return;

        ProductItem prod = catalog.get(idx);
        int qty = (Integer) spinQty.getValue();

        CartItem item = CartItem.newBuilder()
                .setProductId(prod.getId())
                .setProductName(prod.getName())
                .setPrice(prod.getPrice())
                .setQuantity(qty)
                .build();

        cart.add(item);
        cartModel.addRow(new Object[]{item.getProductName(), item.getQuantity(), "$" + (item.getPrice() * qty)});
        updateTotal();
    }

    private void clearCart() {
        cart.clear();
        cartModel.setRowCount(0);
        updateTotal();
    }

    private void updateTotal() {
        double total = 0.0;
        for (CartItem it : cart) total += it.getPrice() * it.getQuantity();
        lblTotal.setText(String.format("Total: $%.2f", total));
    }

    private void buyOrder() {
        try {
            OrderRequest.Builder req = OrderRequest.newBuilder().setClientId(clientId);
            for (CartItem it : cart) req.addItems(it);

            OrderResponse res = stub.processOrder(req.build());

            if (res.getSuccess()) {
                JOptionPane.showMessageDialog(this, res.getMessage(), "Éxito", JOptionPane.INFORMATION_MESSAGE);
                clearCart();
            } else {
                JOptionPane.showMessageDialog(this, res.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error de comunicación: " + e.getMessage(), "Falla", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new client().setVisible(true));
    }
}