package com.example.supermarket;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.*;
import com.vaadin.flow.component.textfield.*;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.PWA;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.EmptyResultDataAccessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SpringBootApplication
@Route("")
@PWA(name = "Supermarket Billing", shortName = "Supermarket")
public class Main extends VerticalLayout implements AppShellConfigurator {

    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final Div page = new Div();
    private final H2 pageTitle = new H2("Dashboard");
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public Main(JdbcTemplate db, TransactionTemplate transactions) {
        this.db = db;
        this.transactions = transactions;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("background", "#f5f7fb").set("font-family", "Inter, Arial, sans-serif");

        Div header = new Div();
        header.add(new H2("🛒 Supermarket Billing & Inventory"));
        header.add(new Span("Java + Vaadin"));
        header.getStyle().set("display", "flex").set("justify-content", "space-between")
                .set("align-items", "center").set("padding", "18px 28px")
                .set("background", "#14213d").set("color", "white");
        header.getChildren().filter(c -> c instanceof H2).map(c -> (H2)c).forEach(h -> h.getStyle().set("margin", "0"));
        header.getChildren().filter(c -> c instanceof Span).map(c -> (Span)c).forEach(s -> s.getStyle().set("color", "#b9c7df"));

        String[] names = {"Dashboard", "Products", "Categories", "Inventory", "Billing", "Sales Reports"};
        Tabs tabs = new Tabs();
        Map<Tab, String> tabNames = new LinkedHashMap<>();
        for (String name : names) {
            Tab tab = new Tab(name);
            tabs.add(tab);
            tabNames.put(tab, name);
        }
        tabs.getStyle().set("padding", "8px 20px").set("background", "white");
        page.setWidthFull();
        page.getStyle().set("padding", "22px 28px").set("box-sizing", "border-box");
        add(header, tabs, page);
        setFlexGrow(1, page);
        tabs.addSelectedChangeListener(e -> showPage(tabNames.get(e.getSelectedTab())));
        showPage("Dashboard");
    }

    private void showPage(String name) {
        page.removeAll();
        pageTitle.setText(name);
        page.add(pageTitle);
        switch (name) {
            case "Products" -> productsPage();
            case "Categories" -> categoriesPage();
            case "Inventory" -> inventoryPage();
            case "Billing" -> billingPage();
            case "Sales Reports" -> reportsPage();
            default -> dashboardPage();
        }
    }

