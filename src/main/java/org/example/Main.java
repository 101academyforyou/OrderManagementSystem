package org.example;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/** Console interface; business rules live in {@link ShopService}. */
public class Main {
    private static final Scanner INPUT = new Scanner(System.in);
    private static final ShopService SHOP = new ShopService();

    public static void main(String[] args) {
        SHOP.addProduct("無線滑鼠", new BigDecimal("599"), 20);
        SHOP.addProduct("機械鍵盤", new BigDecimal("1890"), 12);
        SHOP.addCustomer("王小明", "ming@example.com", "0912-345-678");
        while (true) {
            System.out.println("\n=== 電商管理系統 ===\n1. 商品管理  2. 顧客管理  3. 訂單管理  4. 營運報表  0. 離開");
            switch (readInt("請選擇功能：", 0, 4)) {
                case 1 -> productMenu();
                case 2 -> customerMenu();
                case 3 -> orderMenu();
                case 4 -> report();
                case 0 -> { System.out.println("已離開系統。"); return; }
            }
        }
    }

    private static void productMenu() {
        while (true) {
            System.out.println("\n--- 商品管理 ---\n1. 列出商品  2. 新增商品  3. 補充庫存  0. 返回");
            switch (readInt("請選擇：", 0, 3)) {
                case 1 -> SHOP.products().forEach(p -> System.out.printf("%d | %s | $%s | 庫存 %d%n", p.id(), p.name(), p.price(), p.stock()));
                case 2 -> run(() -> SHOP.addProduct(readText("商品名稱："), readMoney("售價："), readInt("初始庫存：", 0, Integer.MAX_VALUE)));
                case 3 -> run(() -> SHOP.restock(readInt("商品編號：", 1, Integer.MAX_VALUE), readInt("補貨數量：", 1, Integer.MAX_VALUE)));
                case 0 -> { return; }
            }
        }
    }

    private static void customerMenu() {
        while (true) {
            System.out.println("\n--- 顧客管理 ---\n1. 列出顧客  2. 新增顧客  0. 返回");
            switch (readInt("請選擇：", 0, 2)) {
                case 1 -> SHOP.customers().forEach(c -> System.out.printf("%d | %s | %s | %s%n", c.id(), c.name(), c.email(), c.phone()));
                case 2 -> run(() -> SHOP.addCustomer(readText("顧客姓名："), readText("電子郵件："), readText("電話：")));
                case 0 -> { return; }
            }
        }
    }

    private static void orderMenu() {
        while (true) {
            System.out.println("\n--- 訂單管理 ---\n1. 建立訂單  2. 列出訂單  3. 搜尋訂單  4. 更新狀態  0. 返回");
            switch (readInt("請選擇：", 0, 4)) {
                case 1 -> createOrder();
                case 2 -> SHOP.orders().forEach(o -> System.out.printf("%d | 顧客 %d | $%s | %s | 商品 %s%n", o.id(), o.customerId(), o.total(), o.status(), o.items()));
                case 3 -> searchOrders();
                case 4 -> changeStatus();
                case 0 -> { return; }
            }
        }
    }

    private static void searchOrders() {
        String query = readText("搜尋訂單編號、顧客姓名／Email、狀態或商品名稱：");
        var results = SHOP.searchOrders(query);
        if (results.isEmpty()) {
            System.out.println("沒有符合條件的訂單。");
            return;
        }
        results.forEach(o -> {
            ShopService.Customer customer = SHOP.getCustomer(o.customerId());
            System.out.printf("訂單 %d | %s <%s> | $%s | %s | 商品 %s%n",
                    o.id(), customer.name(), customer.email(), o.total(), o.status(), o.items());
        });
    }

    private static void createOrder() {
        SHOP.customers().forEach(c -> System.out.printf("顧客 %d | %s%n", c.id(), c.name()));
        int customerId = readInt("顧客編號：", 1, Integer.MAX_VALUE);
        Map<Integer, Integer> items = new LinkedHashMap<>();
        while (true) {
            SHOP.products().forEach(p -> System.out.printf("商品 %d | %s | $%s | 庫存 %d%n", p.id(), p.name(), p.price(), p.stock()));
            int productId = readInt("商品編號（0 完成）：", 0, Integer.MAX_VALUE);
            if (productId == 0) break;
            int qty = readInt("數量：", 1, Integer.MAX_VALUE);
            items.merge(productId, qty, Integer::sum);
        }
        run(() -> {
            ShopService.Order order = SHOP.placeOrder(customerId, items);
            System.out.printf("訂單 %d 已建立，金額 $%s%n", order.id(), order.total());
        });
    }

    private static void changeStatus() {
        System.out.println("1. 處理中  2. 已出貨  3. 已完成  4. 已取消");
        int id = readInt("訂單編號：", 1, Integer.MAX_VALUE);
        ShopService.OrderStatus status = switch (readInt("新狀態：", 1, 4)) {
            case 1 -> ShopService.OrderStatus.PROCESSING;
            case 2 -> ShopService.OrderStatus.SHIPPED;
            case 3 -> ShopService.OrderStatus.COMPLETED;
            default -> ShopService.OrderStatus.CANCELLED;
        };
        run(() -> SHOP.changeOrderStatus(id, status));
    }

    private static void report() {
        long lowStock = SHOP.products().stream().filter(p -> p.stock() <= 5).count();
        BigDecimal revenue = SHOP.orders().stream()
                .filter(o -> o.status() == ShopService.OrderStatus.SHIPPED || o.status() == ShopService.OrderStatus.COMPLETED)
                .map(ShopService.Order::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.printf("商品 %d 項、顧客 %d 位、訂單 %d 筆、低庫存商品 %d 項%n", SHOP.products().size(), SHOP.customers().size(), SHOP.orders().size(), lowStock);
        System.out.println("已出貨及已完成營收：$" + revenue.setScale(2));
    }

    private static void run(Runnable action) {
        try { action.run(); System.out.println("操作完成。"); }
        catch (IllegalArgumentException | IllegalStateException e) { System.out.println("操作失敗：" + e.getMessage()); }
        catch (ArithmeticException e) { System.out.println("操作失敗：數值超出可處理範圍。"); }
    }
    private static String readText(String prompt) {
        while (true) { System.out.print(prompt); String s = INPUT.nextLine().trim(); if (!s.isEmpty()) return s; System.out.println("不可空白。"); }
    }
    private static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try { int value = Integer.parseInt(INPUT.nextLine().trim()); if (value >= min && value <= max) return value; }
            catch (NumberFormatException ignored) { }
            System.out.printf("請輸入 %d 到 %d 之間的整數。%n", min, max);
        }
    }
    private static BigDecimal readMoney(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { BigDecimal value = new BigDecimal(INPUT.nextLine().trim()); if (value.signum() > 0) return value; }
            catch (NumberFormatException ignored) { }
            System.out.println("請輸入大於 0 的金額。");
        }
    }
}
