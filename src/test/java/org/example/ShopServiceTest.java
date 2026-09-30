package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShopServiceTest {
    private ShopService shop;
    private ShopService.Product product;
    private ShopService.Customer customer;

    @BeforeEach
    void setUp() {
        shop = new ShopService();
        product = shop.addProduct("滑鼠", new BigDecimal("12.345"), 5);
        customer = shop.addCustomer("小明", "ming@example.com", "0912345678");
    }

    /**
     * 情境：管理員新增一項售價有三位小數、庫存為 5 的商品。
     * 操作：建立商品時輸入售價 12.345。
     * 預期：售價依兩位小數四捨五入為 12.35，初始庫存為 5，且可用編號查回同一商品。
     */
    @Test
    void addProductRoundsPriceAndAssignsInitialStock() {
        assertEquals(new BigDecimal("12.35"), product.price());
        assertEquals(5, product.stock());
        assertEquals(product, shop.getProduct(product.id()));
    }

    /**
     * 情境：新增商品或顧客時提供不符合規則的資料。
     * 操作：分別輸入空白商品名稱、零售價、負庫存、格式錯誤的電子郵件，以及已存在的電子郵件
     *      （電子郵件比對不區分大小寫）。
     * 預期：每一種資料都會被拒絕，並以 IllegalArgumentException 回報；不接受無效或重複資料。
     */
    @Test
    void rejectsInvalidProductsAndCustomers() {
        assertThrows(IllegalArgumentException.class, () -> shop.addProduct("", BigDecimal.ONE, 1));
        assertThrows(IllegalArgumentException.class, () -> shop.addProduct("商品", BigDecimal.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> shop.addProduct("商品", BigDecimal.ONE, -1));
        assertThrows(IllegalArgumentException.class, () -> shop.addCustomer("小華", "bad-email", ""));
        assertThrows(IllegalArgumentException.class, () -> shop.addCustomer("小華", "MING@example.com", ""));
    }

    /**
     * 情境：顧客購買一項有足夠庫存的商品。
     * 前置條件：商品單價為 12.35，庫存為 5；顧客已存在。
     * 操作：顧客下單購買 3 件。
     * 預期：訂單總額為 37.05，庫存降為 2，訂單初始狀態為 PROCESSING。
     */
    @Test
    void placingOrderCalculatesTotalAndDecreasesStock() {
        ShopService.Order order = shop.placeOrder(customer.id(), Map.of(product.id(), 3));

        assertEquals(new BigDecimal("37.05"), order.total());
        assertEquals(2, shop.getProduct(product.id()).stock());
        assertEquals(ShopService.OrderStatus.PROCESSING, order.status());
    }

    /**
     * 情境：多商品訂單中，前一項庫存足夠，但後一項商品庫存不足。
     * 前置條件：滑鼠庫存為 5，鍵盤庫存為 1。
     * 操作：同一筆訂單要求滑鼠 2 件及鍵盤 2 件。
     * 預期：整筆訂單失敗；兩項商品庫存均維持原值，且不建立任何訂單，確保下單具原子性。
     */
    @Test
    void invalidOrderDoesNotPartiallyChangeStock() {
        ShopService.Product second = shop.addProduct("鍵盤", new BigDecimal("30"), 1);

        assertThrows(IllegalArgumentException.class,
                () -> shop.placeOrder(customer.id(), Map.of(product.id(), 2, second.id(), 2)));

        assertEquals(5, shop.getProduct(product.id()).stock());
        assertEquals(1, shop.getProduct(second.id()).stock());
        assertTrue(shop.orders().isEmpty());
    }

    /**
     * 情境：顧客取消一筆已扣除庫存的訂單，並嘗試再次取消。
     * 前置條件：訂單購買 2 件商品，建立後庫存由 5 降為 3。
     * 操作：第一次將狀態改為 CANCELLED，再次嘗試取消同一訂單。
     * 預期：第一次取消會將庫存恢復至 5；第二次取消被拒絕，庫存不會重複增加。
     */
    @Test
    void cancellingOrderRestoresStockAndCannotBeRepeated() {
        ShopService.Order order = shop.placeOrder(customer.id(), Map.of(product.id(), 2));
        shop.changeOrderStatus(order.id(), ShopService.OrderStatus.CANCELLED);

        assertEquals(5, shop.getProduct(product.id()).stock());
        assertEquals(ShopService.OrderStatus.CANCELLED, shop.getOrder(order.id()).status());
        assertThrows(IllegalStateException.class,
                () -> shop.changeOrderStatus(order.id(), ShopService.OrderStatus.CANCELLED));
        assertEquals(5, shop.getProduct(product.id()).stock());
    }

    /**
     * 情境：管理員為商品補貨，並驗證補貨數量必須為正數。
     * 前置條件：商品目前庫存為 5。
     * 操作：先嘗試補貨 0 件，再補貨 3 件。
     * 預期：0 件補貨會以 IllegalArgumentException 拒絕；補貨 3 件成功，庫存變為 8。
     */
    @Test
    void restockRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> shop.restock(product.id(), 0));
        assertEquals(8, shop.restock(product.id(), 3).stock());
    }
}
