package org.example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Business rules for the shop, kept independent from console input/output. */
public class ShopService {
    private final Map<Integer, Product> products = new LinkedHashMap<>();
    private final Map<Integer, Customer> customers = new LinkedHashMap<>();
    private final Map<Integer, Order> orders = new LinkedHashMap<>();
    private int nextProductId = 1, nextCustomerId = 1, nextOrderId = 1;

    public Product addProduct(String name, BigDecimal price, int stock) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("商品名稱不可空白");
        if (price == null || price.signum() <= 0) throw new IllegalArgumentException("售價必須大於 0");
        if (stock < 0) throw new IllegalArgumentException("庫存不可小於 0");
        Product p = new Product(nextProductId++, name.trim(), price.setScale(2, RoundingMode.HALF_UP), stock);
        products.put(p.id(), p);
        return p;
    }

    public Customer addCustomer(String name, String email, String phone) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("顧客姓名不可空白");
        if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new IllegalArgumentException("電子郵件格式不正確");
        if (customers.values().stream().anyMatch(c -> c.email().equalsIgnoreCase(email)))
            throw new IllegalArgumentException("此電子郵件已註冊");
        Customer c = new Customer(nextCustomerId++, name.trim(), email.trim(), phone == null ? "" : phone.trim());
        customers.put(c.id(), c);
        return c;
    }

    /** Creates an order atomically: invalid requests never consume stock. */
    public Order placeOrder(int customerId, Map<Integer, Integer> requestedItems) {
        if (!customers.containsKey(customerId)) throw new IllegalArgumentException("找不到顧客");
        if (requestedItems == null || requestedItems.isEmpty()) throw new IllegalArgumentException("訂單至少需要一項商品");
        Map<Integer, Integer> items = new LinkedHashMap<>(requestedItems);
        for (var item : items.entrySet()) {
            Product p = products.get(item.getKey());
            if (p == null) throw new IllegalArgumentException("找不到商品：" + item.getKey());
            if (item.getValue() == null || item.getValue() <= 0) throw new IllegalArgumentException("購買數量必須大於 0");
            if (p.stock() < item.getValue()) throw new IllegalArgumentException("商品庫存不足：" + p.name());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (var item : items.entrySet()) {
            Product p = products.get(item.getKey());
            total = total.add(p.price().multiply(BigDecimal.valueOf(item.getValue())));
        }
        items.forEach((id, qty) -> products.put(id, products.get(id).withStock(products.get(id).stock() - qty)));
        Order order = new Order(nextOrderId++, customerId, Map.copyOf(items),
                total.setScale(2, RoundingMode.HALF_UP), LocalDateTime.now(), OrderStatus.PROCESSING);
        orders.put(order.id(), order);
        return order;
    }

    public Order changeOrderStatus(int orderId, OrderStatus status) {
        Order old = orders.get(orderId);
        if (old == null) throw new IllegalArgumentException("找不到訂單");
        if (status == null) throw new IllegalArgumentException("訂單狀態不可為空");
        if (old.status() == OrderStatus.CANCELLED) throw new IllegalStateException("已取消的訂單不能再變更");
        if (status == OrderStatus.CANCELLED) {
            old.items().forEach((id, qty) -> {
                Product p = products.get(id);
                products.put(id, p.withStock(Math.addExact(p.stock(), qty)));
            });
        }
        Order updated = old.withStatus(status);
        orders.put(orderId, updated);
        return updated;
    }

    public Product restock(int productId, int amount) {
        Product p = requireProduct(productId);
        if (amount <= 0) throw new IllegalArgumentException("補貨數量必須大於 0");
        Product updated = p.withStock(Math.addExact(p.stock(), amount));
        products.put(productId, updated);
        return updated;
    }

    public Product getProduct(int id) { return requireProduct(id); }
    public Customer getCustomer(int id) {
        Customer c = customers.get(id);
        if (c == null) throw new IllegalArgumentException("找不到顧客");
        return c;
    }
    public Order getOrder(int id) {
        Order o = orders.get(id);
        if (o == null) throw new IllegalArgumentException("找不到訂單");
        return o;
    }
    public List<Product> products() { return List.copyOf(products.values()); }
    public List<Customer> customers() { return List.copyOf(customers.values()); }
    public List<Order> orders() { return List.copyOf(orders.values()); }

    /** Searches orders by order number, customer name or email, status, or purchased product name.
     * A null or blank query returns all orders; matching ignores case and leading/trailing spaces.
     */
    public List<Order> searchOrders(String query) {
        if (query == null || query.isBlank()) return orders();
        String term = query.trim().toLowerCase(java.util.Locale.ROOT);
        return orders.values().stream().filter(order -> {
            Customer customer = customers.get(order.customerId());
            boolean matchesOrder = Integer.toString(order.id()).contains(term);
            boolean matchesCustomer = customer.name().toLowerCase(java.util.Locale.ROOT).contains(term)
                    || customer.email().toLowerCase(java.util.Locale.ROOT).contains(term);
            boolean matchesStatus = order.status().name().toLowerCase(java.util.Locale.ROOT).contains(term);
            boolean matchesProduct = order.items().keySet().stream().map(products::get)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(product -> product.name().toLowerCase(java.util.Locale.ROOT).contains(term));
            return matchesOrder || matchesCustomer || matchesStatus || matchesProduct;
        }).toList();
    }

    private Product requireProduct(int id) {
        Product p = products.get(id);
        if (p == null) throw new IllegalArgumentException("找不到商品");
        return p;
    }

    public record Product(int id, String name, BigDecimal price, int stock) {
        Product withStock(int value) { return new Product(id, name, price, value); }
    }
    public record Customer(int id, String name, String email, String phone) { }
    public enum OrderStatus { PROCESSING, SHIPPED, COMPLETED, CANCELLED }
    public record Order(int id, int customerId, Map<Integer, Integer> items, BigDecimal total,
                        LocalDateTime createdAt, OrderStatus status) {
        Order withStatus(OrderStatus value) { return new Order(id, customerId, items, total, createdAt, value); }
    }
}