    private void dashboardPage() {
        long productCount = scalar("SELECT COUNT(*) FROM products");
        long categoryCount = scalar("SELECT COUNT(*) FROM categories");
        long lowStock = scalar("SELECT COUNT(*) FROM products WHERE stock <= reorder_level");
        BigDecimal sales = db.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM sales WHERE CAST(created_at AS DATE)=CURRENT_DATE",
                BigDecimal.class);
        HorizontalLayout cards = new HorizontalLayout(
                metric("Today's Sales", money(sales), "↗"),
                metric("Products", String.valueOf(productCount), "▣"),
                metric("Categories", String.valueOf(categoryCount), "▦"),
                metric("Low Stock Items", String.valueOf(lowStock), "⚠"));
        cards.setWidthFull();
        cards.setWrap(true);
        page.add(cards, new H3("Recent bills"));
        Grid<Sale> grid = new Grid<>(Sale.class, false);
        grid.addColumn(Sale::id).setHeader("Bill #").setAutoWidth(true);
        grid.addColumn(Sale::counter).setHeader("Counter");
        grid.addColumn(s -> s.createdAt().format(dateFormat)).setHeader("Date");
        grid.addColumn(s -> money(s.total())).setHeader("Total");
        grid.setItems(recentSales());
        grid.setHeight("320px");
        page.add(grid);
    }

    private Component metric(String label, String value, String symbol) {
        Div card = new Div();
        card.add(new Span(symbol), new Paragraph(label), new H2(value));
        card.getStyle().set("background", "white").set("border", "1px solid #e5eaf2")
                .set("border-radius", "12px").set("padding", "18px 22px")
                .set("min-width", "190px").set("flex", "1");
        card.getChildren().forEach(c -> c.getStyle().set("margin", "4px 0"));
        card.getChildren().filter(c -> c instanceof H2).forEach(c -> c.getStyle().set("color", "#1769aa"));
        return card;
    }

    private void productsPage() {
        Button add = primary("Add Product", e -> productDialog(null));
        Grid<Product> grid = productGrid();
        page.add(toolbar("Manage products", add), grid);
        grid.setItems(allProducts());
    }

    private Grid<Product> productGrid() {
        Grid<Product> grid = new Grid<>(Product.class, false);
        grid.addColumn(Product::id).setHeader("ID").setAutoWidth(true);
        grid.addColumn(Product::name).setHeader("Product").setFlexGrow(2);
        grid.addColumn(Product::category).setHeader("Category");
        grid.addColumn(p -> money(p.price())).setHeader("Price");
        grid.addColumn(Product::stock).setHeader("Stock");
        grid.addColumn(Product::reorderLevel).setHeader("Reorder at");
        grid.addComponentColumn(p -> {
            Button edit = new Button("Edit", e -> productDialog(p));
            edit.setThemeName("tertiary");
            Button delete = new Button("Delete", e -> confirmDelete(p));
            delete.setThemeName("tertiary error");
            return new HorizontalLayout(edit, delete);
        }).setHeader("Actions");
        grid.setWidthFull();
        grid.setHeight("480px");
        return grid;
    }

    private void productDialog(Product existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing == null ? "Add product" : "Edit product");
        TextField name = new TextField("Product name");
        ComboBox<String> category = new ComboBox<>("Category");
        category.setItems(categoryNames());
        category.setAllowCustomValue(true);
        category.addCustomValueSetListener(e -> {
            String value = e.getDetail().trim();
            if (!value.isBlank()) {
                ensureCategory(value);
                category.setItems(categoryNames());
                category.setValue(value);
            }
        });
        NumberField price = new NumberField("Selling price");
        NumberField stock = new NumberField("Opening stock");
        NumberField reorder = new NumberField("Low-stock threshold");
        price.setMin(0.01); stock.setMin(0); reorder.setMin(0);
        if (existing != null) {
            name.setValue(existing.name()); category.setValue(existing.category());
            price.setValue(existing.price().doubleValue()); stock.setValue((double)existing.stock());
            reorder.setValue((double)existing.reorderLevel());
        } else {
            stock.setValue(0d); reorder.setValue(5d);
        }
        FormLayout form = new FormLayout(name, category, price, stock, reorder);
        Button save = primary("Save", e -> {
            if (name.isEmpty() || category.isEmpty() || price.isEmpty() || stock.isEmpty()) {
                notify("Please complete the required fields", true); return;
            }
            String cat = category.getValue().trim();
            ensureCategory(cat);
            if (existing == null) {
                db.update("INSERT INTO products(name,category,price,stock,reorder_level) VALUES (?,?,?,?,?)",
                        name.getValue().trim(), cat, BigDecimal.valueOf(price.getValue()),
                        stock.getValue().intValue(), reorder.getValue() == null ? 5 : reorder.getValue().intValue());
            } else {
                db.update("UPDATE products SET name=?,category=?,price=?,stock=?,reorder_level=? WHERE id=?",
                        name.getValue().trim(), cat, BigDecimal.valueOf(price.getValue()),
                        stock.getValue().intValue(), reorder.getValue() == null ? 5 : reorder.getValue().intValue(), existing.id());
            }
            dialog.close(); showPage("Products"); notify("Product saved", false);
        });
        Button cancel = new Button("Cancel", e -> dialog.close());
        dialog.add(form); dialog.getFooter().add(cancel, save); dialog.open();
    }

    private void confirmDelete(Product p) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Delete product?");
        d.add("Delete " + p.name() + "? This cannot be undone.");
        Button yes = new Button("Delete", e -> {
            db.update("DELETE FROM products WHERE id=?", p.id());
            d.close(); showPage("Products"); notify("Product deleted", false);
        });
        yes.setThemeName("primary error");
        d.getFooter().add(new Button("Cancel", e -> d.close()), yes);
        d.open();
    }

    private void categoriesPage() {
        TextField name = new TextField("New category");
        name.setPlaceholder("e.g. Bakery");
        Button add = primary("Add category", e -> {
            if (name.isEmpty()) return;
            try {
                db.update("INSERT INTO categories(name) VALUES (?)", name.getValue().trim());
                name.clear(); showPage("Categories"); notify("Category added", false);
            } catch (Exception ex) { notify("Category already exists", true); }
        });
        Grid<Category> grid = new Grid<>(Category.class, false);
        grid.addColumn(Category::id).setHeader("ID");
        grid.addColumn(Category::name).setHeader("Category").setFlexGrow(1);
        grid.addComponentColumn(c -> {
            Button delete = new Button("Delete", e -> {
                Integer used = db.queryForObject("SELECT COUNT(*) FROM products WHERE category=?", Integer.class, c.name());
                if (used != null && used > 0) { notify("Move or delete products in this category first", true); return; }
                db.update("DELETE FROM categories WHERE id=?", c.id()); showPage("Categories");
            });
            delete.setThemeName("tertiary error");
            return delete;
        }).setHeader("Actions");
        grid.setItems(db.query("SELECT id,name FROM categories ORDER BY name", (rs, n) -> new Category(rs.getLong(1), rs.getString(2))));
        grid.setHeight("420px"); grid.setWidthFull();
        page.add(new HorizontalLayout(name, add), grid);
    }

    private void inventoryPage() {
        Grid<Product> grid = productGrid();
        grid.setItems(allProducts());
        TextField productId = new TextField("Product ID");
        NumberField quantity = new NumberField("Quantity to add");
        quantity.setMin(1); quantity.setStep(1);
        Button restock = primary("Add stock", e -> {
            try {
                long id = Long.parseLong(productId.getValue());
                int qty = quantity.getValue().intValue();
                if (qty <= 0) throw new IllegalArgumentException();
                int changed = db.update("UPDATE products SET stock=stock+? WHERE id=?", qty, id);
                if (changed == 0) { notify("Product ID not found", true); return; }
                showPage("Inventory"); notify("Stock updated", false);
            } catch (Exception ex) { notify("Enter a valid product ID and quantity", true); }
        });
        page.add(new Paragraph("Restock products. Products at or below their reorder threshold are highlighted in the product list."),
                new HorizontalLayout(productId, quantity, restock), grid);
    }

    private void billingPage() {
        ComboBox<String> counter = new ComboBox<>("Billing counter");
        counter.setItems("Counter 1", "Counter 2", "Counter 3", "Counter 4");
        counter.setValue("Counter 1");
        ComboBox<Product> product = new ComboBox<>("Select product");
        product.setItems(allProducts());
        product.setItemLabelGenerator(p -> p.name() + " (stock: " + p.stock() + ") — " + money(p.price()));
        NumberField quantity = new NumberField("Quantity");
        quantity.setMin(1); quantity.setStep(1); quantity.setValue(1d);
        List<CartLine> cart = new ArrayList<>();
        Grid<CartLine> cartGrid = new Grid<>(CartLine.class, false);
        cartGrid.addColumn(CartLine::name).setHeader("Product").setFlexGrow(2);
        cartGrid.addColumn(CartLine::quantity).setHeader("Qty");
        cartGrid.addColumn(c -> money(c.price())).setHeader("Unit price");
        cartGrid.addColumn(c -> money(c.price().multiply(BigDecimal.valueOf(c.quantity())))).setHeader("Amount");
        cartGrid.setWidthFull(); cartGrid.setHeight("260px");
        H3 subtotalLabel = new H3("Subtotal: ₹0.00");
        H3 totalLabel = new H3("Total: ₹0.00");
        ComboBox<String> discountType = new ComboBox<>("Discount type");
        discountType.setItems("No discount", "Percentage (%)", "Fixed amount (₹)");
        discountType.setValue("No discount");
        NumberField discount = new NumberField("Discount value");
        discount.setMin(0); discount.setValue(0d);
        Runnable refreshCart = () -> {
            cartGrid.setItems(new ArrayList<>(cart));
            BigDecimal sub = subtotal(cart);
            BigDecimal disc = discountAmount(sub, discountType.getValue(), discount.getValue());
            subtotalLabel.setText("Subtotal: " + money(sub));
            totalLabel.setText("Total: " + money(sub.subtract(disc).max(BigDecimal.ZERO)));
        };
        cartGrid.addComponentColumn(line -> {
            Button remove = new Button("Remove", e -> { cart.remove(line); refreshCart.run(); });
            remove.setThemeName("tertiary error"); return remove;
        }).setHeader("");
        Button add = primary("Add to bill", e -> {
            if (product.isEmpty() || quantity.isEmpty() || quantity.getValue() < 1) {
                notify("Select a product and valid quantity", true); return;
            }
            Product p = product.getValue();
            int qty = quantity.getValue().intValue();
            if (qty > p.stock()) { notify("Insufficient stock. Available: " + p.stock(), true); return; }
            cart.add(new CartLine(p.id(), p.name(), p.price(), qty));
            refreshCart.run();
        });
        discountType.addValueChangeListener(e -> refreshCart.run());
        discount.addValueChangeListener(e -> refreshCart.run());
        Button checkout = primary("Complete sale", e -> {
            if (cart.isEmpty()) { notify("Add at least one product", true); return; }
            try {
                BigDecimal sub = subtotal(cart);
                BigDecimal disc = discountAmount(sub, discountType.getValue(), discount.getValue());
                BigDecimal total = sub.subtract(disc).max(BigDecimal.ZERO);
                String counterName = counter.getValue();
                transactions.executeWithoutResult(status -> {
                    for (CartLine line : cart) {
                        int updated = db.update("UPDATE products SET stock=stock-? WHERE id=? AND stock>=?",
                                line.quantity(), line.productId(), line.quantity());
                        if (updated == 0) throw new IllegalStateException("Stock changed for " + line.name() + ". Refresh and try again.");
                    }
                    org.springframework.jdbc.support.GeneratedKeyHolder keyHolder =
                            new org.springframework.jdbc.support.GeneratedKeyHolder();
                    db.update(connection -> {
                        java.sql.PreparedStatement statement = connection.prepareStatement(
                                "INSERT INTO sales(counter,subtotal,discount,total) VALUES (?,?,?,?)",
                                java.sql.Statement.RETURN_GENERATED_KEYS);
                        statement.setString(1, counterName);
                        statement.setBigDecimal(2, sub);
                        statement.setBigDecimal(3, disc);
                        statement.setBigDecimal(4, total);
                        return statement;
                    }, keyHolder);
                    Long saleId = Objects.requireNonNull(keyHolder.getKey()).longValue();
                    for (CartLine line : cart) {
                        db.update("INSERT INTO sale_items(sale_id,product_id,product_name,quantity,unit_price,line_total) VALUES (?,?,?,?,?,?)",
                                saleId, line.productId(), line.name(), line.quantity(), line.price(),
                                line.price().multiply(BigDecimal.valueOf(line.quantity())));
                    }
                });
                cart.clear(); discount.clear(); discountType.setValue("No discount");
                refreshCart.run(); product.setItems(allProducts());
                notify("Sale completed. Bill #" + db.queryForObject("SELECT MAX(id) FROM sales", Long.class), false);
            } catch (Exception ex) { notify("Sale failed: " + ex.getMessage(), true); }
        });
        HorizontalLayout selection = new HorizontalLayout(counter, product, quantity, add);
        selection.setAlignItems(Alignment.END); selection.setWidthFull(); selection.setFlexGrow(1, product);
        HorizontalLayout discounts = new HorizontalLayout(discountType, discount);
        page.add(new Paragraph("Each counter has its own checkout identity. Stock is checked again when the sale is saved."),
                selection, new H3("Current bill"), cartGrid, discounts, subtotalLabel, totalLabel, checkout);
        refreshCart.run();
    }

    private void reportsPage() {
        BigDecimal today = db.queryForObject("SELECT COALESCE(SUM(total),0) FROM sales WHERE CAST(created_at AS DATE)=CURRENT_DATE", BigDecimal.class);
        BigDecimal allTime = db.queryForObject("SELECT COALESCE(SUM(total),0) FROM sales", BigDecimal.class);
        page.add(new HorizontalLayout(metric("Today's revenue", money(today), "₹"),
                metric("All-time revenue", money(allTime), "₹")));
        Grid<Sale> grid = new Grid<>(Sale.class, false);
        grid.addColumn(Sale::id).setHeader("Bill #");
        grid.addColumn(Sale::counter).setHeader("Counter");
        grid.addColumn(s -> s.createdAt().format(dateFormat)).setHeader("Date");
        grid.addColumn(s -> money(s.subtotal())).setHeader("Subtotal");
        grid.addColumn(s -> money(s.discount())).setHeader("Discount");
        grid.addColumn(s -> money(s.total())).setHeader("Total");
        grid.addComponentColumn(s -> {
            Button view = new Button("View bill", e -> showSale(s.id()));
            view.setThemeName("tertiary"); return view;
        }).setHeader("Details");
        grid.setItems(recentSales());
        grid.setWidthFull(); grid.setHeight("480px");
        page.add(new H3("Sales history (latest 200)"), grid);
    }

    private void showSale(long id) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Bill #" + id);
        Sale sale = db.queryForObject("SELECT id,counter,subtotal,discount,total,created_at FROM sales WHERE id=?",
                saleMapper(), id);
        Grid<Map<String,Object>> items = new Grid<>();
        items.addColumn(r -> String.valueOf(r.get("product_name"))).setHeader("Product");
        items.addColumn(r -> String.valueOf(r.get("quantity"))).setHeader("Qty");
        items.addColumn(r -> money((BigDecimal)r.get("line_total"))).setHeader("Amount");
        items.setItems(db.queryForList("SELECT product_name,quantity,line_total FROM sale_items WHERE sale_id=?", id));
        items.setHeight("220px");
        d.add(new Paragraph(sale.counter() + " · " + sale.createdAt().format(dateFormat)), items,
                new Paragraph("Subtotal: " + money(sale.subtotal())),
                new Paragraph("Discount: " + money(sale.discount())),
                new H3("Total: " + money(sale.total())));
        d.getFooter().add(new Button("Close", e -> d.close())); d.open();
    }

    private Component toolbar(String text, Component action) {
        HorizontalLayout bar = new HorizontalLayout(new H3(text), action);
        bar.setWidthFull(); bar.setAlignItems(Alignment.CENTER); bar.expand(new H3());
        bar.setJustifyContentMode(JustifyContentMode.BETWEEN);
        return bar;
    }

    private Button primary(String text, ComponentEventListener<ClickEvent<Button>> action) {
        Button b = new Button(text, action);
        b.setThemeName("primary");
        return b;
    }

    private void notify(String message, boolean error) {
        Notification n = Notification.show(message, 3500, Notification.Position.TOP_END);
        n.addThemeVariants(error ? NotificationVariant.LUMO_ERROR : NotificationVariant.LUMO_SUCCESS);
    }

    private long scalar(String sql) { Long v = db.queryForObject(sql, Long.class); return v == null ? 0 : v; }
    private void ensureCategory(String name) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM categories WHERE name=?", Integer.class, name);
        if (count == null || count == 0) db.update("INSERT INTO categories(name) VALUES (?)", name);
    }
    private List<String> categoryNames() { return db.query("SELECT name FROM categories ORDER BY name", (rs,n) -> rs.getString(1)); }
    private List<Product> allProducts() {
        return db.query("SELECT p.id,p.name,p.category,p.price,p.stock,p.reorder_level FROM products p ORDER BY p.name", productMapper());
    }
    private List<Sale> recentSales() {
        return db.query("SELECT id,counter,subtotal,discount,total,created_at FROM sales ORDER BY id DESC LIMIT 200", saleMapper());
    }
    private RowMapper<Product> productMapper() {
        return (rs,n) -> new Product(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
                rs.getBigDecimal("price"), rs.getInt("stock"), rs.getInt("reorder_level"));
    }
    private RowMapper<Sale> saleMapper() {
        return (rs,n) -> new Sale(rs.getLong("id"), rs.getString("counter"), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("discount"), rs.getBigDecimal("total"), rs.getTimestamp("created_at").toLocalDateTime());
    }
    private BigDecimal subtotal(List<CartLine> lines) {
        return lines.stream().map(l -> l.price().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal discountAmount(BigDecimal sub, String type, Double value) {
        if (value == null || value <= 0 || type == null || type.equals("No discount")) return BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.valueOf(value);
        if (type.startsWith("Percentage")) amount = sub.multiply(amount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return amount.min(sub).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
    private String money(BigDecimal value) { return "₹" + (value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString()); }

    @Bean
    CommandLineRunner initializeDatabase(JdbcTemplate jdbc) {
        return args -> {
            jdbc.execute("CREATE TABLE IF NOT EXISTS categories (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS products (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(180) NOT NULL, category VARCHAR(100) NOT NULL, price DECIMAL(12,2) NOT NULL, stock INTEGER NOT NULL DEFAULT 0, reorder_level INTEGER NOT NULL DEFAULT 5)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sales (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, counter VARCHAR(50) NOT NULL, subtotal DECIMAL(12,2) NOT NULL, discount DECIMAL(12,2) NOT NULL, total DECIMAL(12,2) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sale_items (id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, sale_id BIGINT NOT NULL REFERENCES sales(id), product_id BIGINT, product_name VARCHAR(180) NOT NULL, quantity INTEGER NOT NULL, unit_price DECIMAL(12,2) NOT NULL, line_total DECIMAL(12,2) NOT NULL)");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class) == 0) {
                jdbc.update("INSERT INTO categories(name) VALUES ('Groceries'),('Beverages'),('Dairy'),('Snacks'),('Household')");
            }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class) == 0) {
                jdbc.update("INSERT INTO products(name,category,price,stock,reorder_level) VALUES " +
                        "('Rice 1 kg','Groceries',65.00,100,10),('Wheat Flour 1 kg','Groceries',48.00,80,10)," +
                        "('Milk 1 L','Dairy',32.00,60,10),('Tea 250 g','Beverages',110.00,35,5)," +
                        "('Potato Chips','Snacks',20.00,50,8),('Dish Wash 500 ml','Household',85.00,25,5)");
            }
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    record Product(long id, String name, String category, BigDecimal price, int stock, int reorderLevel) {}
    record Category(long id, String name) {}
    record Sale(long id, String counter, BigDecimal subtotal, BigDecimal discount, BigDecimal total, LocalDateTime createdAt) {}
    record CartLine(long productId, String name, BigDecimal price, int quantity) {}
}
